package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.request.PublicProfileSearchRequest;
import com.giut.server.dto.profile.response.PublicProfileDetailResponse;
import com.giut.server.dto.profile.response.PublicProfileListResponse;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 조회·신고·공유")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/profile")
public class PublicProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(
            summary = "전체 공개 프로필 조회",
            description = "프로필 공개가 켜져 있고 현재 활동 상태가 휴식 중이 아닌 사용자 프로필을 페이지 단위로 조회합니다. 대표 역할, 세부 역할, 활동 상태, 학과, 기술 스택으로 필터링할 수 있으며 여러 필터는 AND 조건으로 적용됩니다. page는 0부터 시작하며 페이지당 5개로 고정됩니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "전체 공개 프로필 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublicProfileListResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"profiles\":[{\"userId\":1,\"nickname\":\"김민재\",\"universityVerified\":true,\"profileImageUrl\":\"https://cdn.giut.com/profiles/1.png\",\"activityStatus\":\"LOOKING_FOR_TEAM\",\"activityStatusName\":\"팀 찾는 중\",\"primaryRoles\":[{\"code\":\"DEVELOPMENT\",\"name\":\"개발\"}],\"departmentName\":\"컴퓨터과학부\",\"grade\":3,\"bio\":\"AI로 더 편리한 캠퍼스 서비스를 만들고 싶어요.\",\"skills\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\"}]}],\"page\":0,\"size\":5,\"totalElements\":24,\"totalPages\":5,\"hasNext\":true}"
                            )
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "page, skillTagId 또는 필터 값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"ConstraintViolationException : page는 0 이상이어야 합니다.\",\"code\":400}")
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
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}")
                    )
            )
    })
    public ResponseEntity<PublicProfileListResponse> getPublicProfiles(
            @Valid @ParameterObject @ModelAttribute PublicProfileSearchRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(userProfileService.getPublicProfiles(currentUserId, request));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "공개 프로필 상세 조회", description = "기웃허브 목록에서 선택한 사용자의 역할, 전체 태그, 활동 이력, 공개 포트폴리오를 포함한 상세 프로필을 조회합니다. 공개 포트폴리오는 노출 순서(showcaseOrder) 오름차순으로 반환됩니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "공개 프로필 상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublicProfileDetailResponse.class),
                            examples = @ExampleObject(value = "{\"nickname\":\"김민재\",\"universityVerified\":true,\"profile\":{\"userId\":1,\"departmentName\":\"컴퓨터과학부\",\"grade\":3,\"primaryRoles\":[{\"code\":\"DEVELOPMENT\",\"name\":\"개발\"}],\"activityStatusName\":\"팀 찾는 중\",\"profileImageUrl\":\"https://cdn.giut.com/profiles/1.png\",\"bio\":\"AI로 더 편리한 캠퍼스 서비스를 만들고 싶어요.\",\"searchable\":true,\"roles\":[{\"code\":\"BACKEND_DEVELOPER\",\"name\":\"백엔드 개발자\"}],\"tags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\",\"relatedRoles\":[{\"code\":\"DATA_ANALYST\",\"name\":\"데이터 분석\"}]}],\"portfolioItems\":[{\"id\":1,\"imageUrl\":\"https://cdn.giut.com/portfolio/data-contest.png\",\"title\":\"서울시 데이터 공모전 발표\",\"caption\":\"데이터 정책부터 발표까지 맡았어요.\",\"markdownContent\":\"문제 정의와 데이터 분석, 발표 자료 제작을 담당했습니다.\",\"showcaseOrder\":1,\"representative\":true}],\"activityHistories\":[]}}")
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "400",
                    description = "userId 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"MethodArgumentTypeMismatchException : userId는 숫자여야 합니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "공개 프로필을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 공개 프로필을 찾을 수 없습니다.\",\"code\":404}"))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}"))
            )
    })
    public ResponseEntity<PublicProfileDetailResponse> getPublicProfile(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(userProfileService.getPublicProfile(userId));
    }
}
