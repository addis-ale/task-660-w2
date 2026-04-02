package com.heritage.marketplace.ticket;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketFollowUpRepository extends JpaRepository<TicketFollowUp, UUID> {

    List<TicketFollowUp> findByTicket_IdOrderByCreatedAtAsc(UUID ticketId);
}
