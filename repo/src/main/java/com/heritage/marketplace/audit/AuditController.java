package com.heritage.marketplace.audit;

import com.heritage.marketplace.audit.dto.AuditLogEntryResponse;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogEntryResponse>>> listAuditLogs(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int pageSize
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, pageSize));

        AuditLogPageResult result = auditLogQueryService.search(
            entityType,
            entityId,
            action,
            actorId,
            from,
            to,
            safePage,
            safeSize
        );

        int totalPages = (int) Math.ceil((double) result.totalItems() / safeSize);
        ApiMeta meta = ApiMeta.of(safePage, safeSize, result.totalItems(), totalPages);
        return ResponseEntity.ok(ApiResponse.success(result.items(), meta));
    }
}
