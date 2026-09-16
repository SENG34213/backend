package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.AuthException;
import com.gamingcastle.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public User getMyProfile(UUID userId) {
        return findUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID userId) {
        return findUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateMyProfile(UUID userId, String fullName, String phoneNumber) {
        User user = findUser(userId);

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        user.setPhoneNumber(phoneNumber == null ? null : phoneNumber.trim());

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteMyAccount(UUID userId) {
        User user = findUser(userId);
        userRepository.delete(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found"
                ));
    }
}

