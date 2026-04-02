package com.heritage.marketplace.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.ticket.TicketSeverity;
import com.heritage.marketplace.ticket.TicketStatus;
import com.heritage.marketplace.ticket.TicketType;
import java.time.LocalDateTime;
import java.util.UUID;

public record TicketResponse(
    UUID id,
    @JsonProperty("reporter_id")
    UUID reporterId,
    @JsonProperty("reporter_name")
    String reporterName,
    TicketType type,
    TicketSeverity severity,
    TicketStatus status,
    @JsonProperty("assigned_to")
    UUID assignedTo,
    @JsonProperty("assigned_to_name")
    String assignedToName,
    @JsonProperty("location_address")
    String locationAddress,
    @JsonProperty("location_cross_street")
    String locationCrossStreet,
    String description,
    @JsonProperty("closure_code")
    String closureCode,
    @JsonProperty("closure_notes")
    String closureNotes,
    @JsonProperty("sla_acknowledge_by")
    LocalDateTime slaAcknowledgeBy,
    @JsonProperty("sla_resolve_by")
    LocalDateTime slaResolveBy,
    @JsonProperty("acknowledged_at")
    LocalDateTime acknowledgedAt,
    @JsonProperty("resolved_at")
    LocalDateTime resolvedAt,
    @JsonProperty("escalated_at")
    LocalDateTime escalatedAt,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
