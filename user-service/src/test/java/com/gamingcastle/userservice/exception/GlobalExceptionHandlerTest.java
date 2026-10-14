package com.gamingcastle.userservice.exception;

import com.gamingcastle.userservice.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/test");
        request.setRequestURI("/api/test");
    }

    @Test
    @DisplayName("handleValidation: should return 400 Bad Request with VALIDATION_ERROR and field errors map")
    void handleValidation_shouldReturnBadRequest_withFieldErrors() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "must be a well-formed email address"));
        bindingResult.addError(new FieldError("target", "password", "Password must be at least 8 characters"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(response.getBody().path()).isEqualTo("/api/test");
        assertThat(response.getBody().fieldErrors()).containsEntry("email", "must be a well-formed email address");
        assertThat(response.getBody().fieldErrors()).containsEntry("password", "Password must be at least 8 characters");
    }

    @Test
    @DisplayName("handleUserNotFound: should return 404 Not Found with USER_NOT_FOUND")
    void handleUserNotFound_shouldReturnNotFound() {
        UUID userId = UUID.randomUUID();
        UserNotFoundException ex = new UserNotFoundException(userId);

        ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_NOT_FOUND);
        assertThat(response.getBody().message()).contains(userId.toString());
    }

    @Test
    @DisplayName("handleEmailAlreadyExists: should return 409 Conflict with EMAIL_TAKEN")
    void handleEmailAlreadyExists_shouldReturnConflict() {
        EmailAlreadyExistsException ex = new EmailAlreadyExistsException();

        ResponseEntity<ErrorResponse> response = handler.handleEmailAlreadyExists(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.EMAIL_TAKEN);
    }

    @Test
    @DisplayName("handleInvalidCredentials: should return 401 Unauthorized with INVALID_CREDENTIALS")
    void handleInvalidCredentials_shouldReturnUnauthorized() {
        InvalidCredentialsException ex = new InvalidCredentialsException();

        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("handleAccountLocked: should return 423 Locked with ACCOUNT_LOCKED and Retry-After header")
    void handleAccountLocked_shouldReturnLocked_withRetryAfterHeader() {
        Instant lockedUntil = Instant.now().plusSeconds(900);
        AccountLockedException ex = new AccountLockedException(lockedUntil);

        ResponseEntity<ErrorResponse> response = handler.handleAccountLocked(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("handlePhoneAlreadyExists: should return 409 Conflict with PHONE_TAKEN")
    void handlePhoneAlreadyExists_shouldReturnConflict() {
        PhoneAlreadyExistsException ex = new PhoneAlreadyExistsException();

        ResponseEntity<ErrorResponse> response = handler.handlePhoneAlreadyExists(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.PHONE_TAKEN);
    }

    @Test
    @DisplayName("handleInvalidResetCode: should return 400 Bad Request with INVALID_RESET_CODE")
    void handleInvalidResetCode_shouldReturnBadRequest() {
        InvalidResetCodeException ex = new InvalidResetCodeException("Invalid reset code");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidResetCode(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.INVALID_RESET_CODE);
        assertThat(response.getBody().message()).isEqualTo("Invalid reset code");
    }

    @Test
    @DisplayName("handleNotificationDeliveryFailed: should return 502 Bad Gateway with NOTIFICATION_DELIVERY_FAILED")
    void handleNotificationDeliveryFailed_shouldReturnBadGateway() {
        NotificationDeliveryException ex = new NotificationDeliveryException("Gateway timeout", new RuntimeException("connection refused"));

        ResponseEntity<ErrorResponse> response = handler.handleNotificationDeliveryFailed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.NOTIFICATION_DELIVERY_FAILED);
    }

    @Test
    @DisplayName("handleUserAlreadyDeactivated: should return 409 Conflict with USER_ALREADY_DEACTIVATED")
    void handleUserAlreadyDeactivated_shouldReturnConflict() {
        UUID userId = UUID.randomUUID();
        UserAlreadyDeactivatedException ex = new UserAlreadyDeactivatedException(userId);

        ResponseEntity<ErrorResponse> response = handler.handleUserAlreadyDeactivated(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_ALREADY_DEACTIVATED);
        assertThat(response.getBody().message()).contains(userId.toString());
    }

    @Test
    @DisplayName("handleAccountDeactivated: should return 403 Forbidden with ACCOUNT_DEACTIVATED")
    void handleAccountDeactivated_shouldReturnForbidden() {
        AccountDeactivatedException ex = new AccountDeactivatedException();

        ResponseEntity<ErrorResponse> response = handler.handleAccountDeactivated(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.ACCOUNT_DEACTIVATED);
    }

    @Test
    @DisplayName("handleAccountDeactivatedByAdmin: should return 403 Forbidden with ACCOUNT_DEACTIVATED_BY_ADMIN")
    void handleAccountDeactivatedByAdmin_shouldReturnForbidden() {
        AccountDeactivatedByAdminException ex = new AccountDeactivatedByAdminException();

        ResponseEntity<ErrorResponse> response = handler.handleAccountDeactivatedByAdmin(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.ACCOUNT_DEACTIVATED_BY_ADMIN);
    }

    @Test
    @DisplayName("handleUserAlreadyActive: should return 409 Conflict with USER_ALREADY_ACTIVE")
    void handleUserAlreadyActive_shouldReturnConflict() {
        UUID userId = UUID.randomUUID();
        UserAlreadyActiveException ex = new UserAlreadyActiveException(userId);

        ResponseEntity<ErrorResponse> response = handler.handleUserAlreadyActive(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.USER_ALREADY_ACTIVE);
        assertThat(response.getBody().message()).contains(userId.toString());
    }
}
