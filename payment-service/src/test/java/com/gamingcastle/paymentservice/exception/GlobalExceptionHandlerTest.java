package com.gamingcastle.paymentservice.exception;

import com.gamingcastle.paymentservice.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/payments");
    }

    @Test
    @DisplayName("handleLoyaltyRejected: returns 422 Unprocessable Entity with error body")
    void shouldHandleLoyaltyRejected() {
        LoyaltyRejectedException ex = new LoyaltyRejectedException("Not enough points");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleLoyaltyRejected(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(422);
        assertThat(response.getBody().getMessage()).isEqualTo("Not enough points");
        assertThat(response.getBody().getPath()).isEqualTo("/api/payments");
    }

    @Test
    @DisplayName("handleLoyaltyUnavailable: returns 503 Service Unavailable with error body")
    void shouldHandleLoyaltyUnavailable() {
        LoyaltyUnavailableException ex = new LoyaltyUnavailableException("Service down");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleLoyaltyUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("Service down");
    }

    @Test
    @DisplayName("handleIllegalArgument: returns 400 Bad Request with error body")
    void shouldHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid amount");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid amount");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: returns 400 Bad Request with field error message")
    void shouldHandleMethodArgumentNotValid() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "Amount is required"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Amount is required");
    }

    @Test
    @DisplayName("handleAccessDenied: returns 403 Forbidden when user is authenticated")
    void shouldHandleAccessDenied_whenAuthenticated() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        java.util.UUID.randomUUID(), null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Denied");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("handleAccessDenied: returns 401 Unauthorized when anonymous")
    void shouldHandleAccessDenied_whenAnonymous() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Denied");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("Authentication required");
    }
}
