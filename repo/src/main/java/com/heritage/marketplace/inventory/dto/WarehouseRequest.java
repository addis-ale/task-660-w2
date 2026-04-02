package com.heritage.marketplace.inventory.dto;

import com.heritage.marketplace.inventory.WarehouseStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WarehouseRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Address is required")
    String address,

    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
    BigDecimal latitude,

    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
    BigDecimal longitude,

    @NotNull(message = "Status is required")
    WarehouseStatus status
) {
}
