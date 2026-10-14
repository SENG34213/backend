package com.gamingcastle.bookingservice.exception;

import com.gamingcastle.bookingservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("handleBookingException: returns status and error response from exception")
    void handleBookingException_shouldReturnStatusAndErrorResponse() {
        BookingException ex = new BookingException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Slot already taken");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBookingException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("SLOT_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("Slot already taken");
    }

    @Test
    @DisplayName("handleValidation: formats field error message")
    void handleValidation_shouldReturnBadRequestWithFormattedMessage() throws NoSuchMethodException {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "stationId", "must not be null"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("stationId: must not be null");
    }

    @Test
    @DisplayName("handleValidation: handles binding result with no field errors")
    void handleValidation_shouldFallbackWhenNoFieldErrors() throws NoSuchMethodException {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("handleAccessDenied: returns 401 when anonymous or unauthenticated")
    void handleAccessDenied_shouldReturn401_whenAnonymous() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        // Scenario 1: null authentication
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");

        // Scenario 2: anonymous token
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        response = exceptionHandler.handleAccessDenied(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("handleAccessDenied: returns 403 when authenticated user lacks permission")
    void handleAccessDenied_shouldReturn403_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("You do not have permission to access this resource");
    }

    @Test
    @DisplayName("handleUnexpected: returns 500 INTERNAL_SERVER_ERROR")
    void handleUnexpected_shouldReturn500() {
        Exception ex = new RuntimeException("Database timeout");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("handleMissingRequestHeader: returns 401 when X-User-Id or X-User-Role missing")
    void handleMissingRequestHeader_shouldReturn401_forIdentityHeaders() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);

        MissingRequestHeaderException exUserId = new MissingRequestHeaderException("X-User-Id", parameter);
        ResponseEntity<ErrorResponse> responseUserId = exceptionHandler.handleMissingRequestHeader(exUserId, request);
        assertThat(responseUserId.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseUserId.getBody().error()).isEqualTo("UNAUTHORIZED");
        assertThat(responseUserId.getBody().message()).contains("X-User-Id");

        MissingRequestHeaderException exUserRole = new MissingRequestHeaderException("X-User-Role", parameter);
        ResponseEntity<ErrorResponse> responseUserRole = exceptionHandler.handleMissingRequestHeader(exUserRole, request);
        assertThat(responseUserRole.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseUserRole.getBody().error()).isEqualTo("UNAUTHORIZED");
        assertThat(responseUserRole.getBody().message()).contains("X-User-Role");
    }

    @Test
    @DisplayName("handleMissingRequestHeader: returns 400 when other header is missing")
    void handleMissingRequestHeader_shouldReturn400_forOtherHeaders() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);

        MissingRequestHeaderException ex = new MissingRequestHeaderException("Custom-Header", parameter);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMissingRequestHeader(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).contains("Custom-Header");
    }

    @Test
    @DisplayName("handleTypeMismatch: returns custom message for UUID vs generic argument")
    void handleTypeMismatch_shouldHandleUuidAndGenericTypes() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);

        MethodArgumentTypeMismatchException uuidEx = new MethodArgumentTypeMismatchException(
                "invalid-uuid", UUID.class, "id", parameter, new IllegalArgumentException());
        ResponseEntity<String> uuidResponse = exceptionHandler.handleTypeMismatch(uuidEx);
        assertThat(uuidResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uuidResponse.getBody()).isEqualTo("Invalid UUID format");

        MethodArgumentTypeMismatchException intEx = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", parameter, new NumberFormatException());
        ResponseEntity<String> intResponse = exceptionHandler.handleTypeMismatch(intEx);
        assertThat(intResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(intResponse.getBody()).isEqualTo("Invalid argument");
    }

    private void dummyMethod(String param) {}
}
