package com.gamingcastle.notificationservice.dto;

public record PasswordResetSmsRequest(String phoneNumber, String code) {}
