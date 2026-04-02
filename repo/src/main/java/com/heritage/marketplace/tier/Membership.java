package com.heritage.marketplace.tier;

import com.heritage.marketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private TierConfig tier;

    @Column(name = "total_spend", nullable = false)
    private BigDecimal totalSpend;

    @Column(name = "tier_valid_until", nullable = false)
    private LocalDate tierValidUntil;

    @Column(name = "upgraded_at")
    private LocalDateTime upgradedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TierConfig getTier() {
        return tier;
    }

    public void setTier(TierConfig tier) {
        this.tier = tier;
    }

    public BigDecimal getTotalSpend() {
        return totalSpend;
    }

    public void setTotalSpend(BigDecimal totalSpend) {
        this.totalSpend = totalSpend;
    }

    public LocalDate getTierValidUntil() {
        return tierValidUntil;
    }

    public void setTierValidUntil(LocalDate tierValidUntil) {
        this.tierValidUntil = tierValidUntil;
    }

    public LocalDateTime getUpgradedAt() {
        return upgradedAt;
    }

    public void setUpgradedAt(LocalDateTime upgradedAt) {
        this.upgradedAt = upgradedAt;
    }
}
