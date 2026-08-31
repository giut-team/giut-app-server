package com.giut.server.controller;

import com.giut.server.dto.request.UniversityEmailSendRequest;
import com.giut.server.dto.request.UniversityEmailVerifyRequest;
import com.giut.server.dto.response.UniversityEmailSendResponse;
import com.giut.server.dto.response.UniversityEmailVerifyResponse;
import com.giut.server.service.UniversityEmailVerificationService;
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

@Tag(name = "University Email Verification")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/university-email")
public class UniversityEmailVerificationController {

    private final UniversityEmailVerificationService universityEmailVerificationService;

    @PostMapping("/send")
    @Operation(summary = "학교 이메일 인증코드 발송", description = "서울시립대 이메일(@uos.ac.kr)로 인증코드를 발송합니다.")
    public ResponseEntity<UniversityEmailSendResponse> sendCode(
            Authentication authentication,
            @Valid @RequestBody UniversityEmailSendRequest request
    ) {
        Long memberId = getCurrentMemberId(authentication);
        return ResponseEntity.ok(universityEmailVerificationService.sendCode(memberId, request));
    }

    @PostMapping("/verify")
    @Operation(summary = "학교 이메일 인증코드 확인", description = "인증코드가 일치하면 학교 이메일 인증을 완료합니다.")
    public ResponseEntity<UniversityEmailVerifyResponse> verifyCode(
            Authentication authentication,
            @Valid @RequestBody UniversityEmailVerifyRequest request
    ) {
        Long memberId = getCurrentMemberId(authentication);
        return ResponseEntity.ok(universityEmailVerificationService.verifyCode(memberId, request));
    }

    private Long getCurrentMemberId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }

        return Long.valueOf(authentication.getName());
    }
}
