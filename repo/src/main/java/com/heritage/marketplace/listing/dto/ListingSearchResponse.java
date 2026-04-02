package com.heritage.marketplace.listing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.listing.ListingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ListingSearchResponse(
    UUID id,
    String title,
    String category,
    BigDecimal price,
    List<String> tags,
    String neighborhood,
    @JsonProperty("trending_score")
    BigDecimal trendingScore,
    ListingStatus status,
    @JsonProperty("created_at")
    LocalDateTime createdAt,
    @JsonProperty("distance_miles")
    Double distanceMiles
) {
}
