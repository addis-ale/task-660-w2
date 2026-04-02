package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record LowStockAlertResponse(
    @JsonProperty("inventory_id")
    UUID inventoryId,
    @JsonProperty("listing_id")
    UUID listingId,
    @JsonProperty("warehouse_id")
    UUID warehouseId,
    @JsonProperty("available_qty")
    int availableQty,
    @JsonProperty("low_stock_threshold")
    int lowStockThreshold,
    String severity,
    String color
) {
}
