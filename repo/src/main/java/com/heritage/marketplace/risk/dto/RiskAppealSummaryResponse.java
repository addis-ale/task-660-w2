package com.heritage.marketplace.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.appeal.AppealStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record RiskAppealSummaryResponse(
    UUID id,
    @JsonProperty("ticket_id")
    UUID ticketId,
    AppealStatus status,
    @JsonProperty("created_at")
    LocalDateTime createdAt,
    @JsonProperty("decided_at")
    LocalDateTime decidedAt
) {
}
