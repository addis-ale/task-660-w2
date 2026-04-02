package com.heritage.marketplace.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_flags")
public class RiskFlag {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, columnDefinition = "risk_entity_type")
    private RiskEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, columnDefinition = "risk_flag_type")
    private RiskFlagType flagType;

    @Column(name = "incident_count", nullable = false)
    private Integer incidentCount;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RiskEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(RiskEntityType entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public RiskFlagType getFlagType() {
        return flagType;
    }

    public void setFlagType(RiskFlagType flagType) {
        this.flagType = flagType;
    }

    public Integer getIncidentCount() {
        return incidentCount;
    }

    public void setIncidentCount(Integer incidentCount) {
        this.incidentCount = incidentCount;
    }

    public LocalDate getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDate windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDate getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDate windowEnd) {
        this.windowEnd = windowEnd;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
