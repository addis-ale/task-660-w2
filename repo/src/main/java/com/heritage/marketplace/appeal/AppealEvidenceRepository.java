package com.heritage.marketplace.appeal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppealEvidenceRepository extends JpaRepository<AppealEvidence, UUID> {

    List<AppealEvidence> findByAppeal_IdOrderByUploadedAtAsc(UUID appealId);

    Optional<AppealEvidence> findByIdAndAppeal_Id(UUID evidenceId, UUID appealId);
}
