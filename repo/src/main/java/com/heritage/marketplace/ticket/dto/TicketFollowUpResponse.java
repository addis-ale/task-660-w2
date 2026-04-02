package com.heritage.marketplace.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record TicketFollowUpResponse(
    UUID id,
    @JsonProperty("ticket_id")
    UUID ticketId,
    @JsonProperty("author_id")
    UUID authorId,
    @JsonProperty("author_name")
    String authorName,
    String message,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
