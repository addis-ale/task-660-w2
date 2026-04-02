package com.heritage.marketplace.listing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.listing.ListingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ListingDetailResponse(
    UUID id,
    @JsonProperty("seller_id")
    UUID sellerId,
    String title,
    String description,
    String category,
    BigDecimal price,
    List<String> tags,
    String neighborhood,
    BigDecimal latitude,
    BigDecimal longitude,
    @JsonProperty("layout_sqft")
    BigDecimal layoutSqft,
    @JsonProperty("availability_start")
    LocalDate availabilityStart,
    @JsonProperty("availability_end")
    LocalDate availabilityEnd,
    ListingStatus status,
    @JsonProperty("view_count")
    int viewCount,
    @JsonProperty("order_count_7d")
    int orderCount7d,
    @JsonProperty("trending_score")
    BigDecimal trendingScore,
    @JsonProperty("created_at")
    LocalDateTime createdAt,
    @JsonProperty("stock_summary")
    List<StockSummaryResponse> stockSummary,
    @JsonProperty("tier_pricing")
    TierPricingResponse tierPricing
) {
}
