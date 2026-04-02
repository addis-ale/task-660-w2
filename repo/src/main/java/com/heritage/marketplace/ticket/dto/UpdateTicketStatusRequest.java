package com.heritage.marketplace.ticket.dto;

import com.heritage.marketplace.ticket.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(
    @NotNull(message = "status is required")
    TicketStatus status
) {
}
