package com.giut.server.auth.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @InjectMocks AuthService authService;

    @Test
    void logoutBumpsTokenVersionUnderLock() {
        User user = User.createOAuthUser("student@example.com", "student", User.OAuthProvider.KAKAO, "kakao-42");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));

        authService.logout(42L);

        assertThat(user.getTokenVersion()).isEqualTo(1L);
    }

    @Test
    void logoutOfMissingUserFails() {
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(42L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
