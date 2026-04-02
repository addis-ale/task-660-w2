package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record StocktakeItemRequest(
    @JsonProperty("inventory_id")
    UUID inventoryId,
    @JsonProperty("listing_id")
    UUID listingId,
    @JsonProperty("actual_count")
    @Min(value = 0, message = "actual_count must be 0 or greater")
    int actualCount,
    String notes
) {
}
