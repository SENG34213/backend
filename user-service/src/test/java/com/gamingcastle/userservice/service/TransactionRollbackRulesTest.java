package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.client.NotificationClient;
import com.gamingcastle.userservice.dto.request.LoginRequest;
import com.gamingcastle.userservice.entity.Role;
import com.gamingcastle.userservice.entity.User;
import com.gamingcastle.userservice.exception.AccountLockedException;
import com.gamingcastle.userservice.exception.InvalidCredentialsException;
import com.gamingcastle.userservice.repository.UserRepository;
import com.gamingcastle.userservice.service.impl.AuthServiceImpl;
import com.gamingcastle.userservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionRollbackRulesTest {

    private PlatformTransactionManager txManager;
    private TransactionStatus txStatus;

    @BeforeEach
    void setUp() {
        txManager = mock(PlatformTransactionManager.class);
        txStatus = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(txStatus);
    }

    private <I> I withTransactions(Object target, Class<I> iface) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor((TransactionManager) txManager,
                new AnnotationTransactionAttributeSource()));
        return iface.cast(factory.getProxy());
    }

    @Test
    void login_shouldCommitFailedAttempt_whenPasswordIsWrong() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        User user = User.builder().id(UUID.randomUUID()).email("player@example.com")
                .passwordHash("hash").role(Role.CUSTOMER).build();
        when(userRepository.findByEmailForUpdate("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        AuthService authService = withTransactions(
                new AuthServiceImpl(userRepository, passwordEncoder, mock(JwtUtil.class), mock(NotificationClient.class)),
                AuthService.class);

        assertThatThrownBy(() -> authService.login(new LoginRequest("player@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(txManager).commit(txStatus);          // rollback aagakoodaadhu
        verify(txManager, never()).rollback(any());
    }

    private void assertCommittedNotRolledBack() {
        verify(txManager).commit(txStatus);
        verify(txManager, never()).rollback(any());
    }

    @Test
    void login_shouldCommitLock_whenFifthWrongPasswordTriggersLockout() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        User user = User.builder().id(UUID.randomUUID()).email("player@example.com")
                .passwordHash("hash").role(Role.CUSTOMER).failedLoginAttempts(4).build();
        when(userRepository.findByEmailForUpdate("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        AuthService authService = withTransactions(
                new AuthServiceImpl(userRepository, passwordEncoder, mock(JwtUtil.class), mock(NotificationClient.class)),
                AuthService.class);

        assertThatThrownBy(() -> authService.login(new LoginRequest("player@example.com", "wrong")))
                .isInstanceOfAny(InvalidCredentialsException.class, AccountLockedException.class);

        assertThat(user.isLocked()).isTrue();
        assertCommittedNotRolledBack();   // rollback aanaa lock DB-ku pogaadhu
    }
}
