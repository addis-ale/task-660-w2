package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.ticket.TicketService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaEscalationChecker {

    private final TicketService ticketService;

    public SlaEscalationChecker(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.sla-escalation.fixed-delay-ms:60000}")
    public void checkAndEscalate() {
        ticketService.autoEscalateBreachedTickets();
    }
}
