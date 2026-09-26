package com.gamingcastle.userservice.service;

import org.springframework.stereotype.Service;

/**
 * FR-04/FR-05: composes the password-reset message and delegates actual
 * delivery to EmailService (SMTP) / SmsService (Twilio). PasswordResetServiceImpl
 * only depends on the NotificationService interface, so the delivery
 * mechanism behind it can be swapped without touching the reset flow itself.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;

    public NotificationServiceImpl(EmailService emailService, SmsService smsService) {
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @Override
    public void sendPasswordResetEmail(String email, String code) {
        String subject = "Your Gaming Castle password reset code";
        String htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f4f7; font-family:Arial, Helvetica, sans-serif;">

                <div style="max-width:600px; margin:40px auto; padding:20px;">

                    <div style="background-color:#1a1a2e; padding:30px; text-align:center;
                                border-radius:12px 12px 0 0;">
                        <h1 style="margin:0; color:#ffffff; font-size:28px;">
                            🎮 Gaming Castle
                        </h1>
                        <p style="margin:8px 0 0; color:#c7c7d9; font-size:14px;">
                            Password Reset
                        </p>
                    </div>

                    <div style="background-color:#ffffff; padding:40px 30px;
                                border-radius:0 0 12px 12px;">

                        <h2 style="margin-top:0; color:#222222;">
                            Reset Your Password
                        </h2>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            We received a request to reset your Gaming Castle account password.
                        </p>

                        <p style="color:#555555; font-size:16px; line-height:1.6;">
                            Use the verification code below:
                        </p>

                        <div style="margin:30px 0; text-align:center;">
                            <div style="display:inline-block;
                                        padding:18px 35px;
                                        background-color:#f1f0ff;
                                        border:2px dashed #6c63ff;
                                        border-radius:10px;
                                        color:#6c63ff;
                                        font-size:32px;
                                        font-weight:bold;
                                        letter-spacing:8px;">
                                %s
                            </div>
                        </div>

                        <p style="color:#555555; font-size:14px; line-height:1.6;">
                            ⏱ This verification code will expire in
                            <strong>15 minutes</strong>.
                        </p>

                        <p style="color:#777777; font-size:14px; line-height:1.6;">
                            If you didn't request a password reset, you can safely ignore
                            this email. Your account remains secure.
                        </p>

                        <hr style="border:none; border-top:1px solid #eeeeee; margin:30px 0;">

                        <p style="margin:0; color:#999999; font-size:12px; text-align:center;">
                            © Gaming Castle. All rights reserved.
                        </p>

                    </div>

                </div>

            </body>
            </html>
            """.formatted(code);
        emailService.sendHtml(email, subject, htmlBody);
    }

    @Override
    public void sendPasswordResetSms(String phoneNumber, String code) {
        String message = "Your Gaming Castle password reset code is " + code + ". It expires in 15 minutes.";
        smsService.send(phoneNumber, message);
    }
}
