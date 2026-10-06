package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.dto.request.ConfirmReactivationRequest;
import com.gamingcastle.userservice.dto.request.ReactivateAccountRequest;
import com.gamingcastle.userservice.dto.response.AuthResponse;

public interface AccountReactivationService {
    void requestReactivation(ReactivateAccountRequest request);
    AuthResponse confirmReactivation(ConfirmReactivationRequest request);
}
