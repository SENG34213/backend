package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserProfileResponse getMyProfile(UUID userId);
    UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request);
    Page<UserSummaryResponse> getAllUsers(Pageable pageable);
    UserSummaryResponse getUserById(UUID userId);
    void deactivateAccount(UUID userId);
}

