package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.request.PutMyProfileRequest;
import com.giut.server.dto.profile.response.MyProfileResponse;
import com.giut.server.dto.profile.response.MyProfileSaveResponse;
import com.giut.server.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/userprofile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me/profile")
    @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필과 프로필 등록 여부를 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "내 프로필 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyProfileResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "프로필 등록 완료",
                                            value = "{\"profileCompleted\":true,\"profile\":{\"userId\":12,\"department\":\"COMPUTER_SCIENCE\",\"departmentName\":\"컴퓨터과학부\",\"grade\":3,\"gender\":\"MALE\",\"primaryRoles\":[{\"code\":\"DEVELOPMENT\",\"name\":\"개발\"},{\"code\":\"PLANNING\",\"name\":\"기획\"}],\"activityStatus\":\"LOOKING_FOR_TEAM\",\"activityStatusName\":\"팀 찾는 중\",\"profileImageUrl\":\"https://cdn.giut.com/profiles/12.png\",\"bio\":\"백엔드와 AI 프로젝트에 관심이 있습니다.\",\"searchable\":true,\"roles\":[{\"code\":\"BACKEND_DEVELOPER\",\"name\":\"백엔드 개발자\"},{\"code\":\"SERVICE_PLANNER\",\"name\":\"서비스 기획\"}],\"tags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\"},{\"id\":2,\"type\":\"SKILL\",\"name\":\"SQL\"}],\"links\":[{\"type\":\"GITHUB\",\"typeName\":\"GitHub\",\"url\":\"https://github.com/giut\",\"title\":\"GitHub\"}]}}"
                                    ),
                                    @ExampleObject(
                                            name = "프로필 미등록",
                                            value = "{\"profileCompleted\":false,\"profile\":null}"
                                    )
                            }
                    )
            ),
            // 실패 응답
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
    public ResponseEntity<MyProfileResponse> getMyProfile(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());

        return ResponseEntity.ok(userProfileService.getMyProfile(userId));
    }

    @PutMapping("/me/profile")
    @Operation(
            summary = "내 프로필 등록 또는 전체 수정",
            description = "프로필이 없으면 생성하고 201을 반환합니다. 이미 존재하면 전달한 값으로 전체 수정하고 200을 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "201",
                    description = "프로필 최초 등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyProfileResponse.class),
                            examples = @ExampleObject(
                                    name = "프로필 생성 성공",
                                    value = "{\"profileCompleted\":true,\"profile\":{\"userId\":12,\"department\":\"COMPUTER_SCIENCE\",\"departmentName\":\"컴퓨터과학부\",\"grade\":3,\"gender\":\"MALE\",\"primaryRoles\":[{\"code\":\"DEVELOPMENT\",\"name\":\"개발\"},{\"code\":\"PLANNING\",\"name\":\"기획\"}],\"activityStatus\":\"LOOKING_FOR_TEAM\",\"activityStatusName\":\"팀 찾는 중\",\"profileImageUrl\":\"https://cdn.giut.com/profiles/12.png\",\"bio\":\"백엔드와 AI 프로젝트에 관심이 있습니다.\",\"searchable\":true,\"roles\":[{\"code\":\"BACKEND_DEVELOPER\",\"name\":\"백엔드 개발자\"},{\"code\":\"SERVICE_PLANNER\",\"name\":\"서비스 기획\"}],\"tags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Java\"}],\"links\":[{\"type\":\"GITHUB\",\"typeName\":\"GitHub\",\"url\":\"https://github.com/giut\",\"title\":\"GitHub\"}]}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 전체 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyProfileResponse.class),
                            examples = @ExampleObject(
                                    name = "프로필 수정 성공",
                                    value = "{\"profileCompleted\":true,\"profile\":{\"userId\":12,\"department\":\"COMPUTER_SCIENCE\",\"departmentName\":\"컴퓨터과학부\",\"grade\":4,\"gender\":\"MALE\",\"primaryRoles\":[{\"code\":\"DEVELOPMENT\",\"name\":\"개발\"},{\"code\":\"PLANNING\",\"name\":\"기획\"}],\"activityStatus\":\"OPEN_TO_OFFERS\",\"activityStatusName\":\"프로필 쓰는 중\",\"profileImageUrl\":null,\"bio\":\"백엔드 공모전 팀원을 찾고 있습니다.\",\"searchable\":false,\"roles\":[{\"code\":\"DATA_ANALYST\",\"name\":\"데이터 분석\"},{\"code\":\"PROJECT_MANAGER\",\"name\":\"프로젝트 매니저\"}],\"tags\":[],\"links\":[]}}"
                            )
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(
                                    name = "인증 실패",
                                    value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청값 또는 태그/역할 조합",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(
                                    name = "잘못된 요청",
                                    value = "{\"success\":false,\"message\":\"IllegalArgumentException : 세부 역할은 대표 역할과 같은 분야에서 선택해야 합니다.\",\"code\":400}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(
                                    name = "서버 오류",
                                    value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}"
                            )
                    )
            )
    })
    public ResponseEntity<MyProfileResponse> saveMyProfile(
            Authentication authentication,
            @Valid @RequestBody PutMyProfileRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        MyProfileSaveResponse result = userProfileService.saveMyProfile(userId, request);

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        }

        return ResponseEntity.ok(result.response());
    }
}
