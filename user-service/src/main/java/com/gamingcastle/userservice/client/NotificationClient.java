package com.gamingcastle.userservice.client;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestTemplate restTemplate;
    private final String BASE_URL = "http://localhost:8086/api/notifications";

    public NotificationClient() {
        this.restTemplate = new RestTemplate();
    }

    public void sendWelcomeEmail(String email, String fullName) {
        try {
            restTemplate.postForEntity(BASE_URL + "/welcome", new WelcomeEmailRequest(email, fullName), Void.class);
        } catch (Exception e) {
            log.error("Failed to send welcome email via notification-service", e);
        }
    }

    public void sendPasswordResetEmail(String email, String code) {
        try {
            restTemplate.postForEntity(BASE_URL + "/password-reset", new PasswordResetEmailRequest(email, code), Void.class);
        } catch (Exception e) {
            log.error("Failed to send password reset email via notification-service", e);
        }
    }

    public void sendPasswordResetSms(String phoneNumber, String code) {
        try {
            restTemplate.postForEntity(BASE_URL + "/password-reset-sms", new PasswordResetSmsRequest(phoneNumber, code), Void.class);
        } catch (Exception e) {
            log.error("Failed to send password reset sms via notification-service", e);
        }
    }

    public record WelcomeEmailRequest(String email, String fullName) {}
    public record PasswordResetEmailRequest(String email, String code) {}
    public record PasswordResetSmsRequest(String phoneNumber, String code) {}
}
