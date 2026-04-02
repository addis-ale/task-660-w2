package com.heritage.marketplace.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.marketplace.common.exception.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void log(
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        Object changesBefore,
        Object changesAfter,
        String ipAddress
    ) {
        String changesJson;
        try {
            Map<String, Object> payload = Map.of(
                "before", changesBefore == null ? Map.of() : changesBefore,
                "after", changesAfter == null ? Map.of() : changesAfter
            );
            changesJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_LOG_SERIALIZATION_FAILED", "Failed to serialize audit changes");
        }

        String sql = """
            INSERT INTO audit_logs (entity_type, entity_id, action, actor_id, changes, ip_address, created_at)
            VALUES (:entityType, :entityId, :action, :actorId, CAST(:changes AS jsonb), :ipAddress, NOW())
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("entityType", entityType)
            .addValue("entityId", entityId)
            .addValue("action", action)
            .addValue("actorId", actorId)
            .addValue("changes", changesJson)
            .addValue("ipAddress", ipAddress == null ? "system" : ipAddress);

        jdbcTemplate.update(sql, params);
    }
}
