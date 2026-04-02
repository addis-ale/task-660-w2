package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.security.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
    }

    @Test
    @DisplayName("should accept valid password with all requirements")
    void acceptValidPassword() {
        assertDoesNotThrow(() -> validator.validateOrThrow("P@ssw0rd!"));
    }

    @Test
    @DisplayName("should accept long complex password")
    void acceptLongComplexPassword() {
        assertDoesNotThrow(() -> validator.validateOrThrow("MyV3ry$ecure&L0ngP@ssword"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "short",
        "alllowercase1!",
        "ALLUPPERCASE1!",
        "NoDigits!!",
        "NoSpecial1a",
        "Sh0rt!",
        "1234567890"
    })
    @DisplayName("should reject passwords violating policy")
    void rejectWeakPasswords(String password) {
        ApiException ex = assertThrows(ApiException.class,
            () -> validator.validateOrThrow(password));
        assertEquals("PASSWORD_POLICY_VIOLATION", ex.getCode());
    }

    @Test
    @DisplayName("should accept password with exactly 8 characters meeting all rules")
    void acceptMinLengthValidPassword() {
        assertDoesNotThrow(() -> validator.validateOrThrow("Ab1!defg"));
    }
}
