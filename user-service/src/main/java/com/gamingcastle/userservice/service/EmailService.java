package com.gamingcastle.userservice.service;

/** Low-level email transport, used by NotificationService to deliver FR-04 codes. */
public interface EmailService {
    void send(String to, String subject, String body);
    void sendHtml(String to, String subject, String htmlBody);
}
