package com.gamingcastle.tournamentservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("handleRegistrationClosed should return 409 CONFLICT")
    void handleRegistrationClosed_shouldReturnConflict() {
        RegistrationClosedException ex = new RegistrationClosedException("Registration deadline passed");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleRegistrationClosed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("REGISTRATION_CLOSED");
        assertThat(response.getBody().get("message")).isEqualTo("Registration deadline passed");
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("handleNotFound should return 404 NOT_FOUND")
    void handleNotFound_shouldReturnNotFound() {
        TournamentNotFoundException ex = new TournamentNotFoundException("Tournament not found: 123");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().get("message")).isEqualTo("Tournament not found: 123");
    }

    @Test
    @DisplayName("handleDuplicate should return 409 CONFLICT")
    void handleDuplicate_shouldReturnConflict() {
        DuplicateRegistrationException ex = new DuplicateRegistrationException("Already registered");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("DUPLICATE_REGISTRATION");
    }

    @Test
    @DisplayName("handlePaymentFailed should return 402 PAYMENT_REQUIRED")
    void handlePaymentFailed_shouldReturnPaymentRequired() {
        PaymentFailedException ex = new PaymentFailedException("Payment declined by bank");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handlePaymentFailed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("handleIllegalState should return 409 CONFLICT")
    void handleIllegalState_shouldReturnConflict() {
        IllegalStateException ex = new IllegalStateException("Bracket already exists");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("handleIllegalArgument should return 400 BAD_REQUEST")
    void handleIllegalArgument_shouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid match winner");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid should return 400 BAD_REQUEST with field error message")
    void handleMethodArgumentNotValid_shouldReturnBadRequest() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "Tournament name cannot be blank"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().get("message")).isEqualTo("Tournament name cannot be blank");
    }

    @Test
    @DisplayName("handleAccessDenied should return 403 FORBIDDEN when user is authenticated")
    void handleAccessDenied_shouldReturnForbidden_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().get("message")).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("handleAccessDenied should return 401 UNAUTHORIZED when anonymous")
    void handleAccessDenied_shouldReturnUnauthorized_whenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        );

        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().get("message")).isEqualTo("Authentication required");
    }
}
