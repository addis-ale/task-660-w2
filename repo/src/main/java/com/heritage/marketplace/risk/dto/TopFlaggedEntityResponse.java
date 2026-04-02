package com.heritage.marketplace.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.risk.RiskFlagType;
import java.time.LocalDate;
import java.util.UUID;

public record TopFlaggedEntityResponse(
    @JsonProperty("entity_id")
    UUID entityId,
    @JsonProperty("display_name")
    String displayName,
    @JsonProperty("flag_type")
    RiskFlagType flagType,
    @JsonProperty("incident_count")
    int incidentCount,
    @JsonProperty("window_start")
    LocalDate windowStart,
    @JsonProperty("window_end")
    LocalDate windowEnd
) {
}
