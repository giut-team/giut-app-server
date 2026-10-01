package com.giut.server.controller;

import com.giut.server.dto.profile.request.CreateProfileReportRequest;
import com.giut.server.dto.profile.response.ProfileReportResponse;
import com.giut.server.service.ProfileReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 조회·신고·공유")
@RestController
@RequiredArgsConstructor
public class ProfileReportController {

    private final ProfileReportService profileReportService;

    @PostMapping("/api/profile/{userId}/reports")
    @Operation(summary = "공개 프로필 신고", description = "로그인 사용자가 공개 프로필을 신고합니다. 추가 설명은 생략하거나 null로 보낼 수 있습니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공"),
            @ApiResponse(responseCode = "400", description = "신고 사유 오류 또는 본인 신고"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "404", description = "공개 프로필을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 검토 중인 신고가 있음")
    })
    public ResponseEntity<ProfileReportResponse> reportPublicProfile(
            @PathVariable Long userId,
            @Valid @RequestBody CreateProfileReportRequest request,
            Authentication authentication
    ) {
        Long reporterUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileReportService.reportPublicProfile(reporterUserId, userId, request));
    }

}
