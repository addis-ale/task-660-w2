package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InventoryResponse(
    UUID id,
    @JsonProperty("listing_id")
    UUID listingId,
    @JsonProperty("warehouse_id")
    UUID warehouseId,
    @JsonProperty("warehouse_name")
    String warehouseName,
    @JsonProperty("available_qty")
    int availableQty,
    @JsonProperty("reserved_qty")
    int reservedQty,
    @JsonProperty("low_stock_threshold")
    int lowStockThreshold,
    @JsonProperty("is_low_stock")
    boolean lowStock
) {
}
