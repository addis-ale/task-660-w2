package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StocktakeRequest(
    @JsonProperty("warehouse_id")
    @NotNull(message = "warehouse_id is required")
    UUID warehouseId,

    @NotEmpty(message = "items are required")
    List<@Valid StocktakeItemRequest> items
) {
}
