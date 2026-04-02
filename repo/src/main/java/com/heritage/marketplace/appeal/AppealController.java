package com.heritage.marketplace.appeal;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.appeal.dto.AppealDetailResponse;
import com.heritage.marketplace.appeal.dto.AppealFinalReviewRequest;
import com.heritage.marketplace.appeal.dto.AppealResponse;
import com.heritage.marketplace.appeal.dto.AppealReviewRequest;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/appeals")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<AppealDetailResponse>> create(
        @RequestParam("ticket_id") UUID ticketId,
        @RequestParam("reason") String reason,
        @RequestParam(value = "evidence", required = false) List<MultipartFile> evidence,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        AppealDetailResponse response = appealService.createAppeal(ticketId, reason, evidence, principal, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<AppealResponse>>> list(
        @RequestParam(required = false) AppealStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        Page<AppealResponse> appealPage = appealService.listAppeals(principal, status, PageRequest.of(safePage, safeSize));
        ApiMeta meta = ApiMeta.of(safePage, safeSize, appealPage.getTotalElements(), appealPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(appealPage.getContent(), meta));
    }

    @GetMapping("/{appealId}")
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<AppealDetailResponse>> detail(
        @PathVariable UUID appealId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(appealService.getAppeal(appealId, principal)));
    }

    @GetMapping("/{appealId}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<Resource> download(
        @PathVariable UUID appealId,
        @PathVariable UUID evidenceId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        AppealService.EvidenceDownload data = appealService.loadEvidence(appealId, evidenceId, principal);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (data.mimeType() != null && !data.mimeType().isBlank()) {
            mediaType = MediaType.parseMediaType(data.mimeType());
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + data.fileName() + "\"")
            .contentLength(data.fileSize())
            .body(data.resource());
    }

    @PostMapping("/{appealId}/review")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<AppealDetailResponse>> review(
        @PathVariable UUID appealId,
        @Valid @RequestBody AppealReviewRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            appealService.reviewAppeal(appealId, request, principal, clientIp(httpRequest))
        ));
    }

    @PostMapping("/{appealId}/final-review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AppealDetailResponse>> finalReview(
        @PathVariable UUID appealId,
        @Valid @RequestBody AppealFinalReviewRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            appealService.finalReviewAppeal(appealId, request, principal, clientIp(httpRequest))
        ));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
