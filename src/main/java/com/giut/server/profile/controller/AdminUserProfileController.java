package com.giut.server.profile.controller;

import com.giut.server.global.dto.ResultDto;
import com.giut.server.profile.dto.request.UpdateDepartmentRequest;
import com.giut.server.profile.dto.response.DepartmentResponse;
import com.giut.server.profile.service.AdminUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin User Profile", description = "관리자 전용 프로필 관리")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/admin/user-profiles")
public class AdminUserProfileController {

    private final AdminUserProfileService adminUserProfileService;

    @PatchMapping("/{userId}/department")
    @Operation(summary = "사용자 학과 변경", description = "활성 관리자만 기존 프로필의 학과를 변경할 수 있습니다. 일반 사용자는 내 프로필 수정으로 학과를 변경할 수 없습니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "학과 변경 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentResponse.class), examples = @ExampleObject(value = "{\"code\":\"COMPUTER_SCIENCE\",\"name\":\"컴퓨터과학부\"}"))),
            @ApiResponse(responseCode = "400", description = "학과 누락, 지원하지 않는 학과 또는 유효하지 않은 사용자 ID", content = @Content(schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "활성 관리자 권한 필요"),
            @ApiResponse(responseCode = "404", description = "변경할 프로필 없음")
    })
    public ResponseEntity<DepartmentResponse> updateDepartment(
            Authentication authentication,
            @PathVariable @Positive Long userId,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(adminUserProfileService.updateDepartment(adminUserId, userId, request));
    }
}
