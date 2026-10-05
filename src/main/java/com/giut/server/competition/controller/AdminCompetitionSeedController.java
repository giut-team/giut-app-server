package com.giut.server.competition.controller;

import com.giut.server.competition.dto.response.SeedSampleCompetitionsResponse;
import com.giut.server.competition.service.CompetitionSeedService;
import com.giut.server.global.dto.ResultDto;
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

@Tag(name = "Admin Competition", description = "관리자 공모전 등록 및 수정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/competitions")
public class AdminCompetitionSeedController {

    private final CompetitionSeedService competitionSeedService;

    @PostMapping("/samples")
    @Operation(
            summary = "공모전 샘플 10개 일괄 등록",
            description = "활성 관리자가 실제 공모전 공고를 참고한 고정 샘플 10개를 등록합니다. 요청 본문은 없습니다. "
                    + "신규 데이터는 PUBLISHED 상태이며 소개에 테스트 데이터라고 표시합니다. "
                    + "같은 출처 URL이 이미 있으면 기존 데이터를 유지하고 건너뜁니다. "
                    + "일부 일정은 진행·제출 기간을 테스트용 모집 기간으로 사용합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "샘플 등록 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SeedSampleCompetitionsResponse.class),
                            examples = @ExampleObject(value = "{\"createdCount\":10,\"skippedCount\":0,\"competitionIds\":[1,2,3,4,5,6,7,8,9,10]}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ResultDto.class))
            ),
            @ApiResponse(responseCode = "409", description = "등록 도중 공모전 URL 중복 발견"),
            @ApiResponse(responseCode = "500", description = "DB 등록 실패")
    })
    public ResponseEntity<SeedSampleCompetitionsResponse> seedSamples(Authentication authentication) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(competitionSeedService.seedSamples(adminUserId));
    }
}

