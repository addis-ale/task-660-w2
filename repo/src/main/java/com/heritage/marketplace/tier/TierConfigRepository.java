package com.heritage.marketplace.tier;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface TierConfigRepository extends JpaRepository<TierConfig, UUID> {

    Optional<TierConfig> findByName(String name);

    Optional<TierConfig> findFirstByOrderByRankAsc();

    @Query(value = """
        SELECT *
        FROM tier_configs
        WHERE spend_threshold_min <= :totalSpend
          AND (spend_threshold_max IS NULL OR spend_threshold_max >= :totalSpend)
        ORDER BY rank DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<TierConfig> findBestTierForSpend(@Param("totalSpend") java.math.BigDecimal totalSpend);
}
