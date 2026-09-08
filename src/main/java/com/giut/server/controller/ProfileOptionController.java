package com.giut.server.controller;

import com.giut.server.dto.response.ProfileRoleListResponse;
import com.giut.server.dto.response.ProfileTagListResponse;
import com.giut.server.entity.ProfileTag;
import com.giut.server.service.ProfileOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile Options", description = "프로필 작성 화면의 역할과 태그 선택지")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile-options")
public class ProfileOptionController {

    private final ProfileOptionService profileOptionService;

    @GetMapping("/roles")
    @Operation(summary = "대표 역할별 세부 역할 조회", description = "대표 역할 버튼을 선택했을 때 선택 가능한 세부 역할을 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @Parameter(
            name = "primaryRole",
            in = ParameterIn.QUERY,
            required = true,
            example = "DEVELOPMENT",
            description = "대표 역할 코드: DEVELOPMENT(개발), DESIGN(디자인), PLANNING(기획), MARKETING(마케팅)",
            schema = @Schema(
                    type = "string",
                    allowableValues = {"DEVELOPMENT", "DESIGN", "PLANNING", "MARKETING"}
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "세부 역할 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProfileRoleListResponse.class),
                            examples = @ExampleObject(value = "{\"primaryRole\":\"DEVELOPMENT\",\"primaryRoleName\":\"개발\",\"roles\":[{\"code\":\"BACKEND_DEVELOPER\",\"name\":\"백엔드 개발자\"},{\"code\":\"DATA_ANALYST\",\"name\":\"데이터 분석\"}]}"
                    )
                    )
            )
    })
    public ResponseEntity<ProfileRoleListResponse> getRoles(
            @RequestParam String primaryRole
    ) {
        return ResponseEntity.ok(profileOptionService.getRoles(primaryRole));
    }

    @GetMapping("/tags")
    @Operation(summary = "유형별 프로필 태그 조회", description = "기술 스택, 관심 분야, 활동 경험 선택지를 유형별로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @Parameter(name = "type", in = ParameterIn.QUERY, required = true, example = "SKILL", description = "SKILL, INTEREST, EXPERIENCE 중 하나")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 태그 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProfileTagListResponse.class),
                            examples = @ExampleObject(value = "{\"type\":\"SKILL\",\"tags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\"},{\"id\":2,\"type\":\"SKILL\",\"name\":\"SQL\"}]}"
                    )
                    )
            )
    })
    public ResponseEntity<ProfileTagListResponse> getTags(
            @RequestParam ProfileTag.TagType type
    ) {
        return ResponseEntity.ok(profileOptionService.getTags(type));
    }
}
