package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.request.CreateSkillTagRequest;
import com.giut.server.dto.response.CreateSkillTagResponse;
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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "세부 역할 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProfileRoleListResponse.class),
                            examples = @ExampleObject(value = "{\"primaryRole\":\"DEVELOPMENT\",\"primaryRoleName\":\"개발\",\"roles\":[{\"code\":\"BACKEND_DEVELOPER\",\"name\":\"백엔드 개발자\"},{\"code\":\"DATA_ANALYST\",\"name\":\"데이터 분석\"}]}"
                    )
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "필수 Query Parameter 누락 또는 잘못된 요청 형식",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"MissingServletRequestParameterException : Required request parameter 'primaryRole' for method parameter type String is not present\",\"code\":400}")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "대표 역할 또는 세부 역할을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 존재하지 않는 대표 역할입니다.\",\"code\":404}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}")
                    )
            )
    })
    public ResponseEntity<ProfileRoleListResponse> getRoles(
            @RequestParam String primaryRole
    ) {
        return ResponseEntity.ok(profileOptionService.getRoles(primaryRole));
    }

    @GetMapping("/tags")
    @Operation(summary = "유형별 프로필 태그 조회", description = "기술 스택, 관심 분야, 활동 경험 선택지를 유형별로 조회합니다. 기술 스택은 relatedRoleCodes를 전달하면 해당 세부 역할과 연결된 추천 목록만 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @Parameter(name = "type", in = ParameterIn.QUERY, required = true, example = "SKILL", description = "SKILL, INTEREST, EXPERIENCE 중 하나")
    @Parameter(name = "relatedRoleCodes", in = ParameterIn.QUERY, required = false, example = "DATA_ANALYST,FRONTEND_DEVELOPER", description = "기술 스택 추천에 사용할 세부 역할 코드. 쉼표로 여러 개를 전달할 수 있습니다.")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 태그 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProfileTagListResponse.class),
                            examples = @ExampleObject(value = "{\"type\":\"SKILL\",\"tags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\",\"relatedRoles\":[{\"code\":\"DATA_ANALYST\",\"name\":\"데이터 분석\"}]},{\"id\":4,\"type\":\"SKILL\",\"name\":\"React\",\"relatedRoles\":[{\"code\":\"FRONTEND_DEVELOPER\",\"name\":\"프론트엔드 개발자\"}]}]}"
                    )
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "필수 Query Parameter 누락 또는 지원하지 않는 태그 유형",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"MethodArgumentTypeMismatchException : Failed to convert value\",\"code\":400}")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청한 유형의 프로필 태그가 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 등록된 프로필 태그가 없습니다.\",\"code\":404}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}")
                    )
            )
    })
    public ResponseEntity<ProfileTagListResponse> getTags(
            @RequestParam ProfileTag.TagType type,
            @RequestParam(required = false) List<String> relatedRoleCodes
    ) {
        return ResponseEntity.ok(profileOptionService.getTags(
                type,
                relatedRoleCodes == null ? List.of() : relatedRoleCodes
        ));
    }

    @PostMapping("/tags/skills")
    @Operation(summary = "직접 입력 기술 스택 추가", description = "기존 기술 스택에 없던 항목을 SKILL 태그로 추가합니다. 동일한 기술 스택이 있으면 기존 태그를 반환합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "201",
                    description = "기술 스택 신규 추가 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateSkillTagResponse.class), examples = @ExampleObject(value = "{\"created\":true,\"skill\":{\"id\":36,\"type\":\"SKILL\",\"name\":\"Docker\",\"relatedRoles\":[{\"code\":\"BACKEND_DEVELOPER\",\"name\":\"백엔드 개발자\"}]}}"))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "이미 존재하는 기술 스택 반환",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateSkillTagResponse.class), examples = @ExampleObject(value = "{\"created\":false,\"skill\":{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\",\"relatedRoles\":[{\"code\":\"DATA_ANALYST\",\"name\":\"데이터 분석\"}]}}"))
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "기술 스택 이름 누락 또는 길이 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"기술 스택 이름은 필수입니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}"))
            )
    })
    public ResponseEntity<CreateSkillTagResponse> createSkill(
            @Valid @RequestBody CreateSkillTagRequest request
    ) {
        CreateSkillTagResponse response = profileOptionService.createSkill(request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }
}
