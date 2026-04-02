package com.heritage.marketplace.order;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import com.heritage.marketplace.order.dto.CreateOrderRequest;
import com.heritage.marketplace.order.dto.OrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
        @Valid @RequestBody CreateOrderRequest request,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        OrderResponse response = orderService.createOrder(principal.userId(), request, idempotencyKey, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(
        @PathVariable UUID orderId,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            orderService.confirmOrder(orderId, principal.userId(), idempotencyKey, clientIp(httpRequest))
        ));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
        @PathVariable UUID orderId,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(orderId, principal, clientIp(httpRequest))));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> myOrders(
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        Page<OrderResponse> orderPage = orderService.myOrders(principal.userId(), status, PageRequest.of(safePage, safeSize));

        ApiMeta meta = ApiMeta.of(safePage, safeSize, orderPage.getTotalElements(), orderPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(orderPage.getContent(), meta));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
        @PathVariable UUID orderId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(orderId, principal)));
    }

    @PostMapping("/{orderId}/fulfill")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> fulfillOrder(
        @PathVariable UUID orderId,
        @AuthenticationPrincipal JwtUserPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            orderService.fulfillOrder(orderId, principal.userId(), clientIp(httpRequest))
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
