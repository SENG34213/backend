package com.gamingcastle.userservice.client;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private static final String SERVICE_USER_ID = "00000000-0000-0000-0000-000000000001";
    private final RestTemplate restTemplate;

    @Value("${services.notification.base-url:http://localhost:8086}")
    private String notificationBaseUrl;

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    public NotificationClient() {
        this.restTemplate = new RestTemplate();
    }

    public void sendWelcomeEmail(String email, String fullName) {
        try {
            send("/welcome", new WelcomeEmailRequest(email, fullName));
        } catch (Exception e) {
            log.error("Failed to send welcome email via notification-service", e);
        }
    }

    public void sendPasswordResetEmail(String email, String code) {
        try {
            send("/password-reset", new PasswordResetEmailRequest(email, code));
        } catch (Exception e) {
            log.error("Failed to send password reset email via notification-service", e);
        }
    }

    public void sendPasswordResetSms(String phoneNumber, String code) {
        try {
            send("/password-reset-sms", new PasswordResetSmsRequest(phoneNumber, code));
        } catch (Exception e) {
            log.error("Failed to send password reset sms via notification-service", e);
        }
    }

    public void sendAccountReactivationEmail(String email, String code) {
        try {
            send("/account-reactivation", new AccountReactivationEmailRequest(email, code));
        } catch (Exception e) {
            log.error("Failed to send account reactivation email via notification-service", e);
        }
    }

    public void sendAccountReactivationSms(String phoneNumber, String code) {
        try {
            send("/account-reactivation-sms", new AccountReactivationSmsRequest(phoneNumber, code));
        } catch (Exception e) {
            log.error("Failed to send account reactivation sms via notification-service", e);
        }
    }

    private void send(String path, Object request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", gatewaySecret);
        headers.set("X-User-Id", SERVICE_USER_ID);
        headers.set("X-User-Role", "SERVICE");
        restTemplate.postForEntity(
                notificationBaseUrl + "/api/notifications" + path,
                new HttpEntity<>(request, headers),
                Void.class);
    }

    public record WelcomeEmailRequest(String email, String fullName) {}
    public record PasswordResetEmailRequest(String email, String code) {}
    public record PasswordResetSmsRequest(String phoneNumber, String code) {}
    public record AccountReactivationEmailRequest(String email, String code) {}
    public record AccountReactivationSmsRequest(String phoneNumber, String code) {}
}
