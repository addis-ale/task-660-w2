package com.heritage.marketplace.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateOrderItemRequest(
    @JsonProperty("listing_id")
    @NotNull(message = "listing_id is required")
    UUID listingId,

    @Min(value = 1, message = "quantity must be at least 1")
    int quantity
) {
}
