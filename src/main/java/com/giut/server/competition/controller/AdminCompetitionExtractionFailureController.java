package com.giut.server.competition.controller;

import com.giut.server.competition.dto.request.CompetitionExtractionFailureSearchRequest;
import com.giut.server.competition.dto.response.CompetitionExtractionFailureListResponse;
import com.giut.server.competition.service.CompetitionExtractionFailureService;
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
@RequestMapping("/api/admin/competitions/extraction-failures")
public class AdminCompetitionExtractionFailureController {

    private final CompetitionExtractionFailureService failureService;

    @GetMapping
    @Operation(summary = "회원 URL 추출 최종 실패 조회",
            description = "AI 추출이 재시도 끝에 최종 실패(FAILED)한 작업을 실패 시각(failedAt) 최신순으로 조회합니다. "
                    + "from(포함)·to(미포함)는 failedAt 기준 ISO-8601 시각이며 errorCode와 AND 조건입니다. "
                    + "errorDetail은 관리자 전용 원인 요약이고 waitingUsers는 결과를 기다리던 회원 수입니다. "
                    + "실패 작업은 30일 동안 보관합니다. page는 0부터, size는 기본 20, 최대 100입니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추출 실패 목록", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompetitionExtractionFailureListResponse.class))),
            @ApiResponse(responseCode = "400", description = "시각 형식, from≥to, 페이지 값 오류"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "현재 활성 관리자 권한 없음")
    })
    public ResponseEntity<CompetitionExtractionFailureListResponse> getFailures(
            @Valid @ParameterObject @ModelAttribute CompetitionExtractionFailureSearchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(failureService.getFailures(Long.valueOf(authentication.getName()), request));
    }
}
