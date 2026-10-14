package com.gamingcastle.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamingcastle.userservice.dto.request.ConfirmReactivationRequest;
import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.LoginRequest;
import com.gamingcastle.userservice.dto.request.PhoneLoginRequest;
import com.gamingcastle.userservice.dto.request.ReactivateAccountRequest;
import com.gamingcastle.userservice.dto.request.RegisterRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;
import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.exception.AccountLockedException;
import com.gamingcastle.userservice.exception.EmailAlreadyExistsException;
import com.gamingcastle.userservice.exception.GlobalExceptionHandler;
import com.gamingcastle.userservice.exception.InvalidCredentialsException;
import com.gamingcastle.userservice.exception.InvalidResetCodeException;
import com.gamingcastle.userservice.service.AccountReactivationService;
import com.gamingcastle.userservice.service.AuthService;
import com.gamingcastle.userservice.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private AccountReactivationService accountReactivationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, passwordResetService, accountReactivationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    // --- POST /api/auth/register ---

    @Test
    @DisplayName("POST /api/auth/register: should return 201 Created and AuthResponse given valid request")
    void register_shouldReturnCreated_whenRequestIsValid() throws Exception {
        RegisterRequest request = new RegisterRequest("player@example.com", "password123", "Test Player", "0771234567");
        AuthResponse response = AuthResponse.of("mock-jwt-token", UUID.randomUUID().toString(), "player@example.com", "CUSTOMER", true);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.notificationSent").value(true));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/register: should return 400 Bad Request given invalid email")
    void register_shouldReturnBadRequest_whenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("invalid-email", "password123", "Test Player", "0771234567");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register: should return 400 Bad Request given password shorter than 8 chars")
    void register_shouldReturnBadRequest_whenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("player@example.com", "short", "Test Player", "0771234567");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register: should return 409 Conflict when email is already taken")
    void register_shouldReturnConflict_whenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("player@example.com", "password123", "Test Player", null);
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
    }

    // --- POST /api/auth/login ---

    @Test
    @DisplayName("POST /api/auth/login: should return 200 OK and AuthResponse given valid credentials")
    void login_shouldReturnOk_whenCredentialsAreValid() throws Exception {
        LoginRequest request = new LoginRequest("player@example.com", "password123");
        AuthResponse response = AuthResponse.of("mock-jwt-token", UUID.randomUUID().toString(), "player@example.com", "CUSTOMER");

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.email").value("player@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/auth/login: should return 400 Bad Request given blank password")
    void login_shouldReturnBadRequest_whenPasswordIsBlank() throws Exception {
        LoginRequest request = new LoginRequest("player@example.com", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login: should return 401 Unauthorized given invalid credentials")
    void login_shouldReturnUnauthorized_whenCredentialsAreInvalid() throws Exception {
        LoginRequest request = new LoginRequest("player@example.com", "wrong-pass");
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/auth/login: should return 423 Locked with Retry-After header when account is locked")
    void login_shouldReturnLocked_whenAccountIsLocked() throws Exception {
        LoginRequest request = new LoginRequest("player@example.com", "wrong-pass");
        Instant lockedUntil = Instant.now().plusSeconds(600);
        when(authService.login(any(LoginRequest.class))).thenThrow(new AccountLockedException(lockedUntil));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"));
    }

    // --- POST /api/auth/login/phone ---

    @Test
    @DisplayName("POST /api/auth/login/phone: should return 200 OK and AuthResponse given valid phone credentials")
    void loginByPhone_shouldReturnOk_whenCredentialsAreValid() throws Exception {
        PhoneLoginRequest request = new PhoneLoginRequest("0771234567", "password123");
        AuthResponse response = AuthResponse.of("mock-jwt-token", UUID.randomUUID().toString(), "player@example.com", "CUSTOMER");

        when(authService.loginByPhone(any(PhoneLoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/login/phone: should return 400 Bad Request given blank phone number")
    void loginByPhone_shouldReturnBadRequest_whenPhoneNumberIsBlank() throws Exception {
        PhoneLoginRequest request = new PhoneLoginRequest("", "password123");

        mockMvc.perform(post("/api/auth/login/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    // --- POST /api/auth/password/forgot ---

    @Test
    @DisplayName("POST /api/auth/password/forgot: should return 202 Accepted given valid identifier")
    void forgotPassword_shouldReturnAccepted_whenRequestIsValid() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("player@example.com");

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(passwordResetService).requestReset(any(ForgotPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot: should return 400 Bad Request given blank identifier")
    void forgotPassword_shouldReturnBadRequest_whenIdentifierIsBlank() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("");

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- POST /api/auth/password/reset ---

    @Test
    @DisplayName("POST /api/auth/password/reset: should return 200 OK given valid reset request")
    void resetPassword_shouldReturnOk_whenRequestIsValid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("player@example.com", "123456", "newpassword123");

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordResetService).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/password/reset: should return 400 Bad Request when reset code is invalid")
    void resetPassword_shouldReturnBadRequest_whenCodeIsInvalid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("player@example.com", "000000", "newpassword123");
        doThrow(new InvalidResetCodeException("Invalid verification code"))
                .when(passwordResetService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RESET_CODE"));
    }

    @Test
    @DisplayName("POST /api/auth/password/reset: should return 400 Bad Request when new password is too short")
    void resetPassword_shouldReturnBadRequest_whenNewPasswordTooShort() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("player@example.com", "123456", "short");

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    // --- POST /api/auth/reactivate/request ---

    @Test
    @DisplayName("POST /api/auth/reactivate/request: should return 202 Accepted given valid identifier")
    void requestReactivation_shouldReturnAccepted_whenRequestIsValid() throws Exception {
        ReactivateAccountRequest request = new ReactivateAccountRequest("player@example.com");

        mockMvc.perform(post("/api/auth/reactivate/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(accountReactivationService).requestReactivation(any(ReactivateAccountRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/request: should return 400 Bad Request given blank identifier")
    void requestReactivation_shouldReturnBadRequest_whenIdentifierIsBlank() throws Exception {
        ReactivateAccountRequest request = new ReactivateAccountRequest("");

        mockMvc.perform(post("/api/auth/reactivate/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- POST /api/auth/reactivate/confirm ---

    @Test
    @DisplayName("POST /api/auth/reactivate/confirm: should return 200 OK and AuthResponse given valid code")
    void confirmReactivation_shouldReturnOk_whenRequestIsValid() throws Exception {
        ConfirmReactivationRequest request = new ConfirmReactivationRequest("player@example.com", "123456");
        AuthResponse response = AuthResponse.of("mock-jwt-token", UUID.randomUUID().toString(), "player@example.com", "CUSTOMER");

        when(accountReactivationService.confirmReactivation(any(ConfirmReactivationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/reactivate/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"));

        verify(accountReactivationService).confirmReactivation(any(ConfirmReactivationRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/reactivate/confirm: should return 400 Bad Request when code is invalid")
    void confirmReactivation_shouldReturnBadRequest_whenCodeIsInvalid() throws Exception {
        ConfirmReactivationRequest request = new ConfirmReactivationRequest("player@example.com", "000000");
        when(accountReactivationService.confirmReactivation(any(ConfirmReactivationRequest.class)))
                .thenThrow(new InvalidResetCodeException());

        mockMvc.perform(post("/api/auth/reactivate/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RESET_CODE"));
    }
}
