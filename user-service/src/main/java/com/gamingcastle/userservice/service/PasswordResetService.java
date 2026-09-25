package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.ForgotPasswordRequest;
import com.gamingcastle.userservice.dto.request.ResetPasswordRequest;

/** FR-04/FR-05: password reset via email verification or phone verification. */
public interface PasswordResetService {

    /** Generates and sends a verification code to whichever channel the identifier resolves to. */
    void requestReset(ForgotPasswordRequest request);

    /** Verifies the code and sets the new password. */
    void resetPassword(ResetPasswordRequest request);
}
