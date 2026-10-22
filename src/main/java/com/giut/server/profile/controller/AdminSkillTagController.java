package com.giut.server.profile.controller;

import com.giut.server.global.dto.ResultDto;
import com.giut.server.profile.dto.response.SeedDefaultProfileTagsResponse;
import com.giut.server.profile.service.DefaultProfileTagService;
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

@Tag(name = "Admin Profile Tags", description = "관리자 기본 기술 스택·관심 분야·활동 경험 등록")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/profile/tags")
public class AdminSkillTagController {

    private final DefaultProfileTagService defaultProfileTagService;

    @PostMapping({"/defaults", "/skills/defaults"})
    @Operation(
            summary = "기본 프로필 태그 53개 일괄 등록",
            description = "활성 관리자가 기술 스택 40개, 관심 분야(INTEREST) 5개, 활동 경험(EXPERIENCE) 8개와 기술의 역할 연결을 함께 등록합니다. 요청 본문은 없습니다. "
                    + "기본 역할을 먼저 등록해야 합니다. 같은 기술 이름은 대소문자를 구분하지 않고 기존 ID를 재사용하며, "
                    + "누락된 역할 연결만 추가합니다. 관심 분야와 활동 경험도 유형·정규화 이름 기준으로 기존 ID를 재사용하며 기존 이름을 유지합니다. "
                    + "기존 /skills/defaults 경로에서도 세 유형을 함께 등록합니다. createdCount/existingCount/skillTagIds는 SKILL 결과이며 다른 유형은 interestTags/experienceTags에 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "세 유형의 기본 프로필 태그 등록 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SeedDefaultProfileTagsResponse.class),
                            examples = @ExampleObject(value = "{\"createdCount\":0,\"existingCount\":40,\"skillTagIds\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40],\"interestTags\":{\"createdCount\":5,\"existingCount\":0,\"tagIds\":[41,42,43,44,45]},\"experienceTags\":{\"createdCount\":8,\"existingCount\":0,\"tagIds\":[46,47,48,49,50,51,52,53]}}")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "기본 세부 역할 미등록",
                    content = @Content(schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "활성 관리자 권한 필요"),
            @ApiResponse(responseCode = "500", description = "DB 등록 실패")
    })
    public ResponseEntity<SeedDefaultProfileTagsResponse> seedDefaults(Authentication authentication) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(defaultProfileTagService.seedDefaults(adminUserId));
    }
}

