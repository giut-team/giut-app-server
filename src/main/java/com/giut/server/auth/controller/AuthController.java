package com.giut.server.auth.controller;

import com.giut.server.auth.service.AuthService;
import com.giut.server.auth.service.OAuthCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuthCookieService oAuthCookieService;

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 로그인된 사용자(일반 회원·관리자 공통)의 모든 발급된 토큰을 서버에서 무효화하고 로그인 쿠키를 지웁니다. 다른 기기·브라우저에 남아 있는 토큰도 함께 무효화됩니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        Long userId = Long.valueOf(authentication.getName());
        authService.logout(userId);
        oAuthCookieService.clearLoginCookies(response);
        return ResponseEntity.noContent().build();
    }
}
