package com.gamingcastle.userservice.exception;

/** FR-03: phone number must be unique so it can be used to look a user up at login. */
public class PhoneAlreadyExistsException extends RuntimeException {

    public PhoneAlreadyExistsException() {
        this("An account with this phone number already exists");
    }

    public PhoneAlreadyExistsException(String message) {
        super(message);
    }
}
