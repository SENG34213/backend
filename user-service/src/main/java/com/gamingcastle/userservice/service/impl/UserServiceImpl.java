package com.gamingcastle.userservice.service.impl;

import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.UserAlreadyDeactivatedException;
import com.gamingcastle.userservice.exception.UserNotFoundException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gamingcastle.userservice.entity.DeactivatedBy;
import com.gamingcastle.userservice.exception.UserAlreadyActiveException;

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
    public void deactivateMyAccount(UUID userId) {
        User user = findByIdOrThrow(userId);

        if (!user.isEnabled()) {
            throw new UserAlreadyDeactivatedException(userId);
        }
        user.deactivate(DeactivatedBy.SELF);
        userRepository.save(user);

        log.info("Account deactivated by the user: userId={}", userId);
    }

    @Override
    @Transactional
    public void deactivateUser(UUID targetUserId, UUID adminId) {
        User user = findByIdOrThrow(targetUserId);

        DeactivatedBy by = targetUserId.equals(adminId) ? DeactivatedBy.SELF : DeactivatedBy.ADMIN;

        if (!user.isEnabled()) {
            if (user.getDeactivatedBy() == DeactivatedBy.SELF && by == DeactivatedBy.ADMIN) {
                user.deactivate(DeactivatedBy.ADMIN);
                userRepository.save(user);
                log.info("Self-deactivated account taken over by admin: targetUserId={}, adminId={}",
                        targetUserId, adminId);
                return;
            }
            throw new UserAlreadyDeactivatedException(targetUserId);
        }

        user.deactivate(by);
        userRepository.save(user);

        log.info("Account deactivated: targetUserId={}, by={}, adminId={}", targetUserId, by, adminId);
    }

    @Override
    @Transactional
    public void activateUser(UUID userId) {
        User user = findByIdOrThrow(userId);

        if (user.isEnabled()) {
            throw new UserAlreadyActiveException(userId);
        }
        user.activate();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Account reactivated by admin: userId={}", userId);
    }

    private User findByIdOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
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
                user.getCreatedAt(),
                user.getDeactivatedBy()
        );
    }
}