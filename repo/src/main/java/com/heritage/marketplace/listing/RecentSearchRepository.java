package com.heritage.marketplace.listing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentSearchRepository extends JpaRepository<RecentSearch, UUID> {

    List<RecentSearch> findTop20ByUser_IdOrderBySearchedAtDesc(UUID userId);

    void deleteByUser_Id(UUID userId);
}
