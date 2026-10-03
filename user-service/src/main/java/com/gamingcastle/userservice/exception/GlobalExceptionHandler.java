package com.gamingcastle.userservice.exception;

import com.gamingcastle.userservice.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        log.warn("Validation failed at {} -> {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCodes.VALIDATION_ERROR, "Request validation failed",
                        HttpStatus.BAD_REQUEST, request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex,
                                                            HttpServletRequest request) {
        log.warn("User not found at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCodes.USER_NOT_FOUND, ex.getMessage(),
                        HttpStatus.NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex,
                                                                  HttpServletRequest request) {
        log.warn("Email already exists at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCodes.EMAIL_TAKEN, ex.getMessage(),
                        HttpStatus.CONFLICT, request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                  HttpServletRequest request) {
        log.warn("Invalid credentials at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ErrorCodes.INVALID_CREDENTIALS, ex.getMessage(),
                        HttpStatus.UNAUTHORIZED, request.getRequestURI()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex,
                                                             HttpServletRequest request) {
        log.warn("Account locked at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ErrorResponse.of(ErrorCodes.ACCOUNT_LOCKED, ex.getMessage(),
                        HttpStatus.LOCKED, request.getRequestURI()));
    }

    @ExceptionHandler(PhoneAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePhoneAlreadyExists(PhoneAlreadyExistsException ex,
                                                                  HttpServletRequest request) {
        log.warn("Phone number already exists at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCodes.PHONE_TAKEN, ex.getMessage(),
                        HttpStatus.CONFLICT, request.getRequestURI()));
    }

    @ExceptionHandler(InvalidResetCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetCode(InvalidResetCodeException ex,
                                                                HttpServletRequest request) {
        log.warn("Invalid password reset code at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCodes.INVALID_RESET_CODE, ex.getMessage(),
                        HttpStatus.BAD_REQUEST, request.getRequestURI()));
    }

    @ExceptionHandler(NotificationDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleNotificationDeliveryFailed(NotificationDeliveryException ex,
                                                                          HttpServletRequest request) {
        log.error("Notification delivery failed at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(ErrorCodes.NOTIFICATION_DELIVERY_FAILED,
                        "Could not send the verification code right now. Please try again shortly.",
                        HttpStatus.BAD_GATEWAY, request.getRequestURI()));
    }

    @ExceptionHandler(UserAlreadyDeactivatedException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyDeactivated(UserAlreadyDeactivatedException ex,
                                                                      HttpServletRequest request) {
        log.warn("Deactivation rejected at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCodes.USER_ALREADY_DEACTIVATED, ex.getMessage(),
                        HttpStatus.CONFLICT, request.getRequestURI()));
    }
}
