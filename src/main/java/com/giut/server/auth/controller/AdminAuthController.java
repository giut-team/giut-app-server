package com.giut.server.auth.controller;

import com.giut.server.auth.dto.request.LoginRequest;
import com.giut.server.auth.dto.request.SignUpRequest;
import com.giut.server.auth.dto.response.LoginResponse;
import com.giut.server.auth.dto.response.SignUpResponse;
import com.giut.server.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "관리자 계정 생성", description = "현재 활성 관리자만 새 관리자 계정을 생성할 수 있습니다. 공개 회원가입 및 최초 관리자 생성 용도로는 사용할 수 없습니다.")
    public ResponseEntity<SignUpResponse> signUp(
            Authentication authentication,
            @Valid @RequestBody SignUpRequest request) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(authService.signUp(adminUserId, request));
    }

    @PostMapping("/login")
    @Operation(summary = "관리자 전용 로그인", description = "활성 상태의 관리자 계정으로 로그인합니다. 정지·삭제된 계정은 로그인할 수 없습니다.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
