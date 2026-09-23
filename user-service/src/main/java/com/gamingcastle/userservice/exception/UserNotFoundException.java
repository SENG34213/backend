package com.gamingcastle.userservice.exception;

import java.util.UUID;

/** No user row matches the given id / email. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        this("No user found with id: " + userId);
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}
