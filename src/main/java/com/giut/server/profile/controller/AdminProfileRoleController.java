package com.giut.server.profile.controller;

import com.giut.server.global.dto.ResultDto;
import com.giut.server.profile.dto.response.SeedDefaultProfileRolesResponse;
import com.giut.server.profile.service.DefaultProfileRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Profile Roles", description = "관리자 기본 프로필 역할 등록")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/profile/roles")
public class AdminProfileRoleController {

    private final DefaultProfileRoleService defaultProfileRoleService;

    @PostMapping("/defaults")
    @Operation(
            summary = "기본 세부 역할 일괄 등록",
            description = "활성 관리자만 기본 역할 SQL 목록을 DB에 등록할 수 있습니다. 요청 본문은 없습니다. "
                    + "같은 코드가 있으면 기본 대표 역할·이름·표시 순서로 갱신하며 기존 역할 ID는 유지합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "기본 역할 등록 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SeedDefaultProfileRolesResponse.class),
                            examples = @ExampleObject(value = "{\"totalRoleCount\":15}")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ResultDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ResultDto.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "기본 역할 SQL 읽기 또는 DB 등록 실패",
                    content = @Content(schema = @Schema(implementation = ResultDto.class))
            )
    })
    public ResponseEntity<SeedDefaultProfileRolesResponse> seedDefaults(Authentication authentication) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(defaultProfileRoleService.seedDefaults(adminUserId));
    }
}
