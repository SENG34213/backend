package com.gamingcastle.userservice.exception;


public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        this("Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}