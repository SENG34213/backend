package com.gamingcastle.notificationservice.service;

import com.gamingcastle.notificationservice.entity.Notification;
import com.gamingcastle.notificationservice.enums.NotificationStatus;
import com.gamingcastle.notificationservice.enums.NotificationType;
import com.gamingcastle.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationRecordService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRecordService.class);

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
        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipping notification audit record for {} because recipient email is blank", type);
            return;
        }

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
