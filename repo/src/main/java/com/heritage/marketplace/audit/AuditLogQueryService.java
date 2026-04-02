package com.heritage.marketplace.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.marketplace.audit.dto.AuditLogEntryResponse;
import com.heritage.marketplace.common.exception.ApiException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogQueryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AuditLogPageResult search(
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        LocalDateTime from,
        LocalDateTime to,
        int page,
        int pageSize
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND al.entity_type = :entityType ");
            params.addValue("entityType", entityType.trim());
        }
        if (entityId != null) {
            where.append(" AND al.entity_id = :entityId ");
            params.addValue("entityId", entityId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND al.action = :action ");
            params.addValue("action", action.trim());
        }
        if (actorId != null) {
            where.append(" AND al.actor_id = :actorId ");
            params.addValue("actorId", actorId);
        }
        if (from != null) {
            where.append(" AND al.created_at >= :from ");
            params.addValue("from", from);
        }
        if (to != null) {
            where.append(" AND al.created_at <= :to ");
            params.addValue("to", to);
        }

        params.addValue("limit", pageSize);
        params.addValue("offset", page * pageSize);

        String sql = """
            SELECT al.id,
                   al.entity_type,
                   al.entity_id,
                   al.action,
                   al.actor_id,
                   u.display_name AS actor_display_name,
                   al.changes,
                   al.ip_address,
                   al.created_at
            FROM audit_logs al
            LEFT JOIN users u ON u.id = al.actor_id
            %s
            ORDER BY al.created_at DESC, al.id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(where);

        String countSql = "SELECT COUNT(1) FROM audit_logs al " + where;

        List<AuditLogEntryResponse> items = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> changes;
            try {
                changes = objectMapper.readValue(rs.getString("changes"), MAP_TYPE);
            } catch (Exception ex) {
                changes = Map.of();
            }

            Timestamp timestamp = rs.getTimestamp("created_at");

            return new AuditLogEntryResponse(
                rs.getLong("id"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("action"),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_display_name"),
                changes,
                rs.getString("ip_address"),
                timestamp == null ? null : timestamp.toLocalDateTime()
            );
        });

        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);
        if (total == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_QUERY_FAILED", "Unable to count audit logs");
        }

        return new AuditLogPageResult(items, total);
    }

    public List<String> pruneOlderThanYears(int retentionYears) {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(retentionYears).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        String cutoffKey = String.format("%04d_%02d", cutoff.getYear(), cutoff.getMonthValue());

        List<String> partitionNames = jdbcTemplate.query(
            """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename LIKE 'audit_logs_%'
                  AND tablename <> 'audit_logs_default'
                """,
            new MapSqlParameterSource(),
            (rs, rowNum) -> rs.getString("tablename")
        );

        List<String> dropped = new ArrayList<>();
        for (String partition : partitionNames) {
            if (!partition.matches("audit_logs_\\d{4}_\\d{2}")) {
                continue;
            }
            String key = partition.substring("audit_logs_".length());
            if (key.compareTo(cutoffKey) < 0) {
                jdbcTemplate.getJdbcTemplate().execute("DROP TABLE IF EXISTS \"" + partition + "\"");
                dropped.add(partition);
            }
        }

        return dropped;
    }
}
