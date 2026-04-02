package com.heritage.marketplace.ticket;

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
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ticket_type")
    private TicketType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ticket_severity")
    private TicketSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ticket_status")
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "location_address")
    private String locationAddress;

    @Column(name = "location_cross_street")
    private String locationCrossStreet;

    @Column(nullable = false)
    private String description;

    @Column(name = "closure_code")
    private String closureCode;

    @Column(name = "closure_notes")
    private String closureNotes;

    @Column(name = "sla_acknowledge_by", insertable = false, updatable = false)
    private LocalDateTime slaAcknowledgeBy;

    @Column(name = "sla_resolve_by", insertable = false, updatable = false)
    private LocalDateTime slaResolveBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getReporter() {
        return reporter;
    }

    public void setReporter(User reporter) {
        this.reporter = reporter;
    }

    public TicketType getType() {
        return type;
    }

    public void setType(TicketType type) {
        this.type = type;
    }

    public TicketSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(TicketSeverity severity) {
        this.severity = severity;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }

    public String getLocationCrossStreet() {
        return locationCrossStreet;
    }

    public void setLocationCrossStreet(String locationCrossStreet) {
        this.locationCrossStreet = locationCrossStreet;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClosureCode() {
        return closureCode;
    }

    public void setClosureCode(String closureCode) {
        this.closureCode = closureCode;
    }

    public String getClosureNotes() {
        return closureNotes;
    }

    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }

    public LocalDateTime getSlaAcknowledgeBy() {
        return slaAcknowledgeBy;
    }

    public void setSlaAcknowledgeBy(LocalDateTime slaAcknowledgeBy) {
        this.slaAcknowledgeBy = slaAcknowledgeBy;
    }

    public LocalDateTime getSlaResolveBy() {
        return slaResolveBy;
    }

    public void setSlaResolveBy(LocalDateTime slaResolveBy) {
        this.slaResolveBy = slaResolveBy;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(LocalDateTime escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
