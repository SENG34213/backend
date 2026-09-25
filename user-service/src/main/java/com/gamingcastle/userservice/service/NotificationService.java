package com.gamingcastle.userservice.service;

/** FR-04/FR-05: dispatches the verification code used to confirm a password reset. */
public interface NotificationService {

    void sendPasswordResetEmail(String email, String code);

    void sendPasswordResetSms(String phoneNumber, String code);
}
