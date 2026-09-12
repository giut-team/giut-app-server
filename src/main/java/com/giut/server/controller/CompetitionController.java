package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.competition.request.CompetitionSearchRequest;
import com.giut.server.dto.competition.response.PublicCompetitionListResponse;
import com.giut.server.service.CompetitionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Competition", description = "공개 공모전 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;

    @GetMapping
    @Operation(
            summary = "전체 공모전 조회",
            description = "게시된 공모전을 페이지 단위로 조회합니다. 키워드, 카테고리, 모집 상태는 모두 선택 필터이며 함께 보내면 AND 조건으로 적용됩니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "공모전 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PublicCompetitionListResponse.class),
                            examples = @ExampleObject(value = "{\"competitions\":[{\"id\":1,\"title\":\"서울시 데이터 분석 공모전\",\"category\":\"WEB_MOBILE_IT\",\"categoryName\":\"웹/모바일/IT\",\"hostOrganization\":\"서울특별시\",\"summary\":\"공공 데이터를 활용한 서비스 아이디어 공모전입니다.\",\"applicationStartAt\":\"2026-09-15T00:00:00Z\",\"applicationEndAt\":\"2026-10-15T14:59:59Z\",\"recruitmentStatus\":\"OPEN\",\"recruitmentStatusName\":\"모집 중\",\"primaryUrl\":\"https://example.com/recruitment\"}],\"page\":0,\"size\":10,\"totalElements\":1,\"totalPages\":1,\"hasNext\":false}")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "page, size 또는 필터 값 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"size는 20 이하여야 합니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<PublicCompetitionListResponse> getCompetitions(
            @Valid @ParameterObject @ModelAttribute CompetitionSearchRequest request
    ) {
        return ResponseEntity.ok(competitionService.getPublishedCompetitions(request));
    }
}
