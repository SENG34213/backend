package com.gamingcastle.notificationservice.dto;

public record EmailContent(
    String subject,
    String htmlBody
) {
}
