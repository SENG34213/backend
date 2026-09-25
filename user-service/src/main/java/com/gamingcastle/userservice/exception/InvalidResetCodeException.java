package com.gamingcastle.userservice.exception;

/** FR-04/FR-05: the supplied reset code is wrong, already used, or expired. */
public class InvalidResetCodeException extends RuntimeException {

    public InvalidResetCodeException() {
        this("Invalid or expired verification code");
    }

    public InvalidResetCodeException(String message) {
        super(message);
    }
}
