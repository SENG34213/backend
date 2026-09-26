package com.gamingcastle.userservice.service;

/** Low-level SMS transport, used by NotificationService to deliver FR-05 codes. */
public interface SmsService {
    void send(String toPhoneNumber, String message);
}
