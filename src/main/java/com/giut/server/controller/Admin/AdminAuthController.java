package com.giut.server.controller.Admin;

import com.giut.server.dto.request.LoginRequest;
import com.giut.server.dto.request.SignUpRequest;
import com.giut.server.dto.response.LoginResponse;
import com.giut.server.dto.response.SignUpResponse;
import com.giut.server.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "관리자 전용 회원가입", description = "관리자 전용 계정을 생성합니다.")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.ok(authService.signUp(request));
    }

    @PostMapping("/login")
    @Operation(summary = "관리자 전용 로그인", description = "관리자 전용 계정으로 로그인합니다.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
