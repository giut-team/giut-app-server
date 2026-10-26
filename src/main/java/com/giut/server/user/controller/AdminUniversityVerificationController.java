package com.giut.server.user.controller;

import com.giut.server.user.service.UniversityEmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin User", description = "관리자 전용 사용자 관리")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/admin/users")
public class AdminUniversityVerificationController {

    private final UniversityEmailVerificationService universityEmailVerificationService;

    @DeleteMapping("/{userId}/university-verification")
    @Operation(
            summary = "사용자 학교 인증 해제",
            description = "현재 활성 관리자만 사용자의 학교 이메일과 인증 시각을 초기화합니다. "
                    + "현재 서버에 남은 해당 사용자의 인증 코드도 무효화합니다. 이미 미인증이어도 204를 반환합니다. "
                    + "로그인 계정, 프로필, 팀 등 기존 데이터와 권한은 유지합니다. "
                    + "일반 사용자는 이후 보호 API 요청부터 학교 미인증 제한을 받습니다. "
                    + "계정 정지나 재인증 금지는 아니며 새 인증 코드를 통한 재인증은 가능합니다. "
                    + "관리자 계정의 기존 학교 인증 예외도 유지합니다. 요청 본문은 없습니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "인증 해제 완료 또는 이미 미인증"),
            @ApiResponse(responseCode = "400", description = "사용자 ID는 양수여야 함"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "현재 활성 관리자 권한 필요"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    public ResponseEntity<Void> revokeUniversityVerification(
            Authentication authentication,
            @PathVariable @Positive Long userId
    ) {
        universityEmailVerificationService.revokeUniversityVerification(
                Long.valueOf(authentication.getName()), userId);
        return ResponseEntity.noContent().build();
    }
}
