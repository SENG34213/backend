package com.gamingcastle.paymentservice.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private MockPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockPaymentGateway();
    }

    @Test
    @DisplayName("processPayment: returns true for regular amounts not ending in .13")
    void shouldReturnTrueForRegularAmounts() {
        assertThat(gateway.processPayment(new BigDecimal("10.00"))).isTrue();
        assertThat(gateway.processPayment(new BigDecimal("99.99"))).isTrue();
        assertThat(gateway.processPayment(new BigDecimal("50.50"))).isTrue();
    }

    @Test
    @DisplayName("processPayment: returns false when amount ends in .13")
    void shouldReturnFalseWhenAmountEndsInPointThirteen() {
        assertThat(gateway.processPayment(new BigDecimal("0.13"))).isFalse();
        assertThat(gateway.processPayment(new BigDecimal("100.13"))).isFalse();
    }
}
