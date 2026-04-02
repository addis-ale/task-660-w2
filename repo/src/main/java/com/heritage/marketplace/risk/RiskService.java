package com.heritage.marketplace.risk;

import com.heritage.marketplace.appeal.Appeal;
import com.heritage.marketplace.appeal.AppealRepository;
import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.common.util.EncryptionUtil;
import com.heritage.marketplace.risk.dto.RiskAppealSummaryResponse;
import com.heritage.marketplace.risk.dto.RiskDashboardResponse;
import com.heritage.marketplace.risk.dto.RiskEntityDetailsResponse;
import com.heritage.marketplace.risk.dto.RiskFlagResponse;
import com.heritage.marketplace.risk.dto.RiskTicketSummaryResponse;
import com.heritage.marketplace.risk.dto.TopFlaggedEntityResponse;
import com.heritage.marketplace.ticket.Ticket;
import com.heritage.marketplace.ticket.TicketRepository;
import com.heritage.marketplace.ticket.TicketSeverity;
import com.heritage.marketplace.ticket.TicketStatus;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskService {

    private final RiskFlagRepository riskFlagRepository;
    private final TicketRepository ticketRepository;
    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final AuditLogService auditLogService;

    public RiskService(
        RiskFlagRepository riskFlagRepository,
        TicketRepository ticketRepository,
        AppealRepository appealRepository,
        UserRepository userRepository,
        EncryptionUtil encryptionUtil,
        AuditLogService auditLogService
    ) {
        this.riskFlagRepository = riskFlagRepository;
        this.ticketRepository = ticketRepository;
        this.appealRepository = appealRepository;
        this.userRepository = userRepository;
        this.encryptionUtil = encryptionUtil;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public RiskDashboardResponse dashboard(JwtUserPrincipal principal, String ipAddress) {
        List<TicketStatus> openExcluded = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

        long openLow = ticketRepository.countByStatusNotInAndSeverity(openExcluded, TicketSeverity.LOW);
        long openMedium = ticketRepository.countByStatusNotInAndSeverity(openExcluded, TicketSeverity.MEDIUM);
        long openHigh = ticketRepository.countByStatusNotInAndSeverity(openExcluded, TicketSeverity.HIGH);

        Double avgHours = ticketRepository.averageResolutionHours();
        long totalTickets = ticketRepository.count();
        long escalated = ticketRepository.countByEscalatedAtIsNotNull();
        double escalationRate = totalTickets == 0 ? 0 : round2((escalated * 100.0) / totalTickets);

        long activeFlags = riskFlagRepository.countByWindowEndGreaterThanEqual(LocalDate.now());
        List<TopFlaggedEntityResponse> sellers = buildTopFlagged(RiskEntityType.SELLER);
        List<TopFlaggedEntityResponse> members = buildTopFlagged(RiskEntityType.MEMBER);

        auditLogService.log(
            "RISK",
            UUID.randomUUID(),
            "VIEW_DASHBOARD",
            principal.userId(),
            Map.of("activeFlags", activeFlags, "escalationRate", escalationRate),
            ipAddress
        );

        return new RiskDashboardResponse(
            openLow,
            openMedium,
            openHigh,
            round2(avgHours == null ? 0 : avgHours),
            escalationRate,
            activeFlags,
            sellers,
            members
        );
    }

    @Transactional(readOnly = true)
    public Page<RiskFlagResponse> flags(RiskEntityType entityType, RiskFlagType flagType, Pageable pageable, JwtUserPrincipal principal, String ipAddress) {
        Page<RiskFlag> page;
        if (entityType != null && flagType != null) {
            page = riskFlagRepository.findByEntityTypeAndFlagType(entityType, flagType, pageable);
        } else if (entityType != null) {
            page = riskFlagRepository.findByEntityType(entityType, pageable);
        } else if (flagType != null) {
            page = riskFlagRepository.findByFlagType(flagType, pageable);
        } else {
            page = riskFlagRepository.findAll(pageable);
        }

        auditLogService.log(
            "RISK",
            UUID.randomUUID(),
            "VIEW_FLAGS",
            principal.userId(),
            Map.of("entityType", entityType == null ? "ALL" : entityType.name(), "flagType", flagType == null ? "ALL" : flagType.name()),
            ipAddress
        );

        return page.map(this::toFlagResponse);
    }

    @Transactional(readOnly = true)
    public RiskEntityDetailsResponse entityDetails(
        UUID entityId,
        RiskEntityType entityType,
        JwtUserPrincipal principal,
        String ipAddress
    ) {
        User user = userRepository.findById(entityId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", "Risk entity not found"));

        List<RiskFlag> flags = riskFlagRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        List<RiskFlagResponse> flagResponses = flags.stream().map(this::toFlagResponse).toList();

        List<RiskTicketSummaryResponse> recentTickets = ticketRepository.findTop20ByReporter_IdOrderByCreatedAtDesc(entityId).stream()
            .map(t -> new RiskTicketSummaryResponse(t.getId(), t.getType(), t.getSeverity(), t.getStatus(), t.getCreatedAt()))
            .toList();

        List<RiskAppealSummaryResponse> recentAppeals = appealRepository.findTop20ByAppellant_IdOrderByCreatedAtDesc(entityId).stream()
            .map(a -> new RiskAppealSummaryResponse(a.getId(), a.getTicket().getId(), a.getStatus(), a.getCreatedAt(), a.getDecidedAt()))
            .toList();

        double riskScore = computeRiskScore(flags);
        String recommendation = buildRecommendation(entityType, riskScore);

        auditLogService.log(
            "RISK",
            entityId,
            "VIEW_ENTITY",
            principal.userId(),
            Map.of("riskScore", riskScore, "entityType", entityType.name()),
            ipAddress
        );

        return new RiskEntityDetailsResponse(
            entityType,
            entityId,
            encryptionUtil.decrypt(user.getEmail()),
            user.getDisplayName(),
            user.getRole(),
            user.getStatus(),
            flagResponses,
            recentTickets,
            recentAppeals,
            riskScore,
            recommendation
        );
    }

    @Transactional
    public int generateRepeatIncidentFlags() {
        LocalDate windowEnd = LocalDate.now();
        LocalDate windowStart = windowEnd.minusDays(29);
        LocalDateTime startAt = windowStart.atStartOfDay();

        List<Ticket> recentTickets = ticketRepository.findByCreatedAtAfter(startAt);
        if (recentTickets.isEmpty()) {
            return 0;
        }

        Map<UUID, Integer> incidentsByUser = new HashMap<>();
        for (Ticket ticket : recentTickets) {
            incidentsByUser.merge(ticket.getReporter().getId(), 1, Integer::sum);
        }

        int updated = 0;
        for (Map.Entry<UUID, Integer> entry : incidentsByUser.entrySet()) {
            if (entry.getValue() <= 3) {
                continue;
            }

            User user = userRepository.findById(entry.getKey()).orElse(null);
            if (user == null) {
                continue;
            }

            RiskEntityType entityType = mapRoleToEntityType(user.getRole());
            if (entityType == null) {
                continue;
            }

            RiskFlag flag = riskFlagRepository.findByEntityTypeAndEntityIdAndFlagTypeAndWindowStartAndWindowEnd(
                    entityType,
                    user.getId(),
                    RiskFlagType.REPEAT_INCIDENTS,
                    windowStart,
                    windowEnd
                )
                .orElseGet(() -> {
                    RiskFlag created = new RiskFlag();
                    created.setId(UUID.randomUUID());
                    created.setEntityType(entityType);
                    created.setEntityId(user.getId());
                    created.setFlagType(RiskFlagType.REPEAT_INCIDENTS);
                    created.setWindowStart(windowStart);
                    created.setWindowEnd(windowEnd);
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });

            flag.setIncidentCount(entry.getValue());
            riskFlagRepository.save(flag);
            updated++;

            auditLogService.log(
                "RISK_FLAG",
                flag.getId(),
                "UPSERT_REPEAT_INCIDENTS",
                user.getId(),
                Map.of("incidentCount", flag.getIncidentCount(), "entityType", entityType.name()),
                "system"
            );
        }
        return updated;
    }

    private List<TopFlaggedEntityResponse> buildTopFlagged(RiskEntityType type) {
        List<RiskFlag> topFlags = riskFlagRepository.findTop10ByEntityTypeOrderByIncidentCountDescCreatedAtDesc(type);
        if (topFlags.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> users = userRepository.findAllById(topFlags.stream().map(RiskFlag::getEntityId).toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<TopFlaggedEntityResponse> response = new ArrayList<>();
        for (RiskFlag flag : topFlags) {
            User user = users.get(flag.getEntityId());
            response.add(new TopFlaggedEntityResponse(
                flag.getEntityId(),
                user == null ? "Unknown" : user.getDisplayName(),
                flag.getFlagType(),
                flag.getIncidentCount(),
                flag.getWindowStart(),
                flag.getWindowEnd()
            ));
        }

        return response;
    }

    private RiskFlagResponse toFlagResponse(RiskFlag flag) {
        return new RiskFlagResponse(
            flag.getId(),
            flag.getEntityType(),
            flag.getEntityId(),
            flag.getFlagType(),
            flag.getIncidentCount(),
            flag.getWindowStart(),
            flag.getWindowEnd(),
            flag.getCreatedAt()
        );
    }

    private double computeRiskScore(List<RiskFlag> flags) {
        if (flags.isEmpty()) {
            return 0;
        }

        double score = 0;
        LocalDate today = LocalDate.now();
        for (RiskFlag flag : flags) {
            double typeWeight = switch (flag.getFlagType()) {
                case REPEAT_INCIDENTS -> 2.0;
                case MISSED_CHECKINS -> 1.2;
                case BUDDY_PUNCHING -> 1.8;
                case MISIDENTIFICATION -> 1.6;
            };

            long daysSinceEnd = Math.max(0, ChronoUnit.DAYS.between(flag.getWindowEnd(), today));
            double recencyMultiplier = 1.0 / (1.0 + (daysSinceEnd / 30.0));
            score += flag.getIncidentCount() * typeWeight * recencyMultiplier;
        }

        return round2(Math.min(100.0, score));
    }

    private String buildRecommendation(RiskEntityType entityType, double riskScore) {
        if (riskScore >= 75) {
            return switch (entityType) {
                case SELLER -> "Review seller activity. Consider temporary suspension.";
                case MEMBER -> "Escalate member behavior review and restrict high-risk transactions.";
                case STAFF -> "Launch immediate supervisor review and temporary access restrictions.";
            };
        }

        if (riskScore >= 45) {
            return switch (entityType) {
                case SELLER -> "Monitor seller listings closely and require compliance checks.";
                case MEMBER -> "Monitor member incidents and apply enhanced verification.";
                case STAFF -> "Increase staff supervision and validate operational check-ins.";
            };
        }

        return "Continue routine monitoring. No immediate intervention required.";
    }

    private RiskEntityType mapRoleToEntityType(UserRole role) {
        return switch (role) {
            case SELLER -> RiskEntityType.SELLER;
            case MEMBER -> RiskEntityType.MEMBER;
            case WAREHOUSE_STAFF, MODERATOR, ADMIN -> RiskEntityType.STAFF;
            default -> null;
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
