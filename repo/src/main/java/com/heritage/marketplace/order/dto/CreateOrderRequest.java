package com.heritage.marketplace.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    @JsonProperty("delivery_lat")
    @DecimalMin(value = "-90.0", message = "delivery_lat must be at least -90")
    @DecimalMax(value = "90.0", message = "delivery_lat must be at most 90")
    BigDecimal deliveryLat,

    @JsonProperty("delivery_lng")
    @DecimalMin(value = "-180.0", message = "delivery_lng must be at least -180")
    @DecimalMax(value = "180.0", message = "delivery_lng must be at most 180")
    BigDecimal deliveryLng,

    @NotEmpty(message = "items are required")
    List<@Valid CreateOrderItemRequest> items
) {
}
