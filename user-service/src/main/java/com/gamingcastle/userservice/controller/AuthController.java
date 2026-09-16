package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.dto.request.LoginRequest;
import com.gamingcastle.userservice.dto.request.RegisterRequest;
import com.gamingcastle.userservice.service.AuthService;
import com.gamingcastle.userservice.util.APIEndPoints;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * FR-01/FR-03: public auth endpoints. These are the two paths listed as
 * PUBLIC_PATHS in the Gateway's JwtAuthenticationFilter — no token required.
 */
@RestController
@RequestMapping(APIEndPoints.baseAuthAPI)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
