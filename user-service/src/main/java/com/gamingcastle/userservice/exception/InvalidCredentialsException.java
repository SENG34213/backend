package com.gamingcastle.userservice.exception;


public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        this("Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
