package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.inventory.InventoryDocumentType;
import java.util.UUID;

public record InventoryDocumentMovementResponse(
    @JsonProperty("movement_id")
    UUID movementId,
    @JsonProperty("inventory_id")
    UUID inventoryId,
    @JsonProperty("listing_id")
    UUID listingId,
    @JsonProperty("warehouse_id")
    UUID warehouseId,
    @JsonProperty("document_type")
    InventoryDocumentType documentType,
    @JsonProperty("document_ref")
    String documentRef,
    @JsonProperty("quantity_change")
    int quantityChange,
    @JsonProperty("new_available_qty")
    int newAvailableQty,
    @JsonProperty("is_low_stock")
    boolean lowStock
) {
}
