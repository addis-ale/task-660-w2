package com.heritage.marketplace.inventory.dto;

import com.heritage.marketplace.inventory.WarehouseStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record WarehouseResponse(
    UUID id,
    String name,
    String address,
    BigDecimal latitude,
    BigDecimal longitude,
    WarehouseStatus status
) {
}
