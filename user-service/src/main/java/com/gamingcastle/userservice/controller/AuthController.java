package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;
import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.dto.request.LoginRequest;
import com.gamingcastle.userservice.dto.request.PhoneLoginRequest;
import com.gamingcastle.userservice.dto.request.RegisterRequest;
import com.gamingcastle.userservice.service.AuthService;
import com.gamingcastle.userservice.service.PasswordResetService;
import com.gamingcastle.userservice.util.APIEndPoints;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FR-01/FR-03/FR-04/FR-05: public auth endpoints. These are all listed as
 * PUBLIC_PATHS in the Gateway's JwtAuthenticationFilter — no token required.
 */
@RestController
@RequestMapping(APIEndPoints.baseAuthAPI)
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping(APIEndPoints.register)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(APIEndPoints.login)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /** FR-03: log in with phone number + password. */
    @PostMapping(APIEndPoints.loginByPhone)
    public ResponseEntity<AuthResponse> loginByPhone(@Valid @RequestBody PhoneLoginRequest request) {
        AuthResponse response = authService.loginByPhone(request);
        return ResponseEntity.ok(response);
    }

    /**
     * FR-04/FR-05: request a password-reset verification code. Always
     * responds 202 regardless of whether the identifier matches an account,
     * so the endpoint can't be used to enumerate registered users/phones.
     */
    @PostMapping(APIEndPoints.forgotPassword)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** FR-04/FR-05: complete the password reset using the emailed/texted code. */
    @PostMapping(APIEndPoints.resetPassword)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
