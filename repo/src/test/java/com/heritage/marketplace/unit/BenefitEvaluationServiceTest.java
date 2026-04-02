package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.heritage.marketplace.tier.BenefitEvaluationService;
import com.heritage.marketplace.tier.BenefitPackage;
import com.heritage.marketplace.tier.BenefitType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BenefitEvaluationServiceTest {

    private BenefitEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new BenefitEvaluationService();
    }

    @Test
    @DisplayName("should return empty list for null input")
    void returnEmptyForNull() {
        List<BenefitPackage> result = service.selectApplicableBenefits(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should return empty list for empty input")
    void returnEmptyForEmptyInput() {
        List<BenefitPackage> result = service.selectApplicableBenefits(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should select exclusive price over percentage discount")
    void selectExclusivePriceOverPercentageDiscount() {
        BenefitPackage exclusive = buildBenefit(BenefitType.EXCLUSIVE_PRICE, BigDecimal.valueOf(80), 10, null);
        BenefitPackage percentage = buildBenefit(BenefitType.PERCENTAGE_DISCOUNT, BigDecimal.valueOf(15), 5, null);

        List<BenefitPackage> result = service.selectApplicableBenefits(List.of(exclusive, percentage));

        assertEquals(1, result.size());
        assertEquals(BenefitType.EXCLUSIVE_PRICE, result.get(0).getType());
    }

    @Test
    @DisplayName("should select highest priority exclusive price")
    void selectHighestPriorityExclusivePrice() {
        BenefitPackage lowPriority = buildBenefit(BenefitType.EXCLUSIVE_PRICE, BigDecimal.valueOf(90), 5, null);
        BenefitPackage highPriority = buildBenefit(BenefitType.EXCLUSIVE_PRICE, BigDecimal.valueOf(75), 10, null);

        List<BenefitPackage> result = service.selectApplicableBenefits(List.of(lowPriority, highPriority));

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(75), result.get(0).getValue());
    }

    @Test
    @DisplayName("should combine percentage discount and free shipping when no exclusive price")
    void combinePercentageAndFreeShipping() {
        BenefitPackage percentage = buildBenefit(BenefitType.PERCENTAGE_DISCOUNT, BigDecimal.valueOf(20), 5, null);
        BenefitPackage shipping = buildBenefit(BenefitType.FREE_SHIPPING, BigDecimal.ZERO, 3, null);

        List<BenefitPackage> result = service.selectApplicableBenefits(List.of(percentage, shipping));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(bp -> bp.getType() == BenefitType.PERCENTAGE_DISCOUNT));
        assertTrue(result.stream().anyMatch(bp -> bp.getType() == BenefitType.FREE_SHIPPING));
    }

    @Test
    @DisplayName("should pick highest priority within mutual exclusion group")
    void pickHighestPriorityInGroup() {
        BenefitPackage low = buildBenefit(BenefitType.PERCENTAGE_DISCOUNT, BigDecimal.valueOf(10), 3, "DISCOUNT_GROUP");
        BenefitPackage high = buildBenefit(BenefitType.PERCENTAGE_DISCOUNT, BigDecimal.valueOf(20), 8, "DISCOUNT_GROUP");

        List<BenefitPackage> result = service.selectApplicableBenefits(List.of(low, high));

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(20), result.get(0).getValue());
    }

    @Test
    @DisplayName("should select only free shipping if its the only benefit")
    void selectOnlyFreeShipping() {
        BenefitPackage shipping = buildBenefit(BenefitType.FREE_SHIPPING, BigDecimal.ZERO, 1, null);

        List<BenefitPackage> result = service.selectApplicableBenefits(List.of(shipping));

        assertEquals(1, result.size());
        assertEquals(BenefitType.FREE_SHIPPING, result.get(0).getType());
    }

    private BenefitPackage buildBenefit(BenefitType type, BigDecimal value, int priority, String group) {
        BenefitPackage bp = new BenefitPackage();
        bp.setId(UUID.randomUUID());
        bp.setName(type.name() + "-" + priority);
        bp.setType(type);
        bp.setValue(value);
        bp.setPriority(priority);
        bp.setMutualExclusionGroup(group);
        return bp;
    }
}
