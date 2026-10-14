package com.gamingcastle.userservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberUtilTest {

    private static final String DEFAULT_COUNTRY_CODE = "+94";

    @Test
    @DisplayName("toE164: should keep number unchanged when already in international format starting with '+'")
    void toE164_shouldReturnUnchanged_whenAlreadyInternational() {
        String result = PhoneNumberUtil.toE164("+94771234567", DEFAULT_COUNTRY_CODE);
        assertThat(result).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("toE164: should replace leading zero with default country code")
    void toE164_shouldReplaceLeadingZero() {
        String result = PhoneNumberUtil.toE164("0771234567", DEFAULT_COUNTRY_CODE);
        assertThat(result).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("toE164: should prepend country code when number starts without zero or plus")
    void toE164_shouldPrependCountryCode() {
        String result = PhoneNumberUtil.toE164("771234567", DEFAULT_COUNTRY_CODE);
        assertThat(result).isEqualTo("+94771234567");
    }

    @ParameterizedTest
    @CsvSource({
            "' 077 - 123 4567 ', '+94771234567'",
            "'+94 77 - 123 - 4567', '+94771234567'",
            "'  77 123 4567  ', '+94771234567'"
    })
    @DisplayName("toE164: should remove spaces, hyphens and whitespace")
    void toE164_shouldStripWhitespaceAndHyphens(String input, String expected) {
        String result = PhoneNumberUtil.toE164(input, DEFAULT_COUNTRY_CODE);
        assertThat(result).isEqualTo(expected);
    }
}
