package com.gamingcastle.userservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationClientTest {

    private static final String BASE_URL = "http://localhost:8086";
    private static final String GATEWAY_SECRET = "test-gateway-secret-xyz";
    private static final String SERVICE_USER_ID = "00000000-0000-0000-0000-000000000001";

    private NotificationClient notificationClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        notificationClient = new NotificationClient();
        ReflectionTestUtils.setField(notificationClient, "notificationBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(notificationClient, "gatewaySecret", GATEWAY_SECRET);

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(notificationClient, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("sendWelcomeEmail: should dispatch POST with gateway headers and body")
    void sendWelcomeEmail_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/welcome"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(header("X-User-Id", SERVICE_USER_ID))
                .andExpect(header("X-User-Role", "SERVICE"))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test Player"))
                .andRespond(withSuccess());

        notificationClient.sendWelcomeEmail("player@example.com", "Test Player");
        mockServer.verify();
    }

    @Test
    @DisplayName("sendPasswordResetEmail: should dispatch POST with code payload")
    void sendPasswordResetEmail_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/password-reset"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.code").value("123456"))
                .andRespond(withSuccess());

        notificationClient.sendPasswordResetEmail("player@example.com", "123456");
        mockServer.verify();
    }

    @Test
    @DisplayName("sendPasswordResetSms: should dispatch POST with SMS payload")
    void sendPasswordResetSms_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/password-reset-sms"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(jsonPath("$.phoneNumber").value("0771234567"))
                .andExpect(jsonPath("$.code").value("123456"))
                .andRespond(withSuccess());

        notificationClient.sendPasswordResetSms("0771234567", "123456");
        mockServer.verify();
    }

    @Test
    @DisplayName("sendAccountReactivationEmail: should dispatch POST with reactivation payload")
    void sendAccountReactivationEmail_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/account-reactivation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.code").value("654321"))
                .andRespond(withSuccess());

        notificationClient.sendAccountReactivationEmail("player@example.com", "654321");
        mockServer.verify();
    }

    @Test
    @DisplayName("sendAccountReactivationSms: should dispatch POST with reactivation SMS payload")
    void sendAccountReactivationSms_shouldDispatchPost() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/account-reactivation-sms"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Secret", GATEWAY_SECRET))
                .andExpect(jsonPath("$.phoneNumber").value("0771234567"))
                .andExpect(jsonPath("$.code").value("654321"))
                .andRespond(withSuccess());

        notificationClient.sendAccountReactivationSms("0771234567", "654321");
        mockServer.verify();
    }

    @Test
    @DisplayName("send: should catch exceptions gracefully when server returns 500 error")
    void send_shouldCatchExceptionsGracefully_whenServerFails() {
        mockServer.expect(requestTo(BASE_URL + "/api/notifications/welcome"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> notificationClient.sendWelcomeEmail("player@example.com", "Test Player"))
                .doesNotThrowAnyException();
        mockServer.verify();
    }
}
