package com.heritage.marketplace.auth;

import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            this.signingKey = Keys.hmacShaKeyFor(digest);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_INIT_FAILED", "Unable to initialize JWT key");
        }
    }

    public String generateToken(UUID userId, UserRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getTtlSeconds());

        return Jwts.builder()
            .subject(userId.toString())
            .claim(ROLE_CLAIM, role.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UserRole extractRole(String token) {
        String value = parseClaims(token).get(ROLE_CLAIM, String.class);
        return UserRole.valueOf(value);
    }

    public Instant extractExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        return authorizationHeader.substring(7).trim();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
