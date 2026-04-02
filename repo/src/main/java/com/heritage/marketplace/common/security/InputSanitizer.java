package com.heritage.marketplace.common.security;

import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    public String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        String withoutScripts = trimmed
            .replaceAll("(?i)<script.*?>.*?</script>", "")
            .replaceAll("(?i)javascript:", "")
            .replaceAll("[\\u0000-\\u001F]", "");

        return withoutScripts;
    }
}
