package com.giut.server.auth.controller;

import com.giut.server.auth.service.AuthService;
import com.giut.server.auth.service.OAuthCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
    @Operation(summary = "로그아웃", description = "현재 로그인된 사용자(일반 회원·관리자 공통)의 모든 발급된 토큰을 서버에서 무효화하고 로그인 쿠키를 지웁니다. 다른 기기·브라우저에 남아 있는 토큰도 함께 무효화됩니다. "
            + "토큰이 없거나 이미 폐기·탈퇴된 토큰이면 무효화할 토큰 없이 로그인 쿠키만 지우고 204를 반환합니다. 보호 API의 403 응답은 쿠키를 지우지 않으므로, 쓸 수 없는 쿠키는 이 API로 정리합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        // 폐기된 토큰은 필터가 익명으로 바꿔 보낸다. 이때는 무효화할 토큰이 없으므로 쿠키만 지운다.
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            authService.logout(Long.valueOf(authentication.getName()));
        }
        oAuthCookieService.clearLoginCookies(response);
        return ResponseEntity.noContent().build();
    }
}
