package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.listing.ListingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TrendingScoreCalculator {

    private final ListingRepository listingRepository;

    public TrendingScoreCalculator(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    @Scheduled(cron = "${app.scheduler.trending-score.cron:0 0 * * * *}")
    @Transactional
    public void recalculateTrendingScores() {
        listingRepository.recalculateTrendingScores();
    }
}
