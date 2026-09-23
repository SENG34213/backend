package com.gamingcastle.userservice.exception;

/** FR-06: too many failed login attempts, the account is temporarily locked. */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        this("Account is temporarily locked due to repeated failed login attempts");
    }

    public AccountLockedException(String message) {
        super(message);
    }
}