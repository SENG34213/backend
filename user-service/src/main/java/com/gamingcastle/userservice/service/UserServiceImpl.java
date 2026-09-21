package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.AuthException;
import com.gamingcastle.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        User user = findByIdOrThrow(userId);
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = findByIdOrThrow(userId);

        // US-3 AC: only fullName/phoneNumber can change here — email, role,
        // and enabled status are deliberately untouched by this endpoint.
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        userRepository.save(user);

        log.info("Profile updated: userId={}", userId);
        return toProfileResponse(user);
    }

    @Override
    public Page<UserSummaryResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryResponse getUserById(UUID userId) {
        User user = findByIdOrThrow(userId);
        return toSummaryResponse(user);
    }

    @Override
    @Transactional
    public void deactivateAccount(UUID userId) {
        User user = findByIdOrThrow(userId);

        if (!user.isEnabled()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                    "No user found with the given id");
        }
        user.setEnabled(false);
        userRepository.save(user);

        log.info("Account deactivated: userId={}", userId);
    }

    private User findByIdOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "No user found with the given id"));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole().name()
        );
    }

    private UserSummaryResponse toSummaryResponse(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}


