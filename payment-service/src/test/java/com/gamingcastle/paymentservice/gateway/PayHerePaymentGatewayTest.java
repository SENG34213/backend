package com.gamingcastle.paymentservice.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PayHerePaymentGatewayTest {

    private PayHerePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new PayHerePaymentGateway();
        ReflectionTestUtils.setField(gateway, "merchantId", "1211111");
        ReflectionTestUtils.setField(gateway, "merchantSecret", "secret123");
        ReflectionTestUtils.setField(gateway, "currency", "LKR");
        ReflectionTestUtils.setField(gateway, "isSandbox", true);
    }

    @Test
    @DisplayName("generatePayHereHash produces non-null uppercase MD5 hash")
    void shouldGenerateValidHash() {
        String hash = gateway.generatePayHereHash("ORD-100", "500.00", "LKR");
        assertThat(hash).isNotNull().isNotBlank();
        assertThat(hash).matches("^[A-F0-9]{32}$");
    }

    @Test
    @DisplayName("verifyNotification accepts matching MD5 signature")
    void shouldVerifyValidNotificationSignature() {
        // Compute expected signature per PayHere formula
        String orderId = "ORD-200";
        String amount = "1000.00";
        String currency = "LKR";
        String statusCode = "2";

        // hash = strtoupper(md5(merchant_id + order_id + amount + currency + status_code + strtoupper(md5(secret))))
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] secretBytes = md.digest("secret123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : secretBytes) sb.append(String.format("%02x", b));
            String hashedSecret = sb.toString().toUpperCase();

            String raw = "1211111" + orderId + amount + currency + statusCode + hashedSecret;
            byte[] rawBytes = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb2 = new StringBuilder();
            for (byte b : rawBytes) sb2.append(String.format("%02x", b));
            String expectedSig = sb2.toString().toUpperCase();

            boolean result = gateway.verifyNotification("1211111", orderId, amount, currency, statusCode, expectedSig);
            assertThat(result).isTrue();

            boolean badResult = gateway.verifyNotification("1211111", orderId, amount, currency, statusCode, "INVALIDSIG");
            assertThat(badResult).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("processPayment returns true for positive amount")
    void shouldProcessPaymentSuccessfully() {
        assertThat(gateway.processPayment(BigDecimal.valueOf(500))).isTrue();
        assertThat(gateway.processPayment(BigDecimal.ZERO)).isFalse();
    }
}
