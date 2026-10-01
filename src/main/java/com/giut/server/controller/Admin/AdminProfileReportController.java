package com.giut.server.controller.Admin;

import com.giut.server.dto.profile.request.ReviewProfileReportRequest;
import com.giut.server.dto.profile.response.AdminProfileReportDetailResponse;
import com.giut.server.dto.profile.response.ProfileReportPageResponse;
import com.giut.server.entity.ProfileReport;
import com.giut.server.service.ProfileReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Profile Report", description = "관리자 프로필 신고 검토")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/admin/profile-reports")
public class AdminProfileReportController {

    private final ProfileReportService profileReportService;

    @GetMapping
    @Operation(summary = "프로필 신고 목록 조회", description = "신고 상태로 필터링하고 최신순으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ProfileReportPageResponse> getReports(
            @RequestParam(required = false) ProfileReport.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(profileReportService.getReports(adminUserId, status, page, size));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "프로필 신고 상세 조회")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<AdminProfileReportDetailResponse> getReport(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(profileReportService.getReport(adminUserId, reportId));
    }

    @PatchMapping("/{reportId}/review")
    @Operation(summary = "프로필 신고 검토", description = "PENDING 신고를 ACTIONED 또는 DISMISSED로 처리합니다. 계정 정지 등 실제 조치는 별도로 수행합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 검토 완료"),
            @ApiResponse(responseCode = "400", description = "검토 결과 또는 메모 오류"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @ApiResponse(responseCode = "404", description = "신고를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 신고")
    })
    public ResponseEntity<AdminProfileReportDetailResponse> reviewReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReviewProfileReportRequest request,
            Authentication authentication
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(profileReportService.reviewReport(adminUserId, reportId, request));
    }
}
