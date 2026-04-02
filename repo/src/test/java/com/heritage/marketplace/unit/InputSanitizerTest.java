package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.heritage.marketplace.common.security.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InputSanitizerTest {

    private InputSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new InputSanitizer();
    }

    @Test
    @DisplayName("should return null for null input")
    void returnNullForNull() {
        assertNull(sanitizer.sanitize(null));
    }

    @Test
    @DisplayName("should trim whitespace")
    void trimWhitespace() {
        assertEquals("hello", sanitizer.sanitize("  hello  "));
    }

    @Test
    @DisplayName("should remove script tags")
    void removeScriptTags() {
        String input = "Hello <script>alert('xss')</script> World";
        String result = sanitizer.sanitize(input);
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("</script>"));
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("World"));
    }

    @Test
    @DisplayName("should remove javascript: protocol")
    void removeJavascriptProtocol() {
        String input = "javascript:alert(1)";
        String result = sanitizer.sanitize(input);
        assertFalse(result.toLowerCase().contains("javascript:"));
    }

    @Test
    @DisplayName("should remove control characters")
    void removeControlCharacters() {
        String input = "Hello\u0000World\u001F";
        String result = sanitizer.sanitize(input);
        assertFalse(result.contains("\u0000"));
        assertFalse(result.contains("\u001F"));
        assertEquals("HelloWorld", result);
    }

    @Test
    @DisplayName("should handle case-insensitive script tags")
    void handleCaseInsensitiveScriptTags() {
        String input = "<SCRIPT>alert('xss')</SCRIPT>";
        String result = sanitizer.sanitize(input);
        assertFalse(result.toLowerCase().contains("<script>"));
    }

    @Test
    @DisplayName("should preserve normal text")
    void preserveNormalText() {
        String input = "This is a completely normal description with numbers 123 and symbols !@#$%";
        assertEquals(input, sanitizer.sanitize(input));
    }

    @Test
    @DisplayName("should handle empty string")
    void handleEmptyString() {
        assertEquals("", sanitizer.sanitize(""));
    }
}
