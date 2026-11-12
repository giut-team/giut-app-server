package com.giut.server.competition.controller;

import com.giut.server.competition.dto.request.CompetitionExtractPreviewRequest;
import com.giut.server.competition.dto.request.CompetitionSubmissionRequest;
import com.giut.server.competition.dto.response.CompetitionExtractPreviewResponse;
import com.giut.server.competition.dto.response.CompetitionSubmissionResponse;
import com.giut.server.competition.service.AiCompetitionExtractClient.ExtractionUnavailable;
import com.giut.server.competition.service.CompetitionSubmissionService;
import com.giut.server.global.dto.ResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Competition Submission")
@SecurityRequirement(name = "JWT")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions")
public class CompetitionSubmissionController {
    private final CompetitionSubmissionService submissions;

    @PostMapping("/extract-preview")
    @Operation(summary = "회원 제출 공모전 추출 미리보기",
            description = "기존 링크는 AI 호출 없이 반환합니다. 신규 링크는 추출 후 입력이 필요한 항목을 반환합니다.")
    public ResponseEntity<CompetitionExtractPreviewResponse> preview(@Valid @RequestBody CompetitionExtractPreviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(submissions.preview(request.url()));
    }

    @PostMapping("/submissions")
    @Operation(summary = "회원 공모전 제출", description = "확인한 등록 정보를 검토 대기 DRAFT로 저장합니다.")
    public ResponseEntity<CompetitionSubmissionResponse> submit(@Valid @RequestBody CompetitionSubmissionRequest request,
                                                                 Authentication authentication) {
        CompetitionSubmissionResponse result = submissions.submit(Long.valueOf(authentication.getName()), request);
        HttpStatus status = switch (result.status()) {
            case SUBMITTED -> HttpStatus.CREATED;
            case EXISTS -> HttpStatus.OK;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(result);
    }

    @ExceptionHandler(ExtractionUnavailable.class)
    public ResponseEntity<ResultDto> extractionUnavailable(ExtractionUnavailable exception) {
        return ResponseEntity.status(exception.status()).body(ResultDto.builder()
                .success(false).code(exception.status().value()).message(exception.getMessage()).build());
    }
}
