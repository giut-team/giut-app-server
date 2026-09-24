package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.competition.request.CompetitionSearchRequest;
import com.giut.server.dto.competition.response.CompetitionScrapResponse;
import com.giut.server.dto.competition.response.PublicCompetitionDetailResponse;
import com.giut.server.dto.competition.response.PublicCompetitionListResponse;
import com.giut.server.dto.competition.response.PublicCompetitionResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/top5")
    @Operation(summary = "인기 공모전 Top 5 조회", description = "게시된 공모전 중 0.3 × LOG(1 + 조회수) + 0.7 × LOG(1 + 스크랩 수) 점수가 높은 상위 5개를 조회합니다. 점수가 같으면 조회수가 높은 공모전을 우선합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인기 공모전 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicCompetitionResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<java.util.List<PublicCompetitionResponse>> getTop5Competitions() {
        return ResponseEntity.ok(competitionService.getTop5Competitions());
    }

    @GetMapping("/closing-soon")
    @Operation(summary = "마감 임박 공모전 조회", description = "현재 모집 중이며 마감 시각이 조회 시점부터 7일 이내인 게시 공모전을 마감 임박 순으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "마감 임박 공모전 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicCompetitionResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<java.util.List<PublicCompetitionResponse>> getClosingSoonCompetitions() {
        return ResponseEntity.ok(competitionService.getClosingSoonCompetitions());
    }

    @GetMapping("/{competitionId}")
    @Operation(summary = "공모전 상세 조회", description = "게시된 공모전의 상세 정보와 URL을 조회합니다. 조회수는 이 요청마다 1 증가합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공모전 상세 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicCompetitionDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "404", description = "공개된 공모전을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<PublicCompetitionDetailResponse> getCompetition(
            @PathVariable Long competitionId,
            org.springframework.security.core.Authentication authentication
    ) {
        return ResponseEntity.ok(competitionService.getPublishedCompetition(
                Long.valueOf(authentication.getName()),
                competitionId
        ));
    }

    @PostMapping("/{competitionId}/scrap")
    @Operation(summary = "공모전 스크랩 추가", description = "현재 사용자의 공모전 스크랩에 추가합니다. 이미 스크랩한 경우에도 성공으로 처리합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스크랩 추가 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompetitionScrapResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "404", description = "공개된 공모전을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<CompetitionScrapResponse> scrapCompetition(
            @PathVariable Long competitionId,
            org.springframework.security.core.Authentication authentication
    ) {
        return ResponseEntity.ok(competitionService.scrapCompetition(
                Long.valueOf(authentication.getName()),
                competitionId
        ));
    }

    @DeleteMapping("/{competitionId}/scrap")
    @Operation(summary = "공모전 스크랩 삭제", description = "현재 사용자의 공모전 스크랩에서 삭제합니다. 스크랩하지 않은 경우에도 성공으로 처리합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스크랩 삭제 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompetitionScrapResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "404", description = "공개된 공모전을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<CompetitionScrapResponse> removeCompetitionScrap(
            @PathVariable Long competitionId,
            org.springframework.security.core.Authentication authentication
    ) {
        return ResponseEntity.ok(competitionService.removeCompetitionScrap(
                Long.valueOf(authentication.getName()),
                competitionId
        ));
    }
}
