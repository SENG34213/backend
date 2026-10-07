package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.entity.Notification;
import com.gamingcastle.notificationservice.enums.NotificationStatus;
import com.gamingcastle.notificationservice.enums.NotificationType;
import com.gamingcastle.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationRecordService {

    private final NotificationRepository notificationRepository;

    public NotificationRecordService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void recordSuccess(NotificationType type, String recipient) {
        save(type, recipient, NotificationStatus.SENT, null);
    }

    public void recordFailure(NotificationType type, String recipient, String error) {
        save(type, recipient, NotificationStatus.FAILED, error);
    }

    private void save(NotificationType type, String recipient, NotificationStatus status, String error) {
        Instant now = Instant.now();

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .notificationType(type.name())
                .recipientEmail(recipient)
                .status(status.name())
                .errorMessage(error)
                .createdAt(now)
                .sentAt(status == NotificationStatus.SENT ? now : null)
                .build();

        notificationRepository.save(notification);
    }
}
