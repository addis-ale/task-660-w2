package com.heritage.marketplace.tier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BenefitEvaluationService {

    public List<BenefitPackage> selectApplicableBenefits(Collection<BenefitPackage> rawBenefits) {
        if (rawBenefits == null || rawBenefits.isEmpty()) {
            return List.of();
        }

        Map<String, BenefitPackage> groupWinners = new HashMap<>();
        List<BenefitPackage> ungrouped = new ArrayList<>();

        for (BenefitPackage benefit : rawBenefits) {
            if (benefit.getMutualExclusionGroup() == null || benefit.getMutualExclusionGroup().isBlank()) {
                ungrouped.add(benefit);
                continue;
            }

            groupWinners.merge(
                benefit.getMutualExclusionGroup(),
                benefit,
                (a, b) -> comparePriority(a, b) >= 0 ? a : b
            );
        }

        List<BenefitPackage> candidates = new ArrayList<>(ungrouped);
        candidates.addAll(groupWinners.values());

        BenefitPackage exclusive = candidates.stream()
            .filter(bp -> bp.getType() == BenefitType.EXCLUSIVE_PRICE)
            .max(this::comparePriority)
            .orElse(null);

        if (exclusive != null) {
            return List.of(exclusive);
        }

        List<BenefitPackage> selected = new ArrayList<>();

        candidates.stream()
            .filter(bp -> bp.getType() == BenefitType.PERCENTAGE_DISCOUNT)
            .max(this::comparePriority)
            .ifPresent(selected::add);

        candidates.stream()
            .filter(bp -> bp.getType() == BenefitType.FREE_SHIPPING)
            .max(this::comparePriority)
            .ifPresent(selected::add);

        return selected;
    }

    private int comparePriority(BenefitPackage left, BenefitPackage right) {
        return Comparator
            .comparingInt(BenefitPackage::getPriority)
            .thenComparing(bp -> bp.getValue() == null ? BigDecimal.ZERO : bp.getValue())
            .compare(left, right);
    }
}
