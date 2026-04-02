package com.heritage.marketplace.risk;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import com.heritage.marketplace.risk.dto.RiskDashboardResponse;
import com.heritage.marketplace.risk.dto.RiskEntityDetailsResponse;
import com.heritage.marketplace.risk.dto.RiskFlagResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<RiskDashboardResponse>> dashboard(
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(riskService.dashboard(principal, clientIp(request))));
    }

    @GetMapping("/flags")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<RiskFlagResponse>>> flags(
        @RequestParam(required = false) RiskEntityType entityType,
        @RequestParam(required = false) RiskFlagType flagType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest request
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        Page<RiskFlagResponse> flagPage = riskService.flags(
            entityType,
            flagType,
            PageRequest.of(safePage, safeSize),
            principal,
            clientIp(request)
        );

        ApiMeta meta = ApiMeta.of(safePage, safeSize, flagPage.getTotalElements(), flagPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(flagPage.getContent(), meta));
    }

    @GetMapping("/entity/{entityId}")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<RiskEntityDetailsResponse>> entity(
        @PathVariable UUID entityId,
        @RequestParam RiskEntityType entityType,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            riskService.entityDetails(entityId, entityType, principal, clientIp(request))
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
