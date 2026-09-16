package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.dto.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.UserResponse;
import com.gamingcastle.userservice.security.GatewayAuthenticationFilter;
import com.gamingcastle.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID userId) {

        return ResponseEntity.ok(UserResponse.from(userService.getMyProfile(userId)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(UserResponse.from(userService.getUserById(userId)));
    }

    /** Admin-only endpoint. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers()
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse response = UserResponse.from(
                userService.updateMyProfile(userId, request.fullName(), request.phoneNumber())
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID userId) {

        userService.deleteMyAccount(userId);
        return ResponseEntity.noContent().build();
    }
}

