package com.heritage.marketplace.common.security;

import com.heritage.marketplace.common.exception.ApiException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    private static final Pattern POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    public void validateOrThrow(String rawPassword) {
        if (rawPassword == null || !POLICY.matcher(rawPassword).matches()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_POLICY_VIOLATION",
                "Password must be at least 8 chars and include uppercase, lowercase, digit, and special character"
            );
        }
    }
}
