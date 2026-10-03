package com.gamingcastle.userservice.exception;

public class AccountDeactivatedException extends RuntimeException {
    public AccountDeactivatedException() {
        this("Your account is deactivated. Verify your email or phone number to reactivate it and log in");
    }
    public AccountDeactivatedException(String message) {
        super(message);
    }
}
