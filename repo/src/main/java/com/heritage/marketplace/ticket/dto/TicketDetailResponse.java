package com.heritage.marketplace.ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TicketDetailResponse(
    TicketResponse ticket,
    @JsonProperty("follow_ups")
    List<TicketFollowUpResponse> followUps
) {
}
