package com.heritage.marketplace.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    OrderStatus status,
    @JsonProperty("total_amount")
    BigDecimal totalAmount,
    @JsonProperty("discount_amount")
    BigDecimal discountAmount,
    @JsonProperty("final_amount")
    BigDecimal finalAmount,
    @JsonProperty("fulfillment_warehouse_id")
    UUID fulfillmentWarehouseId,
    @JsonProperty("reservation_expires_at")
    LocalDateTime reservationExpiresAt,
    @JsonProperty("idempotency_key")
    String idempotencyKey,
    @JsonProperty("created_at")
    LocalDateTime createdAt,
    @JsonProperty("updated_at")
    LocalDateTime updatedAt,
    List<OrderItemResponse> items,
    @JsonProperty("applied_benefits")
    List<OrderBenefitResponse> appliedBenefits
) {
}
