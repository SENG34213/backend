package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.entity.Notification;
import com.gamingcastle.notificationservice.enums.NotificationStatus;
import com.gamingcastle.notificationservice.enums.NotificationType;
import com.gamingcastle.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRecordServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationRecordService notificationRecordService;

    @BeforeEach
    void setUp() {
        notificationRecordService = new NotificationRecordService(notificationRepository);
    }

    @Test
    @DisplayName("recordSuccess: saves notification entity with status SENT and timestamps")
    void recordSuccess_shouldSaveNotification_withSentStatus() {
        String recipient = "gamer@example.com";
        NotificationType type = NotificationType.BOOKING_CONFIRMATION;

        notificationRecordService.recordSuccess(type, recipient);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNotificationType()).isEqualTo(type.name());
        assertThat(saved.getRecipientEmail()).isEqualTo(recipient);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT.name());
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("recordFailure: saves notification entity with status FAILED and error message")
    void recordFailure_shouldSaveNotification_withFailedStatusAndError() {
        String recipient = "gamer@example.com";
        NotificationType type = NotificationType.PASSWORD_RESET_EMAIL;
        String errorMessage = "SMTP server timeout";

        notificationRecordService.recordFailure(type, recipient, errorMessage);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNotificationType()).isEqualTo(type.name());
        assertThat(saved.getRecipientEmail()).isEqualTo(recipient);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED.name());
        assertThat(saved.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSentAt()).isNull();
    }

    @Test
    @DisplayName("save: skips saving when recipient is null or blank")
    void save_shouldSkip_whenRecipientNullOrBlank() {
        notificationRecordService.recordSuccess(NotificationType.WELCOME_EMAIL, null);
        notificationRecordService.recordSuccess(NotificationType.WELCOME_EMAIL, "   ");
        notificationRecordService.recordFailure(NotificationType.WELCOME_EMAIL, null, "error");
        notificationRecordService.recordFailure(NotificationType.WELCOME_EMAIL, "", "error");

        verify(notificationRepository, never()).save(any());
    }
}
