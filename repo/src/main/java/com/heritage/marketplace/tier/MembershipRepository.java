package com.heritage.marketplace.tier;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByUser_Id(UUID userId);

    List<Membership> findByTierValidUntilBefore(LocalDate date);
}
