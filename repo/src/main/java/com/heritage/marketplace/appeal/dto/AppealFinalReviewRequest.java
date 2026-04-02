package com.heritage.marketplace.appeal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppealFinalReviewRequest(
    @NotNull(message = "decision is required")
    AppealFinalDecision decision,

    @JsonProperty("decision_notes")
    @NotBlank(message = "decision_notes is required")
    String decisionNotes
) {
}
