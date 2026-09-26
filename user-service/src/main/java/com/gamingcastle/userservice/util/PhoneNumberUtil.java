package com.gamingcastle.userservice.util;

/**
 * Normalizes a locally-formatted phone number (e.g. "0771234567") into E.164
 * (e.g. "+94771234567"), since Twilio requires E.164. Numbers already given
 * in E.164 form are passed through unchanged.
 */
public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
    }

    public static String toE164(String rawNumber, String defaultCountryCode) {
        String trimmed = rawNumber.trim().replaceAll("[\\s-]", "");

        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith("0")) {
            return defaultCountryCode + trimmed.substring(1);
        }
        return defaultCountryCode + trimmed;
    }
}
