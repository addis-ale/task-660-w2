package com.heritage.marketplace.ticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    long countByStatusNotIn(List<TicketStatus> statuses);

    long countByStatus(TicketStatus status);

    long count();

    long countByEscalatedAtIsNotNull();

    long countByStatusNotInAndSeverity(List<TicketStatus> statuses, TicketSeverity severity);

    @Query("""
        SELECT AVG(EXTRACT(EPOCH FROM (t.resolvedAt - t.createdAt)) / 3600.0)
        FROM Ticket t
        WHERE t.resolvedAt IS NOT NULL
        """)
    Double averageResolutionHours();

    @Query(value = """
        SELECT *
        FROM tickets
        WHERE status NOT IN ('RESOLVED', 'CLOSED', 'ESCALATED')
          AND ((acknowledged_at IS NULL AND sla_acknowledge_by < :now)
            OR (resolved_at IS NULL AND sla_resolve_by < :now))
        """, nativeQuery = true)
    List<Ticket> findTicketsForEscalation(LocalDateTime now);

    long countByReporter_IdAndCreatedAtAfter(UUID reporterId, LocalDateTime createdAfter);

    List<Ticket> findTop20ByReporter_IdOrderByCreatedAtDesc(UUID reporterId);

    List<Ticket> findByCreatedAtAfter(LocalDateTime createdAfter);
}
