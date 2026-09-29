package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.response.PortfolioItemListResponse;
import com.giut.server.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Portfolio", description = "내 프로필 포트폴리오 관리 및 공개 포트폴리오 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class PublicPortfolioController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}/portfolio-items")
    @Operation(
            summary = "공개 포트폴리오 목록 조회",
            description = "공개 프로필에 노출하도록 선택한 포트폴리오만 노출 순서대로 조회합니다. 최대 6개가 반환되며, 숨김 처리된 포트폴리오는 포함되지 않습니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "공개 포트폴리오 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PortfolioItemListResponse.class),
                            examples = @ExampleObject(value = "{\"portfolioItems\":[{\"id\":15,\"imageUrl\":\"https://cdn.giut.com/portfolio/esg.png\",\"title\":\"ESG 캠페인 팀 회의\",\"caption\":\"일정 정리와 회의록 작성을 맡았어요.\",\"projectStartDate\":\"2025-09-01\",\"projectEndDate\":\"2025-12-31\",\"teamSize\":5,\"markdownContent\":\"## 프로젝트 소개\\n\\n회의 일정과 산출물을 관리했습니다.\",\"skillTags\":[{\"id\":1,\"type\":\"SKILL\",\"name\":\"Python\"}],\"showcaseOrder\":1,\"representative\":true}]}")
                    )
            ),
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
    public ResponseEntity<PortfolioItemListResponse> getPublicPortfolioItems(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(userProfileService.getPublicPortfolioItems(userId));
    }
}
