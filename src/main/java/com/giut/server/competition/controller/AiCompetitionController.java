package com.giut.server.competition.controller;

import com.giut.server.competition.dto.request.AiCompetitionRequest;
import com.giut.server.competition.dto.request.AiCompetitionBatchRequest;
import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.service.AiCompetitionBatchService;
import com.giut.server.competition.service.CompetitionService;
import com.giut.server.global.dto.ResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/integrations/competitions")
@RequiredArgsConstructor
@Tag(name = "AI Competition Ingestion")
public class AiCompetitionController {
    private static final Logger logger = LoggerFactory.getLogger(AiCompetitionController.class);

    private final CompetitionService competitionService;
    private final AiCompetitionBatchService aiCompetitionBatchService;

    @PostMapping
    @Operation(summary = "AI 검증 공모전 등록", description = "X-AI-API-Key 인증. 수신 즉시 PUBLISHED, AUTO_VERIFIED로 등록합니다.",
            parameters = @Parameter(name = "X-AI-API-Key", in = ParameterIn.HEADER, required = true,
                    description = "AI 서버 전용 비밀키"))
    public ResponseEntity<AdminCompetitionResponse> create(@Valid @RequestBody AiCompetitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competitionService.createByAi(request));
    }

    @PostMapping("/batch")
    @Operation(summary = "AI 검증 공모전 일괄 등록", description = "1~20건. 공고마다 따로 저장하므로 일부가 실패해도 나머지는 저장됩니다. 실패한 공고는 원본·사유·수신 시각과 함께 실패 기록에 남고 서버 로그에 AI_INGESTION_FAILURE로 기록됩니다. 응답은 201이며 저장에 성공한 공고만 담습니다. 본문 형식 오류·0건·21건 이상은 400, DB 연결 장애처럼 처리를 이어갈 수 없으면 500입니다.",
            parameters = @Parameter(name = "X-AI-API-Key", in = ParameterIn.HEADER, required = true,
                    description = "AI 서버 전용 비밀키"))
    public ResponseEntity<List<AdminCompetitionResponse>> createBatch(@Valid @RequestBody AiCompetitionBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiCompetitionBatchService.ingest(request.competitions()));
    }

    // AI 수집 서버 디버깅용: GlobalExceptionHandler는 400을 조용히 반환하지만,
    // 이 엔드포인트는 외부(AI 서버)가 보내는 값이라 실패 원인을 서버 로그에서 바로 볼 수 있어야 한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultDto> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        logger.warn("AI competition ingestion 400: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResultDto> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        logger.warn("AI competition ingestion 400: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResultDto> handleMalformedRequest(HttpMessageNotReadableException e) {
        logger.warn("AI competition ingestion 400: malformed request body — {}", e.getMostSpecificCause().getMessage());
        return badRequest("요청 본문이 올바르지 않습니다.");
    }

    private ResponseEntity<ResultDto> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResultDto.builder()
                .success(false)
                .message(message)
                .code(HttpStatus.BAD_REQUEST.value())
                .build());
    }
}
