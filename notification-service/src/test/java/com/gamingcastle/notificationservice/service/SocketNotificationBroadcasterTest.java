package com.gamingcastle.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocketNotificationBroadcasterTest {

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private SocketNotificationBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        broadcaster = new SocketNotificationBroadcaster(restTemplateBuilder);
        ReflectionTestUtils.setField(broadcaster, "socketNotifyUrl", "http://localhost:5001/api/notify");
    }

    @Test
    @DisplayName("broadcast: sends asynchronous POST request with notification payload")
    @SuppressWarnings("unchecked")
    void broadcast_shouldSendPostRequest_whenCalled() throws Exception {
        broadcaster.broadcast(
                "BOOKING_CREATED",
                "BOOKING",
                "New Booking Confirmed",
                "Station PC-01 booked",
                "bkg-123",
                "/admin/bookings"
        );

        // Allow async thread to run
        Thread.sleep(200);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, atLeastOnce()).postForEntity(
                eq("http://localhost:5001/api/notify"),
                captor.capture(),
                eq(Map.class)
        );

        Map<String, Object> body = captor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("type")).isEqualTo("BOOKING_CREATED");
        assertThat(body.get("category")).isEqualTo("BOOKING");
        assertThat(body.get("title")).isEqualTo("New Booking Confirmed");
        assertThat(body.get("message")).isEqualTo("Station PC-01 booked");
        assertThat(body.get("referenceId")).isEqualTo("bkg-123");
        assertThat(body.get("link")).isEqualTo("/admin/bookings");
    }

    @Test
    @DisplayName("broadcast: handles null optional fields by defaulting to fallback values")
    @SuppressWarnings("unchecked")
    void broadcast_shouldDefaultOptionalFields_whenNull() throws Exception {
        broadcaster.broadcast(
                "ALERT",
                "SYSTEM",
                "System Notice",
                null,
                null,
                null
        );

        Thread.sleep(200);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, atLeastOnce()).postForEntity(
                eq("http://localhost:5001/api/notify"),
                captor.capture(),
                eq(Map.class)
        );

        Map<String, Object> body = captor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("");
        assertThat(body.get("referenceId")).isEqualTo("");
        assertThat(body.get("link")).isEqualTo("/admin");
    }

    @Test
    @DisplayName("broadcast: catches and ignores RestClientException if socket server is offline")
    void broadcast_shouldNotThrow_whenSocketServerOffline() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Must not throw exception
        broadcaster.broadcast(
                "BOOKING_CREATED",
                "BOOKING",
                "New Booking",
                "Details",
                "bkg-1",
                "/link"
        );

        Thread.sleep(200);
        verify(restTemplate, atLeastOnce()).postForEntity(anyString(), any(), eq(Map.class));
    }
}
