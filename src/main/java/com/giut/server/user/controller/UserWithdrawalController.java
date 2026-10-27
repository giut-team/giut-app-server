package com.giut.server.user.controller;

import com.giut.server.auth.service.OAuthCookieService;
import com.giut.server.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
@Tag(name = "User Account")
@SecurityRequirement(name = "JWT")
public class UserWithdrawalController {

    private final UserWithdrawalService service;
    private final OAuthCookieService cookieService;

    @DeleteMapping
    @ApiResponse(responseCode = "204", description = "탈퇴 완료 (응답 본문 없음)", content = @Content)
    @Operation(summary = "회원 탈퇴", description = "로그인한 일반 회원 본인을 DELETED로 변경합니다. 학교 인증 전에도 가능하며 관리자 계정은 403, 보관되지 않은 팀의 팀장은 409입니다. 성공 시 204와 로그인 쿠키 만료 헤더를 반환합니다. 데이터는 보존하며 탈퇴 후 기존 토큰의 인증된 접근과 프로필 노출은 차단합니다. 이미 탈퇴한 계정의 토큰 재사용은 403입니다.")
    public ResponseEntity<Void> withdraw(Authentication authentication, HttpServletResponse response) {
        service.withdraw(Long.valueOf(authentication.getName()));
        cookieService.clearLoginCookies(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
