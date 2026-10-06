package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.entity.VerificationPurpose;

import java.util.Optional;

public interface VerificationCodeService {
    Optional<User> findUserByIdentifier(String identifier);

    void issueCode(User user, String identifier, VerificationPurpose purpose);

    void verifyAndConsume(User user, String code, VerificationPurpose purpose);
}