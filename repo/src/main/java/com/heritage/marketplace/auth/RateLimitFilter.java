package com.heritage.marketplace.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.marketplace.common.api.ApiError;
import com.heritage.marketplace.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT = 60;
    private static final long WINDOW_MS = 60_000L;

    private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        TokenBucket bucket = buckets.computeIfAbsent(principal.userId(), id -> new TokenBucket(LIMIT, WINDOW_MS));
        RateLimitResult result = bucket.consume();

        response.setHeader("X-RateLimit-Limit", String.valueOf(LIMIT));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));

        if (!result.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiError error = ApiError.of(
                "RATE_LIMIT_EXCEEDED",
                "Request rate limit exceeded",
                Map.of("limit", LIMIT, "windowSeconds", WINDOW_MS / 1000)
            );
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(error)));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static final class TokenBucket {

        private final int capacity;
        private final double refillPerMillisecond;

        private double tokens;
        private long lastRefillMs;

        private TokenBucket(int capacity, long windowMs) {
            this.capacity = capacity;
            this.refillPerMillisecond = (double) capacity / windowMs;
            this.tokens = capacity;
            this.lastRefillMs = System.currentTimeMillis();
        }

        private synchronized RateLimitResult consume() {
            long now = System.currentTimeMillis();
            refill(now);

            if (tokens >= 1) {
                tokens -= 1;
                int remaining = (int) Math.floor(tokens);
                long resetEpochSeconds = computeResetEpochSeconds(now);
                return new RateLimitResult(true, remaining, resetEpochSeconds, 0);
            }

            long retryAfterMs = (long) Math.ceil((1 - tokens) / refillPerMillisecond);
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterMs / 1000.0));
            long resetEpochSeconds = Instant.ofEpochMilli(now + retryAfterMs).getEpochSecond();
            return new RateLimitResult(false, 0, resetEpochSeconds, retryAfterSeconds);
        }

        private void refill(long nowMs) {
            long elapsed = nowMs - lastRefillMs;
            if (elapsed <= 0) {
                return;
            }

            tokens = Math.min(capacity, tokens + elapsed * refillPerMillisecond);
            lastRefillMs = nowMs;
        }

        private long computeResetEpochSeconds(long nowMs) {
            if (tokens >= capacity) {
                return Instant.ofEpochMilli(nowMs).getEpochSecond();
            }

            long untilFullMs = (long) Math.ceil((capacity - tokens) / refillPerMillisecond);
            return Instant.ofEpochMilli(nowMs + untilFullMs).getEpochSecond();
        }
    }

    private record RateLimitResult(boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    }
}
