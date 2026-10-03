package com.gamingcastle.userservice.exception;

import java.util.UUID;

public class UserAlreadyActiveException extends RuntimeException {
    public UserAlreadyActiveException(UUID userId) {
        this("User account is already active: " + userId);
    }
    public UserAlreadyActiveException(String message) {
        super(message);
    }
}