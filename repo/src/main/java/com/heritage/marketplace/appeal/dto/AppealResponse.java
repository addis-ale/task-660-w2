package com.heritage.marketplace.appeal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.appeal.AppealStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record AppealResponse(
    UUID id,
    @JsonProperty("ticket_id")
    UUID ticketId,
    @JsonProperty("appellant_id")
    UUID appellantId,
    @JsonProperty("appellant_name")
    String appellantName,
    String reason,
    AppealStatus status,
    @JsonProperty("reviewer_id")
    UUID reviewerId,
    @JsonProperty("admin_reviewer_id")
    UUID adminReviewerId,
    @JsonProperty("decision_notes")
    String decisionNotes,
    @JsonProperty("created_at")
    LocalDateTime createdAt,
    @JsonProperty("decided_at")
    LocalDateTime decidedAt
) {
}
