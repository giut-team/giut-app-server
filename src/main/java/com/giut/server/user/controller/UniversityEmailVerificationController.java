package com.giut.server.user.controller;

import com.giut.server.global.dto.ResultDto;
import com.giut.server.user.dto.request.UniversityEmailSendRequest;
import com.giut.server.user.dto.request.UniversityEmailVerifyRequest;
import com.giut.server.user.dto.response.UniversityEmailSendResponse;
import com.giut.server.user.dto.response.UniversityEmailVerifyResponse;
import com.giut.server.user.dto.response.UniversityEmailVerificationStatusResponse;
import com.giut.server.auth.exception.AuthenticationFailedException;
import com.giut.server.user.service.UniversityEmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "University Email Verification")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/university-email")
public class UniversityEmailVerificationController {

    private final UniversityEmailVerificationService universityEmailVerificationService;

    @GetMapping("/status")
    @Operation(summary = "학교 이메일 인증 상태 조회", description = "인증 전 사용자도 이용할 수 있는 본인의 최소 인증 상태입니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<UniversityEmailVerificationStatusResponse> getVerificationStatus(
            Authentication authentication
    ) {
        Long memberId = getCurrentMemberId(authentication);
        return ResponseEntity.ok(new UniversityEmailVerificationStatusResponse(
                universityEmailVerificationService.isUniversityVerified(memberId)
        ));
    }

    @PostMapping("/send")
    @Operation(summary = "학교 이메일 인증코드 발송", description = "서울시립대 이메일(@uos.ac.kr)로 인증코드를 발송합니다. "
            + "사용자별 최근 30분 동안 첫 발송을 포함해 5회 허용하며 이메일을 바꿔도 합산합니다. "
            + "초과 요청 시점부터 30분간 발송을 차단하고 429와 Retry-After(남은 초)를 반환합니다. "
            + "발송 한도 차단 중에도 기존 코드 인증은 가능하지만, 오답 5회 차단 중에는 인증과 발송 모두 불가합니다. "
            + "차단 중 재요청해도 차단 시간은 연장되지 않습니다. "
            + "메일 전송 시도 실패도 횟수에 포함하며 서버 재시작 시 제한 기록은 초기화됩니다.")
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
                    description = "학교 이메일 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"서울시립대 이메일(@uos.ac.kr)만 사용할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "발송 한도 초과 또는 인증코드 오답 차단: Retry-After 초 후 재요청 가능",
                    headers = @Header(name = "Retry-After", description = "차단 해제까지 남은 초", schema = @Schema(type = "integer", example = "1800")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))
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
    @Operation(summary = "학교 이메일 인증코드 확인", description = "인증코드가 일치하면 학교 이메일 인증을 완료합니다. "
            + "사용자별 최근 30분 동안 오답 5회가 누적되면 모든 대기 코드를 무효화하고 즉시 30분간 인증과 메일 발송을 차단합니다. "
            + "재발급·이메일 변경·관리자 인증 해제로 오답 횟수나 차단이 초기화되지 않습니다. "
            + "정상 인증 성공 시 오답 횟수는 초기화됩니다. 차단 중 요청해도 기간은 연장되지 않습니다. "
            + "유효한 코드가 없거나 만료된 요청·입력 형식 오류는 오답으로 집계하지 않습니다.")
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
            @ApiResponse(responseCode = "429", description = "오답 5회 도달 또는 오답 차단 중",
                    headers = @Header(name = "Retry-After", description = "차단 해제까지 남은 초", schema = @Schema(type = "integer", example = "1800")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
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
            throw new AuthenticationFailedException("인증 정보가 없습니다.");
        }

        return Long.valueOf(authentication.getName());
    }
}
