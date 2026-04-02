package com.heritage.marketplace.listing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record TierPricingResponse(
    @JsonProperty("exclusive_price")
    BigDecimal exclusivePrice,
    @JsonProperty("applicable_tier")
    String applicableTier,
    String note
) {
}
