package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.entity.DeactivatedBy;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.UserAlreadyActiveException;
import com.gamingcastle.userservice.exception.UserAlreadyDeactivatedException;
import com.gamingcastle.userservice.exception.UserNotFoundException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Deactivation / activation rules: who did it is recorded, and decides what the user may do next. */
class UserServiceTest {

    @Mock private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserServiceImpl(userRepository);
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("player@example.com")
                .passwordHash("hash")
                .fullName("Test Player")
                .role(Role.CUSTOMER)
                .build();
    }

    // --- self deactivation ---

    @Test
    void deactivateMyAccount_shouldMarkAsSelf() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deactivateMyAccount(user.getId());

        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getDeactivatedBy()).isEqualTo(DeactivatedBy.SELF);
        verify(userRepository).save(user);
    }

    @Test
    void deactivateMyAccount_shouldReject_whenAlreadyDeactivated() {
        User user = activeUser();
        user.deactivate(DeactivatedBy.SELF);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deactivateMyAccount(user.getId()))
                .isInstanceOf(UserAlreadyDeactivatedException.class);
        verify(userRepository, never()).save(any());
    }

    // --- admin deactivation ---

    @Test
    void deactivateUser_shouldMarkAsAdmin_whenAdminDeactivatesSomeoneElse() {
        User user = activeUser();
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deactivateUser(user.getId(), adminId);

        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getDeactivatedBy()).isEqualTo(DeactivatedBy.ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_shouldMarkAsSelf_whenAdminDeactivatesTheirOwnAccount() {
        User admin = activeUser();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        userService.deactivateUser(admin.getId(), admin.getId());

        assertThat(admin.getDeactivatedBy()).isEqualTo(DeactivatedBy.SELF);
    }

    @Test
    void deactivateUser_shouldUpgradeSelfDeactivationToAdmin() {
        User user = activeUser();
        user.deactivate(DeactivatedBy.SELF);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deactivateUser(user.getId(), UUID.randomUUID());

        assertThat(user.getDeactivatedBy()).isEqualTo(DeactivatedBy.ADMIN);
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_shouldReject_whenAlreadyDeactivatedByAdmin() {
        User user = activeUser();
        user.deactivate(DeactivatedBy.ADMIN);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deactivateUser(user.getId(), UUID.randomUUID()))
                .isInstanceOf(UserAlreadyDeactivatedException.class);
    }

    @Test
    void deactivateUser_shouldThrowNotFound_givenUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(id, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }

    // --- admin activation ---

    @Test
    void activateUser_shouldReactivateAndClearDeactivationInfo() {
        User user = activeUser();
        user.deactivate(DeactivatedBy.ADMIN);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.activateUser(user.getId());

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getDeactivatedBy()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void activateUser_shouldReject_whenAlreadyActive() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.activateUser(user.getId()))
                .isInstanceOf(UserAlreadyActiveException.class);
        verify(userRepository, never()).save(any());
    }
}
