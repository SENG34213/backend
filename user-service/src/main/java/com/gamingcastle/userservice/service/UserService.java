package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.AdminUpdateUserRequest;
import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.AdminUserStatsResponse;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
import com.gamingcastle.userservice.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserProfileResponse getMyProfile(UUID userId);
    UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request);
    Page<UserSummaryResponse> getAllUsers(Pageable pageable);
    Page<UserSummaryResponse> searchUsers(String query, Role role, Boolean enabled, Pageable pageable);
    UserSummaryResponse getUserById(UUID userId);
    AdminUserStatsResponse getUserStats();
    UserSummaryResponse adminUpdateUser(UUID userId, AdminUpdateUserRequest request);
    void deactivateMyAccount(UUID userId);
    void deactivateUser(UUID targetUserId, UUID adminId);
    void activateUser(UUID userId);
}