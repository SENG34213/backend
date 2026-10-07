package com.gamingcastle.paymentservice.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public boolean processPayment(BigDecimal amount) {

        /*
         * Test-only failure mechanism.
         *
         * Any amount ending exactly in .13 is rejected.
         * This is NOT a real business rule.
         */
        return amount.remainder(BigDecimal.ONE)
                .compareTo(new BigDecimal("0.13")) != 0;
    }
}