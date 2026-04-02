package com.heritage.marketplace.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitIssuanceRepository extends JpaRepository<BenefitIssuance, UUID> {
}
