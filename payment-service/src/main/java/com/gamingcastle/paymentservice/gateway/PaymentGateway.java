package com.gamingcastle.paymentservice.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {

    boolean processPayment(BigDecimal amount);

}