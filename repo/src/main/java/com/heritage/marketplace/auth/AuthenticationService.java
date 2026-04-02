package com.heritage.marketplace.auth;

import com.heritage.marketplace.auth.dto.AuthTokenResponse;
import com.heritage.marketplace.auth.dto.LoginRequest;
import com.heritage.marketplace.auth.dto.MeResponse;
import com.heritage.marketplace.auth.dto.RegisterRequest;
import com.heritage.marketplace.auth.dto.RegisterResponse;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.InputSanitizer;
import com.heritage.marketplace.common.security.PasswordPolicyValidator;
import com.heritage.marketplace.common.util.EncryptionUtil;
import com.heritage.marketplace.tier.Membership;
import com.heritage.marketplace.tier.MembershipRepository;
import com.heritage.marketplace.tier.TierConfig;
import com.heritage.marketplace.tier.TierService;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import com.heritage.marketplace.user.UserService;
import com.heritage.marketplace.user.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final int MAX_LOGIN_ATTEMPTS_PER_HOUR = 10;

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final TierService tierService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginAttemptService loginAttemptService;
    private final EncryptionUtil encryptionUtil;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final InputSanitizer inputSanitizer;

    public AuthenticationService(
        UserRepository userRepository,
        MembershipRepository membershipRepository,
        TierService tierService,
        UserService userService,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        TokenBlacklistService tokenBlacklistService,
        LoginAttemptService loginAttemptService,
        EncryptionUtil encryptionUtil,
        PasswordPolicyValidator passwordPolicyValidator,
        InputSanitizer inputSanitizer
    ) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tierService = tierService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.loginAttemptService = loginAttemptService;
        this.encryptionUtil = encryptionUtil;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.inputSanitizer = inputSanitizer;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        passwordPolicyValidator.validateOrThrow(request.password());

        String encryptedEmail = encryptionUtil.encryptDeterministic(normalizedEmail);

        if (userRepository.existsByEmail(encryptedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
        }

        TierConfig bronzeTier = tierService.resolveBronzeTier();
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(encryptedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(encryptPhone(request.phone()));
        user.setDisplayName(inputSanitizer.sanitize(request.displayName()));
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);

        Membership membership = new Membership();
        membership.setId(UUID.randomUUID());
        membership.setUser(savedUser);
        membership.setTier(bronzeTier);
        membership.setTotalSpend(BigDecimal.ZERO);
        membership.setTierValidUntil(LocalDate.now().plusYears(1));
        membershipRepository.save(membership);

        return new RegisterResponse(
            savedUser.getId(),
            normalizedEmail,
            savedUser.getDisplayName(),
            null,
            savedUser.getRole(),
            savedUser.getStatus(),
            bronzeTier.getName(),
            savedUser.getCreatedAt()
        );
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String encryptedEmail = encryptionUtil.encryptDeterministic(normalizedEmail);

        User user = userRepository.findByEmail(encryptedEmail)
            .orElseThrow(() -> invalidCredentials(normalizedEmail));

        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now())) {
            long retryAfterSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), user.getLockoutUntil());
            throw new ApiException(
                HttpStatus.LOCKED,
                "ACCOUNT_LOCKED",
                "Account is temporarily locked due to failed login attempts",
                Map.of("retryAfterSeconds", Math.max(1, retryAfterSeconds))
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attemptCount = loginAttemptService.recordFailure(normalizedEmail);
            user.setFailedLoginAttempts(attemptCount);

            if (attemptCount >= MAX_LOGIN_ATTEMPTS_PER_HOUR) {
                LocalDateTime lockoutUntil = LocalDateTime.now().plusMinutes(15);
                user.setLockoutUntil(lockoutUntil);
                userRepository.save(user);

                throw new ApiException(
                    HttpStatus.LOCKED,
                    "ACCOUNT_LOCKED",
                    "Too many login attempts. Account locked for 15 minutes.",
                    Map.of("retryAfterSeconds", 900)
                );
            }

            userRepository.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }

        loginAttemptService.reset(normalizedEmail);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getRole());
        Instant expiresAt = jwtTokenProvider.extractExpiration(accessToken);
        return new AuthTokenResponse(accessToken, "Bearer", expiresAt);
    }

    public void logout(String authorizationHeader) {
        String token = jwtTokenProvider.resolveBearerToken(authorizationHeader);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Bearer token is missing or invalid");
        }

        tokenBlacklistService.blacklist(token, jwtTokenProvider.extractExpiration(token));
    }

    public AuthTokenResponse refresh(AuthenticatedUser principal, String authorizationHeader) {
        String currentToken = jwtTokenProvider.resolveBearerToken(authorizationHeader);
        if (currentToken != null && jwtTokenProvider.validateToken(currentToken)) {
            tokenBlacklistService.blacklist(currentToken, jwtTokenProvider.extractExpiration(currentToken));
        }

        String newToken = jwtTokenProvider.generateToken(principal.userId(), principal.role());
        return new AuthTokenResponse(newToken, "Bearer", jwtTokenProvider.extractExpiration(newToken));
    }

    public MeResponse me(AuthenticatedUser principal) {
        User user = userService.getRequiredById(principal.userId());
        return userService.toMeResponse(user, principal.role());
    }

    private ApiException invalidCredentials(String accountKey) {
        loginAttemptService.recordFailure(accountKey);
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String encryptPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return encryptionUtil.encryptDeterministic(inputSanitizer.sanitize(phone));
    }
}
