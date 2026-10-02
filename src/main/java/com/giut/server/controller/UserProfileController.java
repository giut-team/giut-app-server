package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.request.PutMyProfileRequest;
import com.giut.server.dto.profile.request.UpdateMyProfileRequest;
import com.giut.server.dto.profile.response.MyProfileResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/userprofile")
public class UserProfileController {

    private static final String MY_PROFILE_GET_EXAMPLE = """
            {
              "profileCompleted": true,
              "profile": {
                "userId": 12,
                "nickname": "김민재",
                "departmentName": "컴퓨터과학부",
                "grade": 3,
                "primaryRoles": [{"code": "DEVELOPMENT", "name": "개발"}],
                "activityStatusName": "팀 찾는 중",
                "profileImageUrl": "https://cdn.giut.com/profiles/12.png",
                "bio": "백엔드와 AI 프로젝트에 관심이 있습니다.",
                "searchable": true,
                "roles": [{"code": "BACKEND_DEVELOPER", "name": "백엔드 개발자"}],
                "tags": [{"id": 1, "type": "SKILL", "name": "Python"}],
                "portfolioItems": [{
                  "id": 15,
                  "imageUrl": "https://cdn.giut.com/portfolio/15.png",
                  "title": "서울시 데이터 공모전 발표",
                  "caption": "데이터 분석과 발표를 담당했어요.",
                  "projectStartDate": "2026-03-01",
                  "projectEndDate": "2026-06-30",
                  "teamSize": 4,
                  "roles": [
                    {"code": "BACKEND_DEVELOPER", "name": "백엔드 개발자"},
                    {"code": "DATA_ANALYST", "name": "데이터 분석"}
                  ],
                  "markdownContent": "## 프로젝트 소개",
                  "skillTags": [{"id": 1, "type": "SKILL", "name": "Python"}],
                  "showcaseOrder": 1,
                  "representative": true
                }],
                "activityHistories": [{
                  "id": 1,
                  "category": "AWARD",
                  "categoryName": "수상",
                  "title": "서울시 데이터 활용 공모전 우수상",
                  "organization": "서울특별시",
                  "startMonth": "2025-03",
                  "endMonth": "2025-06"
                }]
              },
              "summary": {
                "portfolioCount": 1,
                "showcaseCount": 1,
                "myTeamCount": 2,
                "scrapCount": 8,
                "competitionScrapCount": 5,
                "teamScrapCount": 3
              }
            }
            """;

    private static final String MY_PROFILE_CREATE_EXAMPLE = """
            {
              "profileCompleted": true,
              "profile": {
                "userId": 12,
                "nickname": "김민재",
                "departmentName": "컴퓨터과학부",
                "grade": 3,
                "primaryRoles": [{"code": "DEVELOPMENT", "name": "개발"}],
                "activityStatusName": "팀 찾는 중",
                "profileImageUrl": "https://cdn.giut.com/profiles/12.png",
                "bio": "백엔드와 AI 프로젝트에 관심이 있습니다.",
                "searchable": true,
                "roles": [{"code": "BACKEND_DEVELOPER", "name": "백엔드 개발자"}],
                "tags": [{"id": 1, "type": "SKILL", "name": "Java"}],
                "portfolioItems": [{
                  "id": 15,
                  "imageUrl": "https://cdn.giut.com/portfolio/15.png",
                  "title": "서울시 데이터 공모전 발표",
                  "caption": "데이터 분석과 발표를 담당했어요.",
                  "projectStartDate": "2026-03-01",
                  "projectEndDate": "2026-06-30",
                  "teamSize": 4,
                  "roles": [{"code": "BACKEND_DEVELOPER", "name": "백엔드 개발자"}],
                  "markdownContent": "## 프로젝트 소개",
                  "skillTags": [{"id": 1, "type": "SKILL", "name": "Python"}],
                  "showcaseOrder": 1,
                  "representative": true
                }],
                "activityHistories": [{
                  "id": 1,
                  "category": "AWARD",
                  "categoryName": "수상",
                  "title": "서울시 데이터 활용 공모전 우수상",
                  "organization": "서울특별시",
                  "startMonth": "2025-03",
                  "endMonth": "2025-06"
                }]
              },
              "summary": {
                "portfolioCount": 1,
                "showcaseCount": 1,
                "myTeamCount": 0,
                "scrapCount": 0,
                "competitionScrapCount": 0,
                "teamScrapCount": 0
              }
            }
            """;

    private static final String PROFILE_WRITE_REQUEST_EXAMPLE = """
            {
              "department": "컴퓨터과학부",
              "primaryRoles": ["DEVELOPMENT", "PLANNING"],
              "roles": ["BACKEND_DEVELOPER", "DATA_ANALYST"],
              "activityStatus": "LOOKING_FOR_TEAM",
              "grade": 3,
              "profileImageUrl": "https://cdn.giut.com/profiles/12.png",
              "bio": "백엔드와 AI 프로젝트에 관심이 있습니다.",
              "searchable": true,
              "skillTagIds": [1, 4],
              "interestTagIds": [21, 25],
              "experienceTagIds": [31],
              "activityHistories": [{
                "category": "AWARD",
                "title": "서울시 데이터 활용 공모전 우수상",
                "organization": "서울특별시",
                "startMonth": "2025-03",
                "endMonth": "2025-06"
              }]
            }
            """;

    private static final String PROFILE_UPDATE_REQUEST_EXAMPLE = """
            {
              "departmentname": "컴퓨터과학부",
              "primaryRoles": ["DEVELOPMENT", "PLANNING"],
              "roles": ["BACKEND_DEVELOPER", "DATA_ANALYST"],
              "activityStatus": "LOOKING_FOR_TEAM",
              "grade": 3,
              "profileImageUrl": "https://cdn.giut.com/profiles/12.png",
              "bio": "백엔드와 AI 프로젝트에 관심이 있습니다.",
              "searchable": true,
              "skillTagIds": [1, 4],
              "interestTagIds": [21, 25],
              "experienceTagIds": [31],
              "activityHistories": [{
                "category": "AWARD",
                "title": "서울시 데이터 활용 공모전 우수상",
                "organization": "서울특별시",
                "startMonth": "2025-03",
                "endMonth": "2025-06"
              }]
            }
            """;

    private final UserProfileService userProfileService;

    @GetMapping("/me/profile")
    @Operation(summary = "내 프로필 조회", description = "프로필·포트폴리오와 마이페이지 요약 개수를 함께 조회합니다.")
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
                                            value = MY_PROFILE_GET_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "프로필 미등록",
                                            value = "{\"profileCompleted\":false,\"profile\":null,\"summary\":{\"portfolioCount\":0,\"showcaseCount\":0,\"myTeamCount\":0,\"scrapCount\":0,\"competitionScrapCount\":0,\"teamScrapCount\":0}}"
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

    @PostMapping("/me/profile")
    @Operation(
            summary = "내 프로필 최초 생성",
            description = "로그인 사용자의 nickname을 이름으로 사용해 프로필을 생성합니다. 성별은 입력받지 않습니다. 활동 이력은 생략하거나 빈 배열로 보낼 수 있으며, 포트폴리오는 전용 API로 등록합니다. 응답 예시는 배열 필드의 형태를 보여주기 위한 것으로, 최초 생성 직후 portfolioItems는 빈 배열입니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PutMyProfileRequest.class),
                            examples = @ExampleObject(value = PROFILE_WRITE_REQUEST_EXAMPLE)
                    )
            )
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "201",
                    description = "프로필 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyProfileResponse.class),
                            examples = @ExampleObject(
                                    name = "프로필 생성 응답 형식 예시 (포트폴리오는 별도 등록)",
                                    value = MY_PROFILE_CREATE_EXAMPLE
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
                    responseCode = "409",
                    description = "이미 프로필이 등록됨",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"ConflictException : 이미 프로필이 등록되어 있습니다.\",\"code\":409}"))
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
    public ResponseEntity<MyProfileResponse> createMyProfile(
            Authentication authentication,
            @Valid @RequestBody PutMyProfileRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.createMyProfile(userId, request));
    }

    @PutMapping("/me/profile")
    @Operation(
            summary = "내 프로필 전체 수정",
            description = "프로필 기본 정보·역할·태그·활동 이력 목록을 전체 교체합니다. 이름과 성별은 기존 값을 유지합니다. 활동 이력의 기존 ID는 유지되지 않습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateMyProfileRequest.class),
                            examples = @ExampleObject(value = PROFILE_UPDATE_REQUEST_EXAMPLE)
                    )
            )
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MyProfileResponse.class), examples = @ExampleObject(value = MY_PROFILE_GET_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청값 또는 태그/역할 조합", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "수정할 프로필을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<MyProfileResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(userProfileService.updateMyProfile(userId, request));
    }
}
