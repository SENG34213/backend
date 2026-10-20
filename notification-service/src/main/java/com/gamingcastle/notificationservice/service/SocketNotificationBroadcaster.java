package com.gamingcastle.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class SocketNotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SocketNotificationBroadcaster.class);

    private final RestTemplate restTemplate;

    @Value("${socket.notification.url:http://localhost:5001/api/notify}")
    private String socketNotifyUrl;

    public SocketNotificationBroadcaster(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(1200))
                .setReadTimeout(Duration.ofMillis(1200))
                .build();
    }

    public void broadcast(String type, String category, String title, String message, String referenceId, String link, String targetUserId, java.util.List<String> targetUserIds) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> payload = new HashMap<>();
                payload.put("type", type);
                payload.put("category", category);
                payload.put("title", title);
                payload.put("message", message != null ? message : "");
                payload.put("referenceId", referenceId != null ? referenceId : "");
                payload.put("link", link != null ? link : "/admin");
                if (targetUserId != null && !targetUserId.isBlank()) {
                    payload.put("targetUserId", targetUserId);
                }
                if (targetUserIds != null && !targetUserIds.isEmpty()) {
                    payload.put("targetUserIds", targetUserIds);
                }

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
                restTemplate.postForEntity(socketNotifyUrl, request, Map.class);
                log.info("[Socket Notify] Successfully pushed event '{}' to {}", type, socketNotifyUrl);
            } catch (Exception ex) {
                log.debug("[Socket Notify] Socket server notification skipped or offline: {}", ex.getMessage());
            }
        });
    }

    public void broadcast(String type, String category, String title, String message, String referenceId, String link) {
        broadcast(type, category, title, message, referenceId, link, null, null);
    }
}
