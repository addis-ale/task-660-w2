package com.heritage.marketplace.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RiskDashboardResponse(
    @JsonProperty("open_tickets_low")
    long openTicketsLow,
    @JsonProperty("open_tickets_medium")
    long openTicketsMedium,
    @JsonProperty("open_tickets_high")
    long openTicketsHigh,
    @JsonProperty("avg_resolution_hours")
    double avgResolutionHours,
    @JsonProperty("escalation_rate_percent")
    double escalationRatePercent,
    @JsonProperty("active_risk_flags")
    long activeRiskFlags,
    @JsonProperty("top_flagged_sellers")
    List<TopFlaggedEntityResponse> topFlaggedSellers,
    @JsonProperty("top_flagged_members")
    List<TopFlaggedEntityResponse> topFlaggedMembers
) {
}
