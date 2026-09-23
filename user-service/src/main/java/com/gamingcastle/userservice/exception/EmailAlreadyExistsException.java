package com.gamingcastle.userservice.exception;

/** FR-01: a registration was attempted with an email that is already taken. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        this("An account with this email already exists");
    }

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}