package com.gamingcastle.loyaltyservice.exception;

import com.gamingcastle.loyaltyservice.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/loyalty/test");
    }

    @Test
    @DisplayName("Given MethodArgumentNotValidException, when handled, then returns 400 BAD_REQUEST with field errors")
    void handleValidation_shouldReturn400WithFieldErrors() {
        // Arrange
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testObject");
        bindingResult.addError(new FieldError("testObject", "points", "must be greater than 0"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(response.getBody().fieldErrors()).containsEntry("points", "must be greater than 0");
    }

    @Test
    @DisplayName("Given InvalidLoyaltyRequestException, when handled, then returns 422 UNPROCESSABLE_ENTITY")
    void handleInvalidRequest_shouldReturn422() {
        // Arrange
        InvalidLoyaltyRequestException ex = new InvalidLoyaltyRequestException("Invalid parameter value");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleInvalidRequest(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.INVALID_LOYALTY_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Invalid parameter value");
    }

    @Test
    @DisplayName("Given InsufficientPointsException, when handled, then returns 422 UNPROCESSABLE_ENTITY")
    void handleInsufficientPoints_shouldReturn422() {
        // Arrange
        InsufficientPointsException ex = new InsufficientPointsException("Not enough points");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleInsufficientPoints(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.INSUFFICIENT_POINTS);
        assertThat(response.getBody().message()).isEqualTo("Not enough points");
    }

    @Test
    @DisplayName("Given RedemptionRejectedException, when handled, then returns 422 with specific error code")
    void handleRedemptionRejected_shouldReturn422WithCustomCode() {
        // Arrange
        RedemptionRejectedException ex = new BelowMinimumRedemptionException(100L);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleRedemptionRejected(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.BELOW_MINIMUM_REDEMPTION);
    }

    @Test
    @DisplayName("Given MissingRequestHeaderException, when handled, then returns 400 BAD_REQUEST")
    void handleMissingHeader_shouldReturn400() {
        // Arrange
        MissingRequestHeaderException ex = new MissingRequestHeaderException("X-User-Id", null);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.MISSING_HEADER);
        assertThat(response.getBody().message()).contains("X-User-Id");
    }

    @Test
    @DisplayName("Given LoyaltyAccountNotFoundException, when handled, then returns 404 NOT_FOUND")
    void handleAccountNotFound_shouldReturn404() {
        // Arrange
        LoyaltyAccountNotFoundException ex = new LoyaltyAccountNotFoundException(UUID.randomUUID());

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleAccountNotFound(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.LOYALTY_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("Given LoyaltyRulesNotFoundException, when handled, then returns 404 NOT_FOUND")
    void handleRulesNotFound_shouldReturn404() {
        // Arrange
        LoyaltyRulesNotFoundException ex = new LoyaltyRulesNotFoundException();

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleRulesNotFound(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.LOYALTY_RULES_NOT_FOUND);
    }

    @Test
    @DisplayName("Given ForbiddenOperationException, when handled, then returns 403 FORBIDDEN")
    void handleForbidden_shouldReturn403() {
        // Arrange
        ForbiddenOperationException ex = new ForbiddenOperationException("Access not allowed");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Access not allowed");
    }

    @Test
    @DisplayName("Given AccessDeniedException, when handled, then returns 403 FORBIDDEN")
    void handleAccessDenied_shouldReturn403() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo(ErrorCodes.FORBIDDEN_MESSAGE);
    }

    @Test
    @DisplayName("Given HttpMessageNotReadableException, when handled, then returns 400 BAD_REQUEST")
    void handleMalformedJson_shouldReturn400() {
        // Arrange
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.MALFORMED_REQUEST);
    }

    @Test
    @DisplayName("Given MethodArgumentTypeMismatchException, when handled, then returns 400 BAD_REQUEST")
    void handleTypeMismatch_shouldReturn400() {
        // Arrange
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "invalid-uuid", UUID.class, "userId", null, new IllegalArgumentException());

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.TYPE_MISMATCH);
    }

    @Test
    @DisplayName("Given HttpRequestMethodNotSupportedException, when handled, then returns 405 METHOD_NOT_ALLOWED")
    void handleMethodNotAllowed_shouldReturn405() {
        // Arrange
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Given DataIntegrityViolationException, when handled, then returns 409 CONFLICT")
    void handleDataIntegrity_shouldReturn409() {
        // Arrange
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Unique constraint violation", new RuntimeException("duplicate key"));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.DATA_INTEGRITY_VIOLATION);
    }

    @Test
    @DisplayName("Given unexpected Exception, when handled, then returns 500 INTERNAL_SERVER_ERROR")
    void handleUnexpected_shouldReturn500() {
        // Arrange
        Exception ex = new NullPointerException("Null pointer occurred");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo(ErrorCodes.INTERNAL_ERROR_MESSAGE);
    }
}
