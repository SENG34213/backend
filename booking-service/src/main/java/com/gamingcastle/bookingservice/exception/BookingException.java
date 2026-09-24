package com.gamingcastle.bookingservice.exception;

import org.springframework.http.HttpStatus;

/** Same shape as user-service's AuthException, for consistency across services. */
public class BookingException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public BookingException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}