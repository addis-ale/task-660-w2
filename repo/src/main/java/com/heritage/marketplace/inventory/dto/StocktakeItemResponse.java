package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record StocktakeItemResponse(
    @JsonProperty("inventory_id")
    UUID inventoryId,
    @JsonProperty("listing_id")
    UUID listingId,
    @JsonProperty("previous_qty")
    int previousQty,
    @JsonProperty("actual_count")
    int actualCount,
    int adjustment,
    @JsonProperty("document_ref")
    String documentRef
) {
}
