package com.heritage.marketplace.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record AcknowledgeTicketRequest(
    @JsonProperty("assigned_to")
    UUID assignedTo
) {
}
