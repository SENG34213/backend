package com.gamingcastle.paymentservice.entity;

/** FR-14/FR-19-21: how the payment was made. */
public enum PaymentMethod {
    ONLINE,  // via the mock/sandbox gateway integration (Sprint 6)
    CASH     // recorded by an admin at the front desk
}
