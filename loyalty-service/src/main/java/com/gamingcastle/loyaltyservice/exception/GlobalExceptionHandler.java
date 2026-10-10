package com.gamingcastle.loyaltyservice.exception;

import com.gamingcastle.loyaltyservice.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(InvalidLoyaltyRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidLoyaltyRequestException ex,
                                                              HttpServletRequest request) {
        log.warn("Invalid loyalty request at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ErrorCodes.INVALID_LOYALTY_REQUEST, ex.getMessage(),
                        HttpStatus.UNPROCESSABLE_ENTITY, request.getRequestURI()));
    }

    @ExceptionHandler(InsufficientPointsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPoints(InsufficientPointsException ex,
                                                                  HttpServletRequest request) {
        log.warn("Insufficient points at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ErrorCodes.INSUFFICIENT_POINTS, ex.getMessage(),
                        HttpStatus.UNPROCESSABLE_ENTITY, request.getRequestURI()));
    }

    @ExceptionHandler(RedemptionRejectedException.class)
    public ResponseEntity<ErrorResponse> handleRedemptionRejected(RedemptionRejectedException ex,
                                                                  HttpServletRequest request) {
        log.warn("Redemption rejected at {}: {} ({})", request.getRequestURI(), ex.getMessage(), ex.getErrorCode());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(),
                        HttpStatus.UNPROCESSABLE_ENTITY, request.getRequestURI()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        log.warn("Missing header at {}: {}", request.getRequestURI(), ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCodes.MISSING_HEADER, "Missing required header: " + ex.getHeaderName(),
                        HttpStatus.BAD_REQUEST, request.getRequestURI()));
    }

    @ExceptionHandler(LoyaltyAccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(LoyaltyAccountNotFoundException ex,
                                                               HttpServletRequest request) {
        log.warn("Account missing at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCodes.LOYALTY_ACCOUNT_NOT_FOUND, ex.getMessage(),
                        HttpStatus.NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(LoyaltyRulesNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRulesNotFound(LoyaltyRulesNotFoundException ex,
                                                             HttpServletRequest request) {
        log.warn("Rules missing at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCodes.LOYALTY_RULES_NOT_FOUND, ex.getMessage(),
                        HttpStatus.NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException ex,
                                                         HttpServletRequest request) {
        log.warn("Forbidden at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCodes.FORBIDDEN, ex.getMessage(),
                        HttpStatus.FORBIDDEN, request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCodes.FORBIDDEN, ErrorCodes.FORBIDDEN_MESSAGE,
                        HttpStatus.FORBIDDEN, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        log.warn("Malformed JSON at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCodes.MALFORMED_REQUEST, "Malformed JSON request",
                        HttpStatus.BAD_REQUEST, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        log.warn("Type mismatch at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCodes.TYPE_MISMATCH, "Invalid request parameter type",
                        HttpStatus.BAD_REQUEST, request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        log.warn("Method not allowed at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(ErrorCodes.METHOD_NOT_ALLOWED, "HTTP method not supported",
                        HttpStatus.METHOD_NOT_ALLOWED, request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity issue at {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCodes.DATA_INTEGRITY_VIOLATION, "A data integrity constraint was violated",
                        HttpStatus.CONFLICT, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCodes.INTERNAL_ERROR, ErrorCodes.INTERNAL_ERROR_MESSAGE,
                        HttpStatus.INTERNAL_SERVER_ERROR, request.getRequestURI()));
    }
}