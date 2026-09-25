package com.gamingcastle.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub implementation for this project's scope: logs the message instead of
 * calling a real provider. Swap this bean for a real integration (Spring Mail
 * / AWS SES for email, Twilio / a local gateway for SMS) by implementing
 * {@link NotificationService} — nothing in AuthService/PasswordResetService
 * needs to change.
 */
@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendPasswordResetEmail(String email, String code) {
        log.info("[EMAIL] Password reset code for {}: {} (expires in 15 minutes)", email, code);
    }

    @Override
    public void sendPasswordResetSms(String phoneNumber, String code) {
        log.info("[SMS] Password reset code for {}: {} (expires in 15 minutes)", phoneNumber, code);
    }
}
