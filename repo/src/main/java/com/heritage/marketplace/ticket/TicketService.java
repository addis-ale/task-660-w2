package com.heritage.marketplace.ticket;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.ticket.dto.CreateFollowUpRequest;
import com.heritage.marketplace.ticket.dto.CreateTicketRequest;
import com.heritage.marketplace.ticket.dto.TicketDetailResponse;
import com.heritage.marketplace.ticket.dto.TicketFollowUpResponse;
import com.heritage.marketplace.ticket.dto.TicketResponse;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketFollowUpRepository ticketFollowUpRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final InputSanitizer inputSanitizer;

    public TicketService(
        TicketRepository ticketRepository,
        TicketFollowUpRepository ticketFollowUpRepository,
        UserRepository userRepository,
        AuditLogService auditLogService,
        InputSanitizer inputSanitizer
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketFollowUpRepository = ticketFollowUpRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.inputSanitizer = inputSanitizer;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, UUID reporterId, String ipAddress) {
        User reporter = userRepository.findById(reporterId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Reporter was not found"));

        Ticket ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setReporter(reporter);
        ticket.setType(request.type());
        ticket.setSeverity(request.severity());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setAssignedTo(null);
        ticket.setLocationAddress(inputSanitizer.sanitize(request.locationAddress()));
        ticket.setLocationCrossStreet(inputSanitizer.sanitize(request.locationCrossStreet()));
        ticket.setDescription(inputSanitizer.sanitize(request.description()));
        ticket.setCreatedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);

        auditLogService.log(
            "TICKET",
            ticket.getId(),
            "CREATE",
            reporterId,
            Map.of("status", ticket.getStatus().name(), "severity", ticket.getSeverity().name(), "type", ticket.getType().name()),
            ipAddress
        );

        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listTickets(
        JwtUserPrincipal principal,
        TicketStatus status,
        TicketSeverity severity,
        TicketType type,
        UUID assignedTo,
        Pageable pageable
    ) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!isModeratorOrAdmin(principal)) {
                predicates.add(cb.equal(root.get("reporter").get("id"), principal.userId()));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedTo));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return ticketRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketDetail(UUID ticketId, JwtUserPrincipal principal) {
        Ticket ticket = getTicketOrThrow(ticketId);
        ensureTicketAccess(principal, ticket);

        List<TicketFollowUpResponse> followUps = ticketFollowUpRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId)
            .stream()
            .map(this::toFollowUpResponse)
            .toList();

        return new TicketDetailResponse(toResponse(ticket), followUps);
    }

    @Transactional
    public TicketResponse acknowledge(UUID ticketId, UUID assignedTo, JwtUserPrincipal principal, String ipAddress) {
        Ticket ticket = getTicketOrThrow(ticketId);
        User assignee = resolveAssignee(assignedTo, principal.userId());

        ticket.setStatus(TicketStatus.ACKNOWLEDGED);
        ticket.setAcknowledgedAt(LocalDateTime.now());
        ticket.setAssignedTo(assignee);
        ticket = ticketRepository.save(ticket);

        auditLogService.log(
            "TICKET",
            ticket.getId(),
            "ACKNOWLEDGE",
            principal.userId(),
            Map.of("status", ticket.getStatus().name(), "assignedTo", assignee.getId()),
            ipAddress
        );

        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse updateStatus(UUID ticketId, TicketStatus targetStatus, JwtUserPrincipal principal, String ipAddress) {
        Ticket ticket = getTicketOrThrow(ticketId);
        validateTransition(ticket.getStatus(), targetStatus);

        ticket.setStatus(targetStatus);
        if (targetStatus == TicketStatus.CLOSED && ticket.getResolvedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSITION", "Ticket must be resolved before closing");
        }
        if (targetStatus == TicketStatus.ESCALATED) {
            ticket.setEscalatedAt(LocalDateTime.now());
            ticket.setAssignedTo(resolveModerator());
        }

        ticket = ticketRepository.save(ticket);

        auditLogService.log(
            "TICKET",
            ticket.getId(),
            "STATUS_UPDATE",
            principal.userId(),
            Map.of("status", ticket.getStatus().name()),
            ipAddress
        );

        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse resolve(UUID ticketId, String closureCode, String closureNotes, JwtUserPrincipal principal, String ipAddress) {
        Ticket ticket = getTicketOrThrow(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "Closed ticket cannot be resolved");
        }

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setClosureCode(inputSanitizer.sanitize(closureCode));
        ticket.setClosureNotes(inputSanitizer.sanitize(closureNotes));
        ticket.setResolvedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);

        auditLogService.log(
            "TICKET",
            ticket.getId(),
            "RESOLVE",
            principal.userId(),
            Map.of("status", ticket.getStatus().name(), "closureCode", ticket.getClosureCode()),
            ipAddress
        );

        return toResponse(ticket);
    }

    @Transactional
    public TicketFollowUpResponse addFollowUp(UUID ticketId, CreateFollowUpRequest request, JwtUserPrincipal principal, String ipAddress) {
        Ticket ticket = getTicketOrThrow(ticketId);
        ensureFollowUpAccess(principal, ticket);

        User author = userRepository.findById(principal.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Author was not found"));

        TicketFollowUp followUp = new TicketFollowUp();
        followUp.setId(UUID.randomUUID());
        followUp.setTicket(ticket);
        followUp.setAuthor(author);
        followUp.setMessage(inputSanitizer.sanitize(request.message()));
        followUp.setCreatedAt(LocalDateTime.now());
        followUp = ticketFollowUpRepository.save(followUp);

        auditLogService.log(
            "TICKET",
            ticket.getId(),
            "FOLLOW_UP",
            principal.userId(),
            Map.of("followUpId", followUp.getId()),
            ipAddress
        );

        return toFollowUpResponse(followUp);
    }

    @Transactional(readOnly = true)
    public List<TicketFollowUpResponse> listFollowUps(UUID ticketId, JwtUserPrincipal principal) {
        Ticket ticket = getTicketOrThrow(ticketId);
        ensureTicketAccess(principal, ticket);
        return ticketFollowUpRepository.findByTicket_IdOrderByCreatedAtAsc(ticketId)
            .stream()
            .map(this::toFollowUpResponse)
            .toList();
    }

    @Transactional
    public int autoEscalateBreachedTickets() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> breached = ticketRepository.findTicketsForEscalation(now);
        if (breached.isEmpty()) {
            return 0;
        }

        User moderator = resolveModerator();
        for (Ticket ticket : breached) {
            ticket.setStatus(TicketStatus.ESCALATED);
            ticket.setEscalatedAt(now);
            ticket.setAssignedTo(moderator);
            ticketRepository.save(ticket);

            auditLogService.log(
                "TICKET",
                ticket.getId(),
                "AUTO_ESCALATE",
                moderator.getId(),
                Map.of("status", ticket.getStatus().name()),
                "system"
            );
        }
        return breached.size();
    }

    private Ticket getTicketOrThrow(UUID ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket was not found"));
    }

    private User resolveAssignee(UUID assignedTo, UUID fallback) {
        UUID target = assignedTo != null ? assignedTo : fallback;
        return userRepository.findById(target)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Assigned user was not found"));
    }

    private User resolveModerator() {
        return userRepository.findFirstByRole(UserRole.MODERATOR)
            .or(() -> userRepository.findFirstByRole(UserRole.ADMIN))
            .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MODERATOR_NOT_FOUND", "No moderator/admin available for escalation"));
    }

    private boolean isModeratorOrAdmin(JwtUserPrincipal principal) {
        return principal.role() == UserRole.MODERATOR || principal.role() == UserRole.ADMIN;
    }

    private void ensureTicketAccess(JwtUserPrincipal principal, Ticket ticket) {
        if (isModeratorOrAdmin(principal)) {
            return;
        }

        boolean reporter = ticket.getReporter().getId().equals(principal.userId());
        boolean assigned = ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(principal.userId());
        if (!(reporter || assigned)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this ticket");
        }
    }

    private void ensureFollowUpAccess(JwtUserPrincipal principal, Ticket ticket) {
        if (isModeratorOrAdmin(principal)) {
            return;
        }

        boolean reporter = ticket.getReporter().getId().equals(principal.userId());
        boolean assigned = ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(principal.userId());
        if (!(reporter || assigned)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot add a follow-up to this ticket");
        }
    }

    private void validateTransition(TicketStatus current, TicketStatus target) {
        if (current == target) {
            return;
        }

        Map<TicketStatus, Set<TicketStatus>> transitions = new EnumMap<>(TicketStatus.class);
        transitions.put(TicketStatus.OPEN, Set.of(TicketStatus.ACKNOWLEDGED, TicketStatus.IN_PROGRESS, TicketStatus.ESCALATED));
        transitions.put(TicketStatus.ACKNOWLEDGED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.ESCALATED));
        transitions.put(TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.ESCALATED));
        transitions.put(TicketStatus.ESCALATED, Set.of(TicketStatus.ACKNOWLEDGED, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
        transitions.put(TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED));
        transitions.put(TicketStatus.CLOSED, Set.of());

        Set<TicketStatus> allowed = transitions.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSITION",
                "Invalid ticket status transition from %s to %s".formatted(current.name(), target.name())
            );
        }
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
            ticket.getId(),
            ticket.getReporter().getId(),
            ticket.getReporter().getDisplayName(),
            ticket.getType(),
            ticket.getSeverity(),
            ticket.getStatus(),
            ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
            ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getDisplayName(),
            ticket.getLocationAddress(),
            ticket.getLocationCrossStreet(),
            ticket.getDescription(),
            ticket.getClosureCode(),
            ticket.getClosureNotes(),
            ticket.getSlaAcknowledgeBy() == null ? ticket.getCreatedAt().plusMinutes(15) : ticket.getSlaAcknowledgeBy(),
            ticket.getSlaResolveBy() == null ? ticket.getCreatedAt().plusHours(24) : ticket.getSlaResolveBy(),
            ticket.getAcknowledgedAt(),
            ticket.getResolvedAt(),
            ticket.getEscalatedAt(),
            ticket.getCreatedAt()
        );
    }

    private TicketFollowUpResponse toFollowUpResponse(TicketFollowUp followUp) {
        return new TicketFollowUpResponse(
            followUp.getId(),
            followUp.getTicket().getId(),
            followUp.getAuthor().getId(),
            followUp.getAuthor().getDisplayName(),
            followUp.getMessage(),
            followUp.getCreatedAt()
        );
    }
}
