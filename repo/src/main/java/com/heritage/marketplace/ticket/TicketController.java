package com.heritage.marketplace.ticket;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import com.heritage.marketplace.ticket.dto.AcknowledgeTicketRequest;
import com.heritage.marketplace.ticket.dto.CreateFollowUpRequest;
import com.heritage.marketplace.ticket.dto.CreateTicketRequest;
import com.heritage.marketplace.ticket.dto.ResolveTicketRequest;
import com.heritage.marketplace.ticket.dto.TicketDetailResponse;
import com.heritage.marketplace.ticket.dto.TicketFollowUpResponse;
import com.heritage.marketplace.ticket.dto.TicketResponse;
import com.heritage.marketplace.ticket.dto.UpdateTicketStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<TicketResponse>> create(
        @Valid @RequestBody CreateTicketRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        TicketResponse response = ticketService.createTicket(request, principal.userId(), clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> list(
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) TicketSeverity severity,
        @RequestParam(required = false) TicketType type,
        @RequestParam(required = false) UUID assignedTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        Page<TicketResponse> ticketPage = ticketService.listTickets(
            principal,
            status,
            severity,
            type,
            assignedTo,
            PageRequest.of(safePage, safeSize)
        );
        ApiMeta meta = ApiMeta.of(safePage, safeSize, ticketPage.getTotalElements(), ticketPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(ticketPage.getContent(), meta));
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> detail(
        @PathVariable UUID ticketId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getTicketDetail(ticketId, principal)));
    }

    @PostMapping("/{ticketId}/acknowledge")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> acknowledge(
        @PathVariable UUID ticketId,
        @RequestBody(required = false) AcknowledgeTicketRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        UUID assignedTo = request == null ? null : request.assignedTo();
        return ResponseEntity.ok(ApiResponse.success(
            ticketService.acknowledge(ticketId, assignedTo, principal, clientIp(httpRequest))
        ));
    }

    @PatchMapping("/{ticketId}/status")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
        @PathVariable UUID ticketId,
        @Valid @RequestBody UpdateTicketStatusRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            ticketService.updateStatus(ticketId, request.status(), principal, clientIp(httpRequest))
        ));
    }

    @PostMapping("/{ticketId}/resolve")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> resolve(
        @PathVariable UUID ticketId,
        @Valid @RequestBody ResolveTicketRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            ticketService.resolve(ticketId, request.closureCode(), request.closureNotes(), principal, clientIp(httpRequest))
        ));
    }

    @PostMapping("/{ticketId}/follow-ups")
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<TicketFollowUpResponse>> addFollowUp(
        @PathVariable UUID ticketId,
        @Valid @RequestBody CreateFollowUpRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            ticketService.addFollowUp(ticketId, request, principal, clientIp(httpRequest))
        ));
    }

    @GetMapping("/{ticketId}/follow-ups")
    @PreAuthorize("hasAnyRole('MEMBER','SELLER','WAREHOUSE_STAFF','MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<TicketFollowUpResponse>>> followUps(
        @PathVariable UUID ticketId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.listFollowUps(ticketId, principal)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
