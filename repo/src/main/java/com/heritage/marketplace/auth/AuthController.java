package com.heritage.marketplace.auth;

import com.heritage.marketplace.auth.dto.AuthTokenResponse;
import com.heritage.marketplace.auth.dto.LoginRequest;
import com.heritage.marketplace.auth.dto.MeResponse;
import com.heritage.marketplace.auth.dto.RegisterRequest;
import com.heritage.marketplace.auth.dto.RegisterResponse;
import com.heritage.marketplace.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResponse response = authenticationService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authenticationService.logout(authorizationHeader);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Logged out successfully")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
        @AuthenticationPrincipal JwtUserPrincipal principal,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        AuthTokenResponse response = authenticationService.refresh(principal, authorizationHeader);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal JwtUserPrincipal principal) {
        MeResponse response = authenticationService.me(principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
