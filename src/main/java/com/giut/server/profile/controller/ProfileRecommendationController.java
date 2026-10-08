package com.giut.server.profile.controller;

import com.giut.server.profile.dto.response.ProfileRecommendationResponse;
import com.giut.server.profile.service.ProfileRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "프로필 추천")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/profile/{userId}/recommendations")
@SecurityRequirement(name = "JWT")
public class ProfileRecommendationController {
    private final ProfileRecommendationService recommendationService;

    @PostMapping
    @Operation(summary = "프로필 추천", description = "공개 프로필을 좋아요처럼 추천합니다. 본인 추천은 불가하며 중복 요청은 한 번만 집계합니다. 요청 본문은 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 성공 또는 이미 추천한 프로필",
                    content = @Content(schema = @Schema(implementation = ProfileRecommendationResponse.class),
                            examples = @ExampleObject(value = "{\"userId\":12,\"recommended\":true,\"recommendationCount\":5}"))),
            @ApiResponse(responseCode = "400", description = "본인 추천 또는 잘못된 사용자 ID"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "활동 중인 사용자가 아님"),
            @ApiResponse(responseCode = "404", description = "공개 프로필을 찾을 수 없음")
    })
    public ProfileRecommendationResponse recommend(Authentication authentication, @PathVariable @Positive Long userId) {
        return recommendationService.recommend(Long.valueOf(authentication.getName()), userId);
    }

    @DeleteMapping
    @Operation(summary = "프로필 추천 취소", description = "내가 한 추천만 취소합니다. 반복 취소는 안전하며, 대상 프로필이 비공개가 되어도 취소할 수 있습니다. 요청 본문은 없습니다.")
    @ApiResponse(responseCode = "200", description = "취소 성공",
            content = @Content(schema = @Schema(implementation = ProfileRecommendationResponse.class),
                    examples = @ExampleObject(value = "{\"userId\":12,\"recommended\":false,\"recommendationCount\":4}")))
    public ProfileRecommendationResponse cancelRecommendation(Authentication authentication, @PathVariable @Positive Long userId) {
        return recommendationService.cancelRecommendation(Long.valueOf(authentication.getName()), userId);
    }

    @GetMapping
    @Operation(summary = "프로필 추천 상태 조회", description = "현재 로그인 사용자의 추천 여부와 대상 프로필이 받은 추천 수를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "추천 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = ProfileRecommendationResponse.class),
                    examples = @ExampleObject(value = "{\"userId\":12,\"recommended\":true,\"recommendationCount\":5}")))
    public ProfileRecommendationResponse getRecommendation(Authentication authentication, @PathVariable @Positive Long userId) {
        return recommendationService.getRecommendation(Long.valueOf(authentication.getName()), userId);
    }
}
