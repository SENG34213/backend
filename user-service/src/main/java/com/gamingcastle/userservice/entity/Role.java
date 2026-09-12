package com.gamingcastle.userservice.entity;

/**
 * FR-02: system roles. Kept as a plain enum (not a DB-driven table) since the
 * SRS only requires two fixed roles for this scope — Customer and Admin.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
