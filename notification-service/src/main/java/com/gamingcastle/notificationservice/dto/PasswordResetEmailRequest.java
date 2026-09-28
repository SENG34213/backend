package com.gamingcastle.notificationservice.dto;

public record PasswordResetEmailRequest(String email, String code) {}
