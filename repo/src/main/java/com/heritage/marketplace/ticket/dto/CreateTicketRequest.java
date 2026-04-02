package com.heritage.marketplace.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.ticket.TicketSeverity;
import com.heritage.marketplace.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
    @NotNull(message = "type is required")
    TicketType type,

    @NotNull(message = "severity is required")
    TicketSeverity severity,

    @JsonProperty("location_address")
    String locationAddress,

    @JsonProperty("location_cross_street")
    String locationCrossStreet,

    @NotBlank(message = "description is required")
    @Size(min = 10, max = 2000, message = "description must be between 10 and 2000 characters")
    String description
) {
}
