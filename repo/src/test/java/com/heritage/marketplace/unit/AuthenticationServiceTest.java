package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.heritage.marketplace.auth.*;
import com.heritage.marketplace.auth.dto.AuthTokenResponse;
import com.heritage.marketplace.auth.dto.LoginRequest;
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
import com.heritage.marketplace.user.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private TierService tierService;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EncryptionUtil encryptionUtil;
    @Spy private PasswordPolicyValidator passwordPolicyValidator = new PasswordPolicyValidator();
    @Spy private InputSanitizer inputSanitizer = new InputSanitizer();

    @InjectMocks private AuthenticationService authenticationService;

    private TierConfig bronzeTier;

    @BeforeEach
    void setUp() {
        bronzeTier = new TierConfig();
        bronzeTier.setId(UUID.randomUUID());
        bronzeTier.setName("Bronze");
        bronzeTier.setRank(1);
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("should register new user with Bronze tier")
        void registerNewUserSuccessfully() {
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.existsByEmail("encrypted-email")).thenReturn(false);
            when(tierService.resolveBronzeTier()).thenReturn(bronzeTier);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

            RegisterRequest request = new RegisterRequest(
                "test@example.com", "P@ssw0rd!", "Test User", "+1234567890"
            );

            RegisterResponse response = authenticationService.register(request);

            assertNotNull(response);
            assertEquals("Test User", response.displayName());
            assertEquals(UserRole.MEMBER, response.role());
            assertEquals(UserStatus.ACTIVE, response.status());
            assertEquals("Bronze", response.tierName());
        }

        @Test
        @DisplayName("should reject duplicate email")
        void rejectDuplicateEmail() {
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.existsByEmail("encrypted-email")).thenReturn(true);

            RegisterRequest request = new RegisterRequest(
                "existing@example.com", "P@ssw0rd!", "Test", null
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.register(request));

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("EMAIL_ALREADY_EXISTS", ex.getCode());
        }

        @Test
        @DisplayName("should reject weak password")
        void rejectWeakPassword() {
            RegisterRequest request = new RegisterRequest(
                "test@example.com", "weak", "Test", null
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.register(request));

            assertEquals("PASSWORD_POLICY_VIOLATION", ex.getCode());
        }

        @Test
        @DisplayName("should reject password without special character")
        void rejectPasswordWithoutSpecialChar() {
            RegisterRequest request = new RegisterRequest(
                "test@example.com", "Password1", "Test", null
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.register(request));

            assertEquals("PASSWORD_POLICY_VIOLATION", ex.getCode());
        }
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("should return token on valid credentials")
        void returnTokenOnValidCredentials() {
            User user = buildUser();
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.findByEmail("encrypted-email")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("P@ssw0rd!", user.getPasswordHash())).thenReturn(true);
            when(jwtTokenProvider.generateToken(user.getId(), user.getRole())).thenReturn("jwt-token");
            when(jwtTokenProvider.extractExpiration("jwt-token")).thenReturn(Instant.now().plusSeconds(3600));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            LoginRequest request = new LoginRequest("test@example.com", "P@ssw0rd!");
            AuthTokenResponse response = authenticationService.login(request);

            assertNotNull(response);
            assertEquals("jwt-token", response.accessToken());
            assertEquals("Bearer", response.tokenType());
        }

        @Test
        @DisplayName("should throw on invalid credentials")
        void throwOnInvalidCredentials() {
            User user = buildUser();
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.findByEmail("encrypted-email")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
            when(loginAttemptService.recordFailure(anyString())).thenReturn(1);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            LoginRequest request = new LoginRequest("test@example.com", "WrongPass1!");

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.login(request));

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
            assertEquals("INVALID_CREDENTIALS", ex.getCode());
        }

        @Test
        @DisplayName("should lock account after 10 failed attempts")
        void lockAccountAfterMaxFailedAttempts() {
            User user = buildUser();
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.findByEmail("encrypted-email")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
            when(loginAttemptService.recordFailure(anyString())).thenReturn(10);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            LoginRequest request = new LoginRequest("test@example.com", "WrongPass1!");

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.login(request));

            assertEquals(HttpStatus.LOCKED, ex.getStatus());
            assertEquals("ACCOUNT_LOCKED", ex.getCode());
        }

        @Test
        @DisplayName("should reject login for locked account")
        void rejectLoginForLockedAccount() {
            User user = buildUser();
            user.setLockoutUntil(LocalDateTime.now().plusMinutes(10));
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.findByEmail("encrypted-email")).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest("test@example.com", "P@ssw0rd!");

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.login(request));

            assertEquals(HttpStatus.LOCKED, ex.getStatus());
        }

        @Test
        @DisplayName("should throw for nonexistent email")
        void throwForNonexistentEmail() {
            when(encryptionUtil.encryptDeterministic(anyString())).thenReturn("encrypted-email");
            when(userRepository.findByEmail("encrypted-email")).thenReturn(Optional.empty());

            LoginRequest request = new LoginRequest("nobody@example.com", "P@ssw0rd!");

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.login(request));

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("should throw for missing token")
        void throwForMissingToken() {
            when(jwtTokenProvider.resolveBearerToken(null)).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                () -> authenticationService.logout(null));

            assertEquals("INVALID_TOKEN", ex.getCode());
        }

        @Test
        @DisplayName("should blacklist valid token on logout")
        void blacklistValidTokenOnLogout() {
            when(jwtTokenProvider.resolveBearerToken("Bearer jwt-token")).thenReturn("jwt-token");
            when(jwtTokenProvider.validateToken("jwt-token")).thenReturn(true);
            when(jwtTokenProvider.extractExpiration("jwt-token")).thenReturn(Instant.now().plusSeconds(3600));

            authenticationService.logout("Bearer jwt-token");

            verify(tokenBlacklistService).blacklist(eq("jwt-token"), any(Instant.class));
        }
    }

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("encrypted-email");
        user.setPasswordHash("hashed-password");
        user.setDisplayName("Test User");
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
