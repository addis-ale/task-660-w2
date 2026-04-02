package com.heritage.marketplace.tier;

import com.heritage.marketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "benefit_packages")
public class BenefitPackage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private TierConfig tier;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "benefit_type")
    private BenefitType type;

    @Column
    private BigDecimal value;

    @Column(name = "scope_category")
    private String scopeCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_seller_id")
    private User scopeSeller;

    @Column(name = "scope_date_start")
    private LocalDate scopeDateStart;

    @Column(name = "scope_date_end")
    private LocalDate scopeDateEnd;

    @Column(nullable = false)
    private boolean stackable;

    @Column(name = "mutual_exclusion_group")
    private String mutualExclusionGroup;

    @Column(nullable = false)
    private int priority;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TierConfig getTier() {
        return tier;
    }

    public void setTier(TierConfig tier) {
        this.tier = tier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BenefitType getType() {
        return type;
    }

    public void setType(BenefitType type) {
        this.type = type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getScopeCategory() {
        return scopeCategory;
    }

    public void setScopeCategory(String scopeCategory) {
        this.scopeCategory = scopeCategory;
    }

    public User getScopeSeller() {
        return scopeSeller;
    }

    public void setScopeSeller(User scopeSeller) {
        this.scopeSeller = scopeSeller;
    }

    public LocalDate getScopeDateStart() {
        return scopeDateStart;
    }

    public void setScopeDateStart(LocalDate scopeDateStart) {
        this.scopeDateStart = scopeDateStart;
    }

    public LocalDate getScopeDateEnd() {
        return scopeDateEnd;
    }

    public void setScopeDateEnd(LocalDate scopeDateEnd) {
        this.scopeDateEnd = scopeDateEnd;
    }

    public boolean isStackable() {
        return stackable;
    }

    public void setStackable(boolean stackable) {
        this.stackable = stackable;
    }

    public String getMutualExclusionGroup() {
        return mutualExclusionGroup;
    }

    public void setMutualExclusionGroup(String mutualExclusionGroup) {
        this.mutualExclusionGroup = mutualExclusionGroup;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
