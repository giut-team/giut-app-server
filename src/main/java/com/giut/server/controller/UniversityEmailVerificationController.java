package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.university.request.UniversityEmailSendRequest;
import com.giut.server.dto.university.request.UniversityEmailVerifyRequest;
import com.giut.server.dto.university.response.UniversityEmailSendResponse;
import com.giut.server.dto.university.response.UniversityEmailVerifyResponse;
import com.giut.server.service.UniversityEmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "인증코드 발송 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UniversityEmailSendResponse.class),
                            examples = @ExampleObject(value = "{\"universityEmail\":\"user@uos.ac.kr\",\"expiresAt\":\"2026-09-08T14:00:00\"}")
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "학교 이메일 형식 오류 또는 이미 사용 중인 이메일",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"서울시립대 이메일(@uos.ac.kr)만 사용할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류 또는 이메일 발송 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}"))
            )
    })
    public ResponseEntity<UniversityEmailSendResponse> sendCode(
            Authentication authentication,
            @Valid @RequestBody UniversityEmailSendRequest request
    ) {
        Long memberId = getCurrentMemberId(authentication);
        return ResponseEntity.ok(universityEmailVerificationService.sendCode(memberId, request));
    }

    @PostMapping("/verify")
    @Operation(summary = "학교 이메일 인증코드 확인", description = "인증코드가 일치하면 학교 이메일 인증을 완료합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "학교 이메일 인증 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UniversityEmailVerifyResponse.class),
                            examples = @ExampleObject(value = "{\"userId\":12,\"universityEmail\":\"user@uos.ac.kr\",\"universityVerifiedAt\":\"2026-09-08T13:55:00\"}")
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "인증코드 오류, 만료 또는 학교 이메일 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증코드가 일치하지 않습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "인증할 사용자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 회원을 찾을 수 없습니다.\",\"code\":404}"))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}"))
            )
    })
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
