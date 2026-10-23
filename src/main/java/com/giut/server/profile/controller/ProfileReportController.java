package com.giut.server.profile.controller;

import com.giut.server.profile.dto.request.CreateProfileReportRequest;
import com.giut.server.profile.dto.response.ProfileReportResponse;
import com.giut.server.profile.dto.response.PendingProfileReportResponse;
import com.giut.server.profile.service.ProfileReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 조회·신고·공유")
@RestController
@RequiredArgsConstructor
@Validated
public class ProfileReportController {

    private final ProfileReportService profileReportService;

    @PostMapping("/api/profile/{userId}/reports")
    @Operation(summary = "공개 프로필 신고", description = "로그인 사용자가 공개 프로필을 신고합니다. 같은 대상에 대한 본인의 PENDING 신고가 있으면 사유와 관계없이 409로 거부합니다. ACTIONED 또는 DISMISSED 처리 후에는 다시 신고할 수 있습니다. 추가 설명은 생략하거나 null로 보낼 수 있습니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신고 접수 성공"),
            @ApiResponse(responseCode = "400", description = "신고 사유 오류 또는 본인 신고"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "404", description = "공개 프로필을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 검토 중인 신고가 있음")
    })
    public ResponseEntity<ProfileReportResponse> reportPublicProfile(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody CreateProfileReportRequest request,
            Authentication authentication
    ) {
        Long reporterUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileReportService.reportPublicProfile(reporterUserId, userId, request));
    }

    @GetMapping("/api/profile/{userId}/reports/pending")
    @Operation(summary = "내 처리 대기 프로필 신고 조회", description = "해당 대상에 대해 본인이 제출한 PENDING 신고의 사유와 설명을 조회합니다. 없거나 이미 처리됐으면 pending=false, report=null을 반환합니다. 다른 사람의 신고나 관리자 검토 내용은 공개하지 않습니다. 제출 후 대상 프로필이 비공개·휴식·비활성으로 변경돼도 본인의 신고 기록은 확인할 수 있습니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "본인의 PENDING 신고 또는 pending=false, report=null"),
            @ApiResponse(responseCode = "400", description = "잘못된 대상 사용자 ID"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "학교 인증 필요 또는 비활성 사용자")
    })
    public ResponseEntity<PendingProfileReportResponse> getMyPendingReport(
            @PathVariable @Positive Long userId,
            Authentication authentication
    ) {
        Long reporterUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profileReportService.getMyPendingReport(reporterUserId, userId));
    }

}
