package com.heritage.marketplace.tier;

import com.heritage.marketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TierService {

    private final TierConfigRepository tierConfigRepository;

    public TierService(TierConfigRepository tierConfigRepository) {
        this.tierConfigRepository = tierConfigRepository;
    }

    public TierConfig resolveBronzeTier() {
        return tierConfigRepository.findByName("Bronze")
            .or(() -> tierConfigRepository.findFirstByOrderByRankAsc())
            .orElseThrow(() -> new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TIER_CONFIG_NOT_FOUND",
                "No tier configuration exists to initialize membership"
            ));
    }
}
