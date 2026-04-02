package com.heritage.marketplace.appeal;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.appeal.dto.AppealDetailResponse;
import com.heritage.marketplace.appeal.dto.AppealEvidenceResponse;
import com.heritage.marketplace.appeal.dto.AppealFinalDecision;
import com.heritage.marketplace.appeal.dto.AppealFinalReviewRequest;
import com.heritage.marketplace.appeal.dto.AppealResponse;
import com.heritage.marketplace.appeal.dto.AppealReviewDecision;
import com.heritage.marketplace.appeal.dto.AppealReviewRequest;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.FileUploadSecurityValidator;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.ticket.Ticket;
import com.heritage.marketplace.ticket.TicketRepository;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AppealService {

    private final AppealRepository appealRepository;
    private final AppealEvidenceRepository appealEvidenceRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final FileUploadSecurityValidator fileUploadSecurityValidator;
    private final InputSanitizer inputSanitizer;
    private final Path uploadRoot;

    public AppealService(
        AppealRepository appealRepository,
        AppealEvidenceRepository appealEvidenceRepository,
        TicketRepository ticketRepository,
        UserRepository userRepository,
        AuditLogService auditLogService,
        FileUploadSecurityValidator fileUploadSecurityValidator,
        InputSanitizer inputSanitizer,
        @Value("${app.appeal.upload-dir:uploads/appeals}") String uploadDir
    ) {
        this.appealRepository = appealRepository;
        this.appealEvidenceRepository = appealEvidenceRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.fileUploadSecurityValidator = fileUploadSecurityValidator;
        this.inputSanitizer = inputSanitizer;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Transactional
    public AppealDetailResponse createAppeal(
        UUID ticketId,
        String reason,
        List<MultipartFile> evidenceFiles,
        JwtUserPrincipal principal,
        String ipAddress
    ) {
        String sanitizedReason = inputSanitizer.sanitize(reason);
        if (sanitizedReason == null || sanitizedReason.length() < 10 || sanitizedReason.length() > 5000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason must be between 10 and 5000 characters");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket was not found"));

        if (!ticket.getReporter().getId().equals(principal.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can only submit appeals for your own tickets");
        }

        List<MultipartFile> files = evidenceFiles == null ? List.of() : evidenceFiles.stream()
            .filter(file -> file != null && !file.isEmpty())
            .toList();

        List<Map<String, Object>> validationErrors = fileUploadSecurityValidator.validateEvidenceFiles(files);
        if (!validationErrors.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "EVIDENCE_VALIDATION_FAILED",
                "Evidence validation failed",
                Map.of("files", validationErrors)
            );
        }

        User appellant = userRepository.findById(principal.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Appellant was not found"));

        Appeal appeal = new Appeal();
        appeal.setId(UUID.randomUUID());
        appeal.setTicket(ticket);
        appeal.setAppellant(appellant);
        appeal.setReason(sanitizedReason);
        appeal.setStatus(AppealStatus.SUBMITTED);
        appeal.setCreatedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);

        List<AppealEvidence> evidenceRecords = storeEvidenceFiles(appeal, files);
        if (!evidenceRecords.isEmpty()) {
            appealEvidenceRepository.saveAll(evidenceRecords);
        }

        auditLogService.log(
            "APPEAL",
            appeal.getId(),
            "CREATE",
            principal.userId(),
            Map.of("status", appeal.getStatus().name(), "ticketId", ticket.getId(), "evidenceCount", evidenceRecords.size()),
            ipAddress
        );

        return toDetailResponse(appeal, evidenceRecords);
    }

    @Transactional(readOnly = true)
    public Page<AppealResponse> listAppeals(JwtUserPrincipal principal, AppealStatus status, Pageable pageable) {
        Specification<Appeal> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!isModeratorOrAdmin(principal)) {
                predicates.add(cb.equal(root.get("appellant").get("id"), principal.userId()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return appealRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AppealDetailResponse getAppeal(UUID appealId, JwtUserPrincipal principal) {
        Appeal appeal = getAppealOrThrow(appealId);
        ensureAppealAccess(appeal, principal);
        List<AppealEvidence> evidence = appealEvidenceRepository.findByAppeal_IdOrderByUploadedAtAsc(appealId);
        return toDetailResponse(appeal, evidence);
    }

    @Transactional(readOnly = true)
    public EvidenceDownload loadEvidence(UUID appealId, UUID evidenceId, JwtUserPrincipal principal) {
        Appeal appeal = getAppealOrThrow(appealId);
        ensureAppealAccess(appeal, principal);

        AppealEvidence evidence = appealEvidenceRepository.findByIdAndAppeal_Id(evidenceId, appealId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Appeal evidence was not found"));

        Path path = Paths.get(evidence.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_FILE_MISSING", "Evidence file is missing from storage");
        }

        Resource resource = new FileSystemResource(path);
        return new EvidenceDownload(resource, evidence.getFileName(), evidence.getMimeType(), evidence.getFileSizeBytes());
    }

    @Transactional
    public AppealDetailResponse reviewAppeal(UUID appealId, AppealReviewRequest request, JwtUserPrincipal principal, String ipAddress) {
        Appeal appeal = getAppealOrThrow(appealId);
        if (appeal.getStatus() == AppealStatus.APPROVED || appeal.getStatus() == AppealStatus.DENIED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPEAL_STATE", "Appeal already has a final decision");
        }

        User reviewer = userRepository.findById(principal.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Reviewer not found"));

        appeal.setReviewer(reviewer);
        appeal.setDecisionNotes(request.decisionNotes().trim());

        if (request.decision() == AppealReviewDecision.ESCALATED_TO_ADMIN) {
            appeal.setStatus(AppealStatus.ESCALATED_TO_ADMIN);
            appeal.setDecidedAt(null);
        } else if (request.decision() == AppealReviewDecision.APPROVED) {
            appeal.setStatus(AppealStatus.APPROVED);
            appeal.setDecidedAt(LocalDateTime.now());
        } else {
            appeal.setStatus(AppealStatus.DENIED);
            appeal.setDecidedAt(LocalDateTime.now());
        }

        appeal = appealRepository.save(appeal);
        List<AppealEvidence> evidence = appealEvidenceRepository.findByAppeal_IdOrderByUploadedAtAsc(appeal.getId());

        auditLogService.log(
            "APPEAL",
            appeal.getId(),
            "REVIEW",
            principal.userId(),
            Map.of("status", appeal.getStatus().name()),
            ipAddress
        );

        return toDetailResponse(appeal, evidence);
    }

    @Transactional
    public AppealDetailResponse finalReviewAppeal(UUID appealId, AppealFinalReviewRequest request, JwtUserPrincipal principal, String ipAddress) {
        Appeal appeal = getAppealOrThrow(appealId);
        if (appeal.getStatus() != AppealStatus.ESCALATED_TO_ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPEAL_STATE", "Appeal must be escalated to admin for final review");
        }

        User adminReviewer = userRepository.findById(principal.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Reviewer not found"));

        appeal.setAdminReviewer(adminReviewer);
        appeal.setDecisionNotes(request.decisionNotes().trim());
        appeal.setStatus(request.decision() == AppealFinalDecision.APPROVED ? AppealStatus.APPROVED : AppealStatus.DENIED);
        appeal.setDecidedAt(LocalDateTime.now());
        appeal = appealRepository.save(appeal);

        List<AppealEvidence> evidence = appealEvidenceRepository.findByAppeal_IdOrderByUploadedAtAsc(appeal.getId());

        auditLogService.log(
            "APPEAL",
            appeal.getId(),
            "FINAL_REVIEW",
            principal.userId(),
            Map.of("status", appeal.getStatus().name()),
            ipAddress
        );

        return toDetailResponse(appeal, evidence);
    }

    private Appeal getAppealOrThrow(UUID appealId) {
        return appealRepository.findById(appealId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPEAL_NOT_FOUND", "Appeal was not found"));
    }

    private void ensureAppealAccess(Appeal appeal, JwtUserPrincipal principal) {
        if (isModeratorOrAdmin(principal)) {
            return;
        }
        if (!appeal.getAppellant().getId().equals(principal.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this appeal");
        }
    }

    private boolean isModeratorOrAdmin(JwtUserPrincipal principal) {
        return principal.role() == UserRole.MODERATOR || principal.role() == UserRole.ADMIN;
    }

    private List<AppealEvidence> storeEvidenceFiles(Appeal appeal, List<MultipartFile> files) {
        try {
            Path appealDir = uploadRoot.resolve(appeal.getId().toString());
            Files.createDirectories(appealDir);

            List<AppealEvidence> evidenceList = new ArrayList<>();
            for (MultipartFile file : files) {
                String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
                String safeOriginal = fileUploadSecurityValidator.sanitizeFileName(original);
                String storedName = UUID.randomUUID() + "_" + safeOriginal;
                Path target = appealDir.resolve(storedName);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                AppealEvidence evidence = new AppealEvidence();
                evidence.setId(UUID.randomUUID());
                evidence.setAppeal(appeal);
                evidence.setFileName(safeOriginal);
                evidence.setFilePath(target.toString());
                evidence.setMimeType(file.getContentType());
                evidence.setFileSizeBytes(file.getSize());
                evidence.setUploadedAt(LocalDateTime.now());
                evidenceList.add(evidence);
            }

            return evidenceList;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EVIDENCE_STORAGE_FAILED", "Failed to store appeal evidence files");
        }
    }

    private AppealResponse toResponse(Appeal appeal) {
        return new AppealResponse(
            appeal.getId(),
            appeal.getTicket().getId(),
            appeal.getAppellant().getId(),
            appeal.getAppellant().getDisplayName(),
            appeal.getReason(),
            appeal.getStatus(),
            appeal.getReviewer() == null ? null : appeal.getReviewer().getId(),
            appeal.getAdminReviewer() == null ? null : appeal.getAdminReviewer().getId(),
            appeal.getDecisionNotes(),
            appeal.getCreatedAt(),
            appeal.getDecidedAt()
        );
    }

    private AppealDetailResponse toDetailResponse(Appeal appeal, List<AppealEvidence> evidence) {
        List<AppealEvidenceResponse> evidenceResponses = evidence.stream()
            .map(ev -> new AppealEvidenceResponse(
                ev.getId(),
                ev.getFileName(),
                ev.getMimeType(),
                ev.getFileSizeBytes(),
                ev.getUploadedAt()
            ))
            .toList();

        return new AppealDetailResponse(toResponse(appeal), evidenceResponses);
    }

    public record EvidenceDownload(Resource resource, String fileName, String mimeType, long fileSize) {
    }
}
