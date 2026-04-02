package com.heritage.marketplace.listing;

import com.heritage.marketplace.listing.dto.ListingSearchResponse;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ListingSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ListingSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ListingPageResult search(ListingSearchCriteria criteria) {
        String distanceExpr = "NULL";
        if (criteria.lat() != null && criteria.lng() != null) {
            distanceExpr = "(3958.8 * 2 * ASIN(SQRT(POWER(SIN((RADIANS(CAST(l.latitude AS DOUBLE PRECISION)) - RADIANS(:lat)) / 2), 2) + COS(RADIANS(:lat)) * COS(RADIANS(CAST(l.latitude AS DOUBLE PRECISION))) * POWER(SIN((RADIANS(CAST(l.longitude AS DOUBLE PRECISION)) - RADIANS(:lng)) / 2), 2))))";
        }

        StringBuilder where = new StringBuilder(" WHERE l.status = 'ACTIVE' ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            where.append(" AND to_tsvector('english', COALESCE(l.title, '') || ' ' || COALESCE(l.description, '')) @@ plainto_tsquery('english', :keyword) ");
            params.addValue("keyword", criteria.keyword().trim());
        }

        if (criteria.neighborhood() != null && !criteria.neighborhood().isBlank()) {
            where.append(" AND l.neighborhood = :neighborhood ");
            params.addValue("neighborhood", criteria.neighborhood().trim());
        }

        if (criteria.priceMin() != null) {
            where.append(" AND l.price >= :priceMin ");
            params.addValue("priceMin", criteria.priceMin());
        }

        if (criteria.priceMax() != null) {
            where.append(" AND l.price <= :priceMax ");
            params.addValue("priceMax", criteria.priceMax());
        }

        if (criteria.sqftMin() != null) {
            where.append(" AND l.layout_sqft >= :sqftMin ");
            params.addValue("sqftMin", criteria.sqftMin());
        }

        if (criteria.sqftMax() != null) {
            where.append(" AND l.layout_sqft <= :sqftMax ");
            params.addValue("sqftMax", criteria.sqftMax());
        }

        if (criteria.availFrom() != null) {
            where.append(" AND (l.availability_end IS NULL OR l.availability_end >= :availFrom) ");
            params.addValue("availFrom", criteria.availFrom());
        }

        if (criteria.availTo() != null) {
            where.append(" AND (l.availability_start IS NULL OR l.availability_start <= :availTo) ");
            params.addValue("availTo", criteria.availTo());
        }

        if (criteria.tags() != null && !criteria.tags().isEmpty()) {
            where.append(" AND l.tags && CAST(:tags AS text[]) ");
            params.addValue("tags", toPgTextArray(criteria.tags()));
        }

        if (criteria.lat() != null && criteria.lng() != null) {
            params.addValue("lat", criteria.lat());
            params.addValue("lng", criteria.lng());

            if (criteria.radiusMiles() != null) {
                where.append(" AND ").append(distanceExpr).append(" <= :radiusMiles ");
                params.addValue("radiusMiles", criteria.radiusMiles());
            }
        }

        String orderBy = resolveOrderBy(criteria.sort(), criteria.lat() != null && criteria.lng() != null);

        int limit = criteria.pageSize();
        int offset = criteria.page() * criteria.pageSize();
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String selectSql = """
            SELECT l.id,
                   l.title,
                   l.category,
                   l.price,
                   l.tags,
                   l.neighborhood,
                   l.trending_score,
                   l.status,
                   l.created_at,
                   %s AS distance_miles
            FROM listings l
            %s
            %s
            LIMIT :limit OFFSET :offset
            """.formatted(distanceExpr, where, orderBy);

        String countSql = "SELECT COUNT(1) FROM listings l " + where;

        List<ListingSearchResponse> rows = jdbcTemplate.query(selectSql, params, listingRowMapper());
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);
        return new ListingPageResult(rows, total == null ? 0 : total);
    }

    private String resolveOrderBy(String sort, boolean hasDistance) {
        String safeSort = sort == null || sort.isBlank() ? "newest" : sort;
        return switch (safeSort) {
            case "price_asc" -> "ORDER BY l.price ASC, l.created_at DESC";
            case "price_desc" -> "ORDER BY l.price DESC, l.created_at DESC";
            case "distance" -> hasDistance ? "ORDER BY distance_miles ASC NULLS LAST" : "ORDER BY l.created_at DESC";
            case "popularity" -> "ORDER BY l.trending_score DESC, l.created_at DESC";
            default -> "ORDER BY l.created_at DESC";
        };
    }

    private String toPgTextArray(List<String> tags) {
        List<String> sanitized = new ArrayList<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                sanitized.add("\"" + tag.replace("\"", "\\\"") + "\"");
            }
        }
        return "{" + String.join(",", sanitized) + "}";
    }

    private RowMapper<ListingSearchResponse> listingRowMapper() {
        return (rs, rowNum) -> new ListingSearchResponse(
            UUID.fromString(rs.getString("id")),
            rs.getString("title"),
            rs.getString("category"),
            rs.getBigDecimal("price"),
            readTags(rs),
            rs.getString("neighborhood"),
            rs.getBigDecimal("trending_score"),
            ListingStatus.valueOf(rs.getString("status")),
            readTimestamp(rs),
            readDistance(rs)
        );
    }

    private List<String> readTags(ResultSet rs) throws SQLException {
        Array sqlArray = rs.getArray("tags");
        if (sqlArray == null) {
            return List.of();
        }
        Object arr = sqlArray.getArray();
        if (arr instanceof String[] values) {
            return Arrays.asList(values);
        }
        return List.of();
    }

    private LocalDateTime readTimestamp(ResultSet rs) throws SQLException {
        return rs.getTimestamp("created_at").toLocalDateTime();
    }

    private Double readDistance(ResultSet rs) throws SQLException {
        Object value = rs.getObject("distance_miles");
        if (value == null) {
            return null;
        }
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }

    public void saveRecentSearch(UUID userId, String query, String filtersJson) {
        String sql = """
            INSERT INTO recent_searches (id, user_id, query, filters, searched_at)
            VALUES (gen_random_uuid(), :userId, :query, CAST(:filters AS jsonb), NOW())
            """;

        jdbcTemplate.update(sql, new MapSqlParameterSource(Map.of(
            "userId", userId,
            "query", query == null ? "" : query,
            "filters", filtersJson == null ? "{}" : filtersJson
        )));
    }

    public void refreshTrendingScores() {
        String sql = """
            UPDATE listings
            SET trending_score = (view_count * 0.4) + (order_count_7d * 0.6)
            """;
        jdbcTemplate.update(sql, new MapSqlParameterSource());
    }
}
