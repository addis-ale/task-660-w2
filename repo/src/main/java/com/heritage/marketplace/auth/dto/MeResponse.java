package com.heritage.marketplace.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heritage.marketplace.user.UserRole;
import com.heritage.marketplace.user.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    @JsonProperty("display_name")
    String displayName,
    String phone,
    UserRole role,
    UserStatus status,
    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
}
