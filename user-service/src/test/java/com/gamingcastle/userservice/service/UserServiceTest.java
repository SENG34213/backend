package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
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

    // --- Profile Management ---

    @Test
    void getMyProfile_shouldReturnProfile_whenUserExists() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getMyProfile(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.fullName()).isEqualTo(user.getFullName());
        assertThat(response.phoneNumber()).isEqualTo(user.getPhoneNumber());
        assertThat(response.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void getMyProfile_shouldThrowNotFound_whenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateMyProfile_shouldUpdateOnlyFullNameAndPhoneNumber_whenUserExists() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest("Updated Player", "0779998888");
        UserProfileResponse response = userService.updateMyProfile(user.getId(), request);

        assertThat(user.getFullName()).isEqualTo("Updated Player");
        assertThat(user.getPhoneNumber()).isEqualTo("0779998888");
        assertThat(response.fullName()).isEqualTo("Updated Player");
        assertThat(response.phoneNumber()).isEqualTo("0779998888");
        verify(userRepository).save(user);
    }

    @Test
    void updateMyProfile_shouldThrowNotFound_whenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest("Updated Player", "0779998888");
        assertThatThrownBy(() -> userService.updateMyProfile(id, request))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // --- User Queries ---

    @Test
    void getAllUsers_shouldReturnPagedUserSummaries_givenPageable() {
        User user = activeUser();
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        Page<UserSummaryResponse> page = userService.getAllUsers(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).email()).isEqualTo("player@example.com");
        assertThat(page.getContent().get(0).role()).isEqualTo("CUSTOMER");
    }

    @Test
    void getUserById_shouldReturnSummary_whenUserExists() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserSummaryResponse response = userService.getUserById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("player@example.com");
        assertThat(response.fullName()).isEqualTo("Test Player");
    }

    @Test
    void getUserById_shouldThrowNotFound_whenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    // --- NotFound edge cases on activation/deactivation ---

    @Test
    void deactivateMyAccount_shouldThrowNotFound_givenUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateMyAccount(id))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateUser_shouldThrowNotFound_givenUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.activateUser(id))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }
}
