package com.heritage.marketplace.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ResolveTicketRequest(
    @JsonProperty("closure_code")
    @NotBlank(message = "closure_code is required")
    String closureCode,

    @JsonProperty("closure_notes")
    @NotBlank(message = "closure_notes is required")
    String closureNotes
) {
}
