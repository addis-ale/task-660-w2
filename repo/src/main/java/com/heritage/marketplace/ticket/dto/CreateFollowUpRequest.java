package com.heritage.marketplace.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFollowUpRequest(
    @NotBlank(message = "message is required")
    @Size(max = 2000, message = "message must be 2000 characters or less")
    String message
) {
}
