package com.heritage.marketplace.listing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecentSearchResponse(
    UUID id,
    String query,
    String filters,
    @JsonProperty("searched_at")
    LocalDateTime searchedAt
) {
}
