package com.heritage.marketplace.risk;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RiskFlagRepository extends JpaRepository<RiskFlag, UUID>, JpaSpecificationExecutor<RiskFlag> {

    long countByWindowEndGreaterThanEqual(LocalDate date);

    List<RiskFlag> findTop10ByEntityTypeOrderByIncidentCountDescCreatedAtDesc(RiskEntityType entityType);

    List<RiskFlag> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(RiskEntityType entityType, UUID entityId);

    Optional<RiskFlag> findByEntityTypeAndEntityIdAndFlagTypeAndWindowStartAndWindowEnd(
        RiskEntityType entityType,
        UUID entityId,
        RiskFlagType flagType,
        LocalDate windowStart,
        LocalDate windowEnd
    );

    Page<RiskFlag> findByEntityTypeAndFlagType(RiskEntityType entityType, RiskFlagType flagType, Pageable pageable);

    Page<RiskFlag> findByEntityType(RiskEntityType entityType, Pageable pageable);

    Page<RiskFlag> findByFlagType(RiskFlagType flagType, Pageable pageable);
}
