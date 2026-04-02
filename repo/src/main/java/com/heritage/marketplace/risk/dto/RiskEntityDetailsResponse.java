package com.heritage.marketplace.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.risk.RiskEntityType;
import com.heritage.marketplace.user.UserRole;
import com.heritage.marketplace.user.UserStatus;
import java.util.List;
import java.util.UUID;

public record RiskEntityDetailsResponse(
    @JsonProperty("entity_type")
    RiskEntityType entityType,
    @JsonProperty("entity_id")
    UUID entityId,
    String email,
    @JsonProperty("display_name")
    String displayName,
    UserRole role,
    UserStatus status,
    List<RiskFlagResponse> flags,
    @JsonProperty("recent_tickets")
    List<RiskTicketSummaryResponse> recentTickets,
    @JsonProperty("recent_appeals")
    List<RiskAppealSummaryResponse> recentAppeals,
    @JsonProperty("risk_score")
    double riskScore,
    String recommendation
) {
}
