package com.heritage.marketplace.common.util;

import org.springframework.stereotype.Component;

@Component
public class PhoneMaskingUtil {

    public String mask(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }

        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "***";
        }

        String prefix = digits.length() >= 3 ? digits.substring(0, 3) : "***";
        String lastFour = digits.substring(digits.length() - 4);
        return prefix + "-***-" + lastFour;
    }
}
