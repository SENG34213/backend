package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.exception.NotificationDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

/**
 * FR-04: sends the password-reset code by email over SMTP via Spring Mail.
 * Configure spring.mail.* (see application.yml) with a real provider —
 * Gmail + an App Password is the fastest way to get this working locally;
 * Brevo or SendGrid's SMTP relay are better fits for anything closer to
 * production. Nothing else in the codebase needs to change either way.
 */
@Service
public class SmtpEmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailServiceImpl(JavaMailSender mailSender,
                                @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Password reset email dispatched to {}", to);
        } catch (MailException ex) {
            log.error("Failed to send password reset email to {}: {}", to, ex.getMessage());
            throw new NotificationDeliveryException("Unable to send verification email", ex);
        }
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(fromAddress);

            helper.setText(htmlBody, true);

            mailSender.send(message);

            log.info("HTML email sent successfully to {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new NotificationDeliveryException("Unable to send email", e);
        }
    }
}
