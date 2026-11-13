package com.giut.server.competition.controller;

import com.giut.server.competition.dto.request.CompetitionIngestionFailureSearchRequest;
import com.giut.server.competition.dto.request.CompetitionIngestionFailureSummaryRequest;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureListResponse;
import com.giut.server.competition.dto.response.CompetitionIngestionFailureSummaryResponse;
import com.giut.server.competition.service.CompetitionIngestionFailureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Competition", description = "관리자 공모전 조회·등록·수정·삭제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/competitions/ingestion-failures")
public class AdminCompetitionIngestionFailureController {

    private final CompetitionIngestionFailureService failureService;

    @GetMapping
    @Operation(summary = "AI 배치 등록 실패 기록 조회",
            description = "AI 배치 등록에서 저장하지 못한 공고를 데이터가 들어온 시각(receivedAt) 최신순으로 조회합니다. "
                    + "from(포함)·to(미포함)는 receivedAt 기준 ISO-8601 시각이며 failureType, batchId와 AND 조건입니다. "
                    + "rawPayload는 AI 서버가 보낸 해당 공고의 원본 JSON입니다. page는 0부터, size는 기본 20, 최대 100입니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실패 기록 목록", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompetitionIngestionFailureListResponse.class))),
            @ApiResponse(responseCode = "400", description = "시각 형식, from≥to, 페이지 또는 실패 유형 값 오류"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "현재 활성 관리자 권한 없음")
    })
    public ResponseEntity<CompetitionIngestionFailureListResponse> getFailures(
            @Valid @ParameterObject @ModelAttribute CompetitionIngestionFailureSearchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(failureService.getFailures(Long.valueOf(authentication.getName()), request));
    }

    @GetMapping("/summary")
    @Operation(summary = "AI 배치 등록 실패 유형별 건수",
            description = "기간 안에 기록된 실패를 유형별(byType)로 셉니다. 중복(DUPLICATE)이 얼마나 들어오는지 볼 때 씁니다. "
                    + "from(포함)·to(미포함)는 데이터가 들어온 시각(receivedAt) 기준 ISO-8601이며 생략하면 제한하지 않습니다. "
                    + "생성에 성공한 건수는 기록하지 않으므로 포함되지 않고, 배치별 생성·실패·중복 건수는 서버 로그 AI_INGESTION_BATCH에 있습니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유형별 건수", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompetitionIngestionFailureSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "시각 형식 또는 from≥to 오류"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "현재 활성 관리자 권한 없음")
    })
    public ResponseEntity<CompetitionIngestionFailureSummaryResponse> getSummary(
            @Valid @ParameterObject @ModelAttribute CompetitionIngestionFailureSummaryRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(failureService.getSummary(Long.valueOf(authentication.getName()), request));
    }
}
