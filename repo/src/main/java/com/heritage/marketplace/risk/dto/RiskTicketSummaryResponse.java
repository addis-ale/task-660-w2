package com.heritage.marketplace.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.ticket.TicketSeverity;
import com.heritage.marketplace.ticket.TicketStatus;
import com.heritage.marketplace.ticket.TicketType;
import java.time.LocalDateTime;
import java.util.UUID;

public record RiskTicketSummaryResponse(
    UUID id,
    TicketType type,
    TicketSeverity severity,
    TicketStatus status,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
