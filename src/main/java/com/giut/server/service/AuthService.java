package com.giut.server.service;

import com.giut.server.dto.auth.request.LoginRequest;
import com.giut.server.dto.auth.request.SignUpRequest;
import com.giut.server.dto.auth.response.LoginResponse;
import com.giut.server.dto.auth.response.SignUpResponse;
import com.giut.server.entity.User;
import com.giut.server.repository.UserRepository;
import com.giut.server.security.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.createAdmin(
                request.getEmail(),
                encodedPassword,
                request.getNickname()
        );

        User savedUser = userRepository.save(user);

        return new SignUpResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getNickname(),
                savedUser.getRole()
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (user.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("관리자 계정만 로그인할 수 있습니다.");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                accessToken,
                refreshToken,
                "Bearer"
        );
    }
}
