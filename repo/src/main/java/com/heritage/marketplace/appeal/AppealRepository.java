package com.heritage.marketplace.appeal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppealRepository extends JpaRepository<Appeal, UUID>, JpaSpecificationExecutor<Appeal> {

    List<Appeal> findTop20ByAppellant_IdOrderByCreatedAtDesc(UUID appellantId);
}
