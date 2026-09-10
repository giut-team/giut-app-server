package com.giut.server.controller;

import com.giut.server.dto.auth.request.KakaoLoginRequest;
import com.giut.server.dto.auth.response.LoginResponse;
import com.giut.server.service.KakaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Kakao Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/kakao")
public class KakaoController {

    private final KakaoService kakaoService;

    @GetMapping("/authorization")
    @Operation(summary = "카카오 인가 페이지로 이동", description = "프론트엔드가 카카오 로그인 화면으로 이동할 때 사용합니다.")
    public ResponseEntity<Void> redirectToKakaoAuthorization() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(kakaoService.getAuthorizationUrl()))
                .build();
    }

    @PostMapping("/login")
    @Operation(summary = "카카오 로그인", description = "프론트엔드 Redirect URI가 받은 인가 코드로 로그인하고 자체 JWT를 발급합니다.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(kakaoService.login(request.getCode()));
    }
}
