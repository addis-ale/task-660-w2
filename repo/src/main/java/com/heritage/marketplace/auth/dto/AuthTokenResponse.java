package com.heritage.marketplace.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AuthTokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_at")
    Instant expiresAt
) {
}
