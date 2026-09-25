package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.response.AuthResponse;
import com.gamingcastle.userservice.dto.request.LoginRequest;
import com.gamingcastle.userservice.dto.request.PhoneLoginRequest;
import com.gamingcastle.userservice.dto.request.RegisterRequest;

/**
 * FR-01–FR-06: registration, login, and account-lockout contract.
 * The controller and tests depend on this interface, not on
 * {@link AuthServiceImpl} directly — Spring wires the one implementation
 * in automatically since there's exactly one @Service bean of this type.
 */
public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /** FR-03: login with phone number + password instead of email + password. */
    AuthResponse loginByPhone(PhoneLoginRequest request);
}
