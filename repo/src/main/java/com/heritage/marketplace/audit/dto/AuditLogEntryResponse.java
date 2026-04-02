package com.heritage.marketplace.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogEntryResponse(
    long id,
    @JsonProperty("entity_type")
    String entityType,
    @JsonProperty("entity_id")
    UUID entityId,
    String action,
    @JsonProperty("actor_id")
    UUID actorId,
    @JsonProperty("actor_display_name")
    String actorDisplayName,
    Map<String, Object> changes,
    @JsonProperty("ip_address")
    String ipAddress,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
