package com.heritage.marketplace.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

public record UpdateThresholdRequest(
    @JsonProperty("low_stock_threshold")
    @Min(value = 0, message = "low_stock_threshold must be 0 or greater")
    int lowStockThreshold
) {
}
