package com.giut.server.auth.service;

import com.giut.server.auth.dto.request.LoginRequest;
import com.giut.server.auth.dto.request.SignUpRequest;
import com.giut.server.auth.dto.response.LoginResponse;
import com.giut.server.auth.dto.response.SignUpResponse;
import com.giut.server.user.entity.User;
import com.giut.server.auth.exception.AuthenticationFailedException;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.user.repository.UserRepository;
import com.giut.server.auth.security.JwtProvider;
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
    public SignUpResponse signUp(Long adminUserId, SignUpRequest request) {
        // 관리자 계정은 누구나 가입할 수 없고, 기존 활성 관리자만 새 관리자를 만들 수 있다.
        if (adminUserId == null
                || !userRepository.existsByIdAndRoleAndStatus(adminUserId, User.Role.ADMIN, User.Status.ACTIVE)) {
            throw new ForbiddenException("활성 관리자만 관리자 계정을 생성할 수 있습니다.");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("이미 사용 중인 이메일입니다.");
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
                .orElseThrow(() -> new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 이메일이 존재하지 않을 때/관리자가 아닐 때/비밀번호가 틀렸을 때 메시지를 전부 동일하게 둬서,
        // 어느 조건에서 막혔는지로 계정 존재 여부·권한이 노출되지 않게 한다.
        if (user.getRole() != User.Role.ADMIN || user.getStatus() != User.Status.ACTIVE) {
            throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
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

    @Transactional
    public void logout(Long userId) {
        // tokenVersion을 올려서, 이미 발급된 access/refresh 토큰을 전부 즉시 무효화한다.
        // 동시 로그아웃 요청이 겹쳐 증가분이 유실되지 않도록 비관적 락으로 조회한다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        user.bumpTokenVersion();
    }
}
