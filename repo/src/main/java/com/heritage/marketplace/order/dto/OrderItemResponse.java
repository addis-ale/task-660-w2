package com.heritage.marketplace.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
    @JsonProperty("listing_id")
    UUID listingId,
    @JsonProperty("listing_title")
    String listingTitle,
    int quantity,
    @JsonProperty("unit_price")
    BigDecimal unitPrice,
    @JsonProperty("line_total")
    BigDecimal lineTotal,
    @JsonProperty("applied_benefit_id")
    UUID appliedBenefitId
) {
}
