package com.gamingcastle.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingcastle.userservice.dto.request.UpdateProfileRequest;
import com.gamingcastle.userservice.dto.response.UserProfileResponse;
import com.gamingcastle.userservice.dto.response.UserSummaryResponse;
import com.gamingcastle.userservice.entity.DeactivatedBy;
import com.gamingcastle.userservice.exception.GlobalExceptionHandler;
import com.gamingcastle.userservice.exception.UserAlreadyActiveException;
import com.gamingcastle.userservice.exception.UserAlreadyDeactivatedException;
import com.gamingcastle.userservice.exception.UserNotFoundException;
import com.gamingcastle.userservice.security.GatewayAuthenticationFilter;
import com.gamingcastle.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String USER_ID_HEADER = GatewayAuthenticationFilter.USER_ID_HEADER;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    // --- GET /api/users/me ---

    @Test
    @DisplayName("GET /api/users/me: should return 200 OK and UserProfileResponse given valid X-User-Id header")
    void getMyProfile_shouldReturnProfile_whenHeaderIsPresent() throws Exception {
        UUID userId = UUID.randomUUID();
        UserProfileResponse profile = new UserProfileResponse(
                userId, "player@example.com", "Test Player", "0771234567", "CUSTOMER");

        when(userService.getMyProfile(userId)).thenReturn(profile);

        mockMvc.perform(get("/api/users/me")
                        .header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test Player"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        verify(userService).getMyProfile(userId);
    }

    @Test
    @DisplayName("GET /api/users/me: should return 400 Bad Request when X-User-Id header is missing")
    void getMyProfile_shouldReturnBadRequest_whenHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/users/me: should return 404 Not Found when user does not exist")
    void getMyProfile_shouldReturnNotFound_whenUserNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getMyProfile(userId)).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/users/me")
                        .header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    // --- PATCH /api/users/me ---

    @Test
    @DisplayName("PATCH /api/users/me: should return 200 OK and updated UserProfileResponse given valid request")
    void updateCurrentUser_shouldReturnUpdatedProfile_whenRequestIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name", "0779876543");
        UserProfileResponse profile = new UserProfileResponse(
                userId, "player@example.com", "Updated Name", "0779876543", "CUSTOMER");

        when(userService.updateMyProfile(eq(userId), any(UpdateProfileRequest.class))).thenReturn(profile);

        mockMvc.perform(patch("/api/users/me")
                        .header(USER_ID_HEADER, userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phoneNumber").value("0779876543"));

        verify(userService).updateMyProfile(eq(userId), any(UpdateProfileRequest.class));
    }

    @Test
    @DisplayName("PATCH /api/users/me: should return 400 Bad Request when fullName is blank")
    void updateCurrentUser_shouldReturnBadRequest_whenFullNameIsBlank() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("", "0779876543");

        mockMvc.perform(patch("/api/users/me")
                        .header(USER_ID_HEADER, userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    // --- GET /api/users ---

    @Test
    @DisplayName("GET /api/users: should return 200 OK and paged user summaries")
    void getAllUsers_shouldReturnPagedUsers_whenAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSummaryResponse summary = new UserSummaryResponse(
                userId, "player@example.com", "Test Player", "0771234567",
                "CUSTOMER", true, Instant.now(), null);

        when(userService.getAllUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/users?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("player@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(userService).getAllUsers(any(Pageable.class));
    }

    // --- GET /api/users/{userId} ---

    @Test
    @DisplayName("GET /api/users/{userId}: should return 200 OK and UserSummaryResponse given existing user")
    void getUserById_shouldReturnUserSummary_whenUserExists() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSummaryResponse summary = new UserSummaryResponse(
                userId, "player@example.com", "Test Player", "0771234567",
                "CUSTOMER", true, Instant.now(), null);

        when(userService.getUserById(userId)).thenReturn(summary);

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("player@example.com"));

        verify(userService).getUserById(userId);
    }

    @Test
    @DisplayName("GET /api/users/{userId}: should return 404 Not Found when user does not exist")
    void getUserById_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId)).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    // --- PATCH /api/users/me/deactivate ---

    @Test
    @DisplayName("PATCH /api/users/me/deactivate: should return 204 No Content when user self-deactivates")
    void deactivateMyAccount_shouldReturnNoContent_whenSuccessful() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/users/me/deactivate")
                        .header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).deactivateMyAccount(userId);
    }

    @Test
    @DisplayName("PATCH /api/users/me/deactivate: should return 409 Conflict when account is already deactivated")
    void deactivateMyAccount_shouldReturnConflict_whenAlreadyDeactivated() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new UserAlreadyDeactivatedException(userId)).when(userService).deactivateMyAccount(userId);

        mockMvc.perform(patch("/api/users/me/deactivate")
                        .header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USER_ALREADY_DEACTIVATED"));
    }

    // --- PATCH /api/users/{userId}/deactivate ---

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate: should return 204 No Content when admin deactivates user")
    void deactivateUser_shouldReturnNoContent_whenAdminDeactivatesTarget() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(patch("/api/users/" + targetUserId + "/deactivate")
                        .header(USER_ID_HEADER, adminId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).deactivateUser(targetUserId, adminId);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/deactivate: should return 409 Conflict when target user is already deactivated")
    void deactivateUser_shouldReturnConflict_whenAlreadyDeactivated() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        doThrow(new UserAlreadyDeactivatedException(targetUserId))
                .when(userService).deactivateUser(targetUserId, adminId);

        mockMvc.perform(patch("/api/users/" + targetUserId + "/deactivate")
                        .header(USER_ID_HEADER, adminId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USER_ALREADY_DEACTIVATED"));
    }

    // --- PATCH /api/users/{userId}/activate ---

    @Test
    @DisplayName("PATCH /api/users/{userId}/activate: should return 204 No Content when admin activates user")
    void activateUser_shouldReturnNoContent_whenSuccessful() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(patch("/api/users/" + targetUserId + "/activate"))
                .andExpect(status().isNoContent());

        verify(userService).activateUser(targetUserId);
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/activate: should return 409 Conflict when user is already active")
    void activateUser_shouldReturnConflict_whenAlreadyActive() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        doThrow(new UserAlreadyActiveException(targetUserId))
                .when(userService).activateUser(targetUserId);

        mockMvc.perform(patch("/api/users/" + targetUserId + "/activate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USER_ALREADY_ACTIVE"));
    }
}
