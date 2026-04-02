package com.heritage.marketplace.tier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tier_configs")
public class TierConfig {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "spend_threshold_min", nullable = false)
    private BigDecimal spendThresholdMin;

    @Column(name = "spend_threshold_max")
    private BigDecimal spendThresholdMax;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getSpendThresholdMin() {
        return spendThresholdMin;
    }

    public void setSpendThresholdMin(BigDecimal spendThresholdMin) {
        this.spendThresholdMin = spendThresholdMin;
    }

    public BigDecimal getSpendThresholdMax() {
        return spendThresholdMax;
    }

    public void setSpendThresholdMax(BigDecimal spendThresholdMax) {
        this.spendThresholdMax = spendThresholdMax;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
