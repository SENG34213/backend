package com.gamingcastle.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailSenderService emailSenderService;

    private final String fromAddress = "noreply@gamingcastle.com";

    @BeforeEach
    void setUp() {
        emailSenderService = new EmailSenderService(mailSender, fromAddress);
    }

    @Test
    @DisplayName("sendHtmlEmail: creates MimeMessage and sends email successfully")
    void sendHtmlEmail_shouldSendEmail_whenValidInput() {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailSenderService.sendHtmlEmail("player@example.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendHtmlEmail: wraps MessagingException into RuntimeException")
    void sendHtmlEmail_shouldThrowRuntimeException_whenMessagingExceptionOccurs() {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        // An invalid email address format like "invalid email@@@" causes MimeMessageHelper/InternetAddress to throw MessagingException
        assertThatThrownBy(() -> emailSenderService.sendHtmlEmail("invalid email@@@", "Test", "<p>Hi</p>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email sending failed");
    }
}
