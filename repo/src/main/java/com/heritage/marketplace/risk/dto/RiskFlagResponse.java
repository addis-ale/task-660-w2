package com.heritage.marketplace.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.risk.RiskEntityType;
import com.heritage.marketplace.risk.RiskFlagType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RiskFlagResponse(
    UUID id,
    @JsonProperty("entity_type")
    RiskEntityType entityType,
    @JsonProperty("entity_id")
    UUID entityId,
    @JsonProperty("flag_type")
    RiskFlagType flagType,
    @JsonProperty("incident_count")
    int incidentCount,
    @JsonProperty("window_start")
    LocalDate windowStart,
    @JsonProperty("window_end")
    LocalDate windowEnd,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
