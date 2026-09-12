package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.dto.AuthResponse;
import com.gamingcastle.userservice.dto.LoginRequest;
import com.gamingcastle.userservice.dto.RegisterRequest;
import com.gamingcastle.userservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FR-01/FR-03: public auth endpoints. These are the two paths listed as
 * PUBLIC_PATHS in the Gateway's JwtAuthenticationFilter — no token required.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
