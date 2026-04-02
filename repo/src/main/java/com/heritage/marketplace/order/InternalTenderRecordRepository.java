package com.heritage.marketplace.order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalTenderRecordRepository extends JpaRepository<InternalTenderRecord, UUID> {

    Optional<InternalTenderRecord> findByIdempotencyKey(String idempotencyKey);
}
