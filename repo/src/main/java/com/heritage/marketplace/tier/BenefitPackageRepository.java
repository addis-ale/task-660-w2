package com.heritage.marketplace.tier;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitPackageRepository extends JpaRepository<BenefitPackage, UUID> {

    List<BenefitPackage> findByTier_Id(UUID tierId);
}
