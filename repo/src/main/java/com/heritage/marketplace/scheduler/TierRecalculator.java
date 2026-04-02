package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.audit.AuditService;
import com.heritage.marketplace.tier.Membership;
import com.heritage.marketplace.tier.MembershipRepository;
import com.heritage.marketplace.tier.TierConfig;
import com.heritage.marketplace.tier.TierConfigRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TierRecalculator {

    private final MembershipRepository membershipRepository;
    private final TierConfigRepository tierConfigRepository;
    private final AuditService auditService;

    public TierRecalculator(
        MembershipRepository membershipRepository,
        TierConfigRepository tierConfigRepository,
        AuditService auditService
    ) {
        this.membershipRepository = membershipRepository;
        this.tierConfigRepository = tierConfigRepository;
        this.auditService = auditService;
    }

    @Scheduled(cron = "${app.scheduler.tier-recalculation.cron:0 0 2 * * *}")
    @Transactional
    public void recalculateTiers() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        for (Membership membership : membershipRepository.findAll()) {
            TierConfig currentTier = membership.getTier();
            TierConfig expectedTier = tierConfigRepository.findBestTierForSpend(membership.getTotalSpend()).orElse(currentTier);

            boolean tierChanged = !currentTier.getId().equals(expectedTier.getId());
            boolean validityExpired = membership.getTierValidUntil() == null || membership.getTierValidUntil().isBefore(today);
            if (!tierChanged && !validityExpired) {
                continue;
            }

            LocalDate previousValidUntil = membership.getTierValidUntil();

            membership.setTier(expectedTier);
            membership.setTierValidUntil(today.plusYears(1));
            if (tierChanged) {
                membership.setUpgradedAt(now);
            }
            membershipRepository.save(membership);

            Map<String, Object> before = new HashMap<>();
            before.put("tier", currentTier.getName());
            before.put("tierValidUntil", previousValidUntil);

            auditService.log(
                "MEMBERSHIP",
                membership.getId(),
                "RECALCULATE_TIER",
                null,
                before,
                Map.of("tier", expectedTier.getName(), "tierValidUntil", today.plusYears(1)),
                "system"
            );
        }
    }
}
