package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record InventoryDocumentItemRequest(
    @JsonProperty("inventory_id")
    UUID inventoryId,
    @JsonProperty("listing_id")
    UUID listingId,
    @Min(value = 1, message = "quantity must be at least 1")
    int quantity,
    String notes
) {
}
