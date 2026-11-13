package com.giut.server.competition.controller;

import com.giut.server.competition.dto.request.CompetitionExtractionRequest;
import com.giut.server.competition.dto.request.CompetitionSubmissionRequest;
import com.giut.server.competition.dto.response.CompetitionExtractionJobResponse;
import com.giut.server.competition.dto.response.CompetitionSubmissionResponse;
import com.giut.server.competition.service.AiCompetitionExtractClient.ExtractionUnavailable;
import com.giut.server.competition.service.CompetitionExtractionJobService;
import com.giut.server.competition.service.CompetitionExtractionJobStore.ExtractionQueueSaturated;
import com.giut.server.competition.service.CompetitionSubmissionService;
import com.giut.server.global.dto.ResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Competition Submission")
@SecurityRequirement(name = "JWT")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/competitions")
public class CompetitionSubmissionController {
    private static final String EXTRACTIONS = "/api/competitions/extractions/";

    private final CompetitionSubmissionService submissions;
    private final CompetitionExtractionJobService extractions;

    @PostMapping("/extractions")
    @Operation(summary = "회원 공모전 URL 추출 작업 접수",
            description = "기존 링크면 200과 status=EXISTS(AI 호출 없음)를 반환합니다. 신규 링크는 작업을 저장·commit한 뒤 "
                    + "202와 jobId·Location을 반환합니다. 같은 링크의 진행 중 작업이 있으면 그 작업을 202로, "
                    + "보관 중인 성공 작업이 있으면 200으로 다시 돌려줍니다.")
    public ResponseEntity<CompetitionExtractionJobResponse> requestExtraction(
            @Valid @RequestBody CompetitionExtractionRequest request, Authentication authentication) {
        CompetitionExtractionJobService.Accepted accepted =
                extractions.accept(Long.valueOf(authentication.getName()), request.url());
        CompetitionExtractionJobResponse body = accepted.response();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(body.inProgress() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore());
        if (body.jobId() != null) {
            response.location(URI.create(EXTRACTIONS + body.jobId()));
        }
        return response.body(body);
    }

    @GetMapping("/extractions/{jobId}")
    @Operation(summary = "회원 공모전 URL 추출 작업 조회",
            description = "본인 작업의 상태와 결과를 반환합니다. SUCCEEDED면 result에 초안(draft)과 회원이 보완할 필드(requiredInputs)가 있습니다. "
                    + "다른 사용자의 작업이나 없는 작업은 404입니다.")
    public ResponseEntity<CompetitionExtractionJobResponse> getExtraction(@PathVariable Long jobId,
                                                                          Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(extractions.get(Long.valueOf(authentication.getName()), jobId));
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

    @ExceptionHandler(ExtractionQueueSaturated.class)
    public ResponseEntity<ResultDto> extractionQueueSaturated(ExtractionQueueSaturated exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(ResultDto.builder().success(false).code(503).message(exception.getMessage()).build());
    }

    @ExceptionHandler(ExtractionUnavailable.class)
    public ResponseEntity<ResultDto> extractionUnavailable(ExtractionUnavailable exception) {
        return ResponseEntity.status(exception.status()).body(ResultDto.builder()
                .success(false).code(exception.status().value()).message(exception.getMessage()).build());
    }
}
