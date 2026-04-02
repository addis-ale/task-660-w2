package com.heritage.marketplace.listing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    Optional<Listing> findByIdAndStatusNot(UUID id, ListingStatus status);

    List<Listing> findByStatusOrderByTrendingScoreDesc(ListingStatus status, Pageable pageable);

    @Modifying
    @Query("""
        UPDATE Listing l
        SET l.viewCount = l.viewCount + 1,
            l.trendingScore = ((l.viewCount + 1) * 0.4) + (l.orderCount7d * 0.6)
        WHERE l.id = :listingId
        """)
    int incrementViewCount(@Param("listingId") UUID listingId);

    @Modifying
    @Query("""
        UPDATE Listing l
        SET l.trendingScore = (l.viewCount * 0.4) + (l.orderCount7d * 0.6)
        """)
    int recalculateTrendingScores();
}
