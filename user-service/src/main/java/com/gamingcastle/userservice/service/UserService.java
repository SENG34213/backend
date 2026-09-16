package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.entity.User;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User getMyProfile(UUID userId);
    User getUserById(UUID userId);
    List<User> getAllUsers();
    User updateMyProfile(UUID userId, String fullName, String phoneNumber);
    void deleteMyAccount(UUID userId);
}

