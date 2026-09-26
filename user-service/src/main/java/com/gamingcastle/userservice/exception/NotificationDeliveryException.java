package com.gamingcastle.userservice.exception;

/** FR-04/FR-05: the reset code could not be delivered by the email/SMS provider. */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
