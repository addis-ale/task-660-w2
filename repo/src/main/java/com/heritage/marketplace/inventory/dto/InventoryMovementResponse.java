package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.inventory.InventoryDocumentType;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
    UUID id,
    @JsonProperty("document_type")
    InventoryDocumentType documentType,
    @JsonProperty("document_ref")
    String documentRef,
    @JsonProperty("quantity_change")
    int quantityChange,
    @JsonProperty("operator_id")
    UUID operatorId,
    @JsonProperty("warehouse_id")
    UUID warehouseId,
    String notes,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
