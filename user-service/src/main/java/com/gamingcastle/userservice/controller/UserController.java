package com.gamingcastle.userservice.controller;

import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
import com.gamingcastle.userservice.security.GatewayAuthenticationFilter;
import com.gamingcastle.userservice.service.UserService;
import com.gamingcastle.userservice.util.APIEndPoints;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(APIEndPoints.baseAPI)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(APIEndPoints.profile)
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID userId) {

        return ResponseEntity.ok(userService.getMyProfile(userId));
    }

    @PatchMapping(APIEndPoints.profile)
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("Profile update request: userId={}", userId);
        return ResponseEntity.ok(userService.updateMyProfile(userId, request));
    }

    @GetMapping(APIEndPoints.users)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserSummaryResponse>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @GetMapping(APIEndPoints.userById)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummaryResponse> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @DeleteMapping(APIEndPoints.profile)
    public ResponseEntity<Void> deactivateMyAccount(
            @RequestHeader(GatewayAuthenticationFilter.USER_ID_HEADER) UUID userId) {
        log.info("Account deactivation request: userId={}", userId);
        userService.deactivateAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(APIEndPoints.userById)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID userId) {
        log.info("Admin account deactivation: targetUserId={}", userId);
        userService.deactivateAccount(userId);
        return ResponseEntity.noContent().build();
    }

}

