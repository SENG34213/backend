package com.gamingcastle.userservice.exception;

import java.util.UUID;

/** Deactivation was requested for an account that is already disabled (soft-deleted). */
public class UserAlreadyDeactivatedException extends RuntimeException {

    public UserAlreadyDeactivatedException(UUID userId) {
        this("User account is already deactivated: " + userId);
    }

    public UserAlreadyDeactivatedException(String message) {
        super(message);
    }
}
