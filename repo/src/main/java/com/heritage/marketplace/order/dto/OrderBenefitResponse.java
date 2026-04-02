package com.heritage.marketplace.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.tier.BenefitType;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderBenefitResponse(
    UUID id,
    String name,
    BenefitType type,
    @JsonProperty("applied_value")
    BigDecimal appliedValue
) {
}
