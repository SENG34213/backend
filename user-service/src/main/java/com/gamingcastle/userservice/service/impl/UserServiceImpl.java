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
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim().isEmpty() ? null : request.avatarUrl().trim());
        }
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
                user.getRole().name(),
                user.getAvatarUrl()
        );
    }

    @Override
    public Page<UserSummaryResponse> searchUsers(String query, com.gamingcastle.userservice.entity.Role role, Boolean enabled, Pageable pageable) {
        String trimmedQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        org.springframework.data.jpa.domain.Specification<User> spec = (root, q, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (trimmedQuery != null) {
                String pattern = "%" + trimmedQuery.toLowerCase() + "%";
                jakarta.persistence.criteria.Predicate nameLike = cb.like(cb.lower(root.get("fullName")), pattern);
                jakarta.persistence.criteria.Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
                jakarta.persistence.criteria.Predicate phoneLike = cb.like(root.get("phoneNumber"), "%" + trimmedQuery + "%");
                predicates.add(cb.or(nameLike, emailLike, phoneLike));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return userRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    public com.gamingcastle.userservice.dto.response.AdminUserStatsResponse getUserStats() {
        java.time.ZoneId colomboZone = java.time.ZoneId.of("Asia/Colombo");
        java.time.LocalDate today = java.time.LocalDate.now(colomboZone);
        java.time.Instant startOfToday = today.atStartOfDay(colomboZone).toInstant();
        java.time.Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(colomboZone).toInstant();

        long total = userRepository.count();
        long active = userRepository.countByEnabled(true);
        long deactivated = userRepository.countByEnabled(false);
        long customerCount = userRepository.countByRole(com.gamingcastle.userservice.entity.Role.CUSTOMER);
        long adminCount = userRepository.countByRole(com.gamingcastle.userservice.entity.Role.ADMIN);
        long newToday = userRepository.countByCreatedAtGreaterThanEqual(startOfToday);
        long newMonth = userRepository.countByCreatedAtGreaterThanEqual(startOfMonth);

        return new com.gamingcastle.userservice.dto.response.AdminUserStatsResponse(
                total, active, deactivated, customerCount, adminCount, newToday, newMonth
        );
    }

    @Override
    @Transactional
    public UserSummaryResponse adminUpdateUser(UUID userId, com.gamingcastle.userservice.dto.request.AdminUpdateUserRequest request) {
        User user = findByIdOrThrow(userId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phoneNumber() != null) {
            String phone = request.phoneNumber().trim();
            user.setPhoneNumber(phone.isEmpty() ? null : phone);
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            if (request.enabled() && !user.isEnabled()) {
                user.activate();
            } else if (!request.enabled() && user.isEnabled()) {
                user.deactivate(DeactivatedBy.ADMIN);
            }
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim().isEmpty() ? null : request.avatarUrl().trim());
        }

        user = userRepository.save(user);
        log.info("Admin updated user: userId={}, role={}, enabled={}", userId, user.getRole(), user.isEnabled());
        return toSummaryResponse(user);
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
                user.getDeactivatedBy(),
                user.getAvatarUrl()
        );
    }
}