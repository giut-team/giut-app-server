package com.giut.server.competition.controller;

import com.giut.server.competition.dto.request.AiCompetitionRequest;
import com.giut.server.competition.dto.request.AiCompetitionBatchRequest;
import com.giut.server.competition.dto.response.AdminCompetitionResponse;
import com.giut.server.competition.service.CompetitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final CompetitionService competitionService;

    @PostMapping
    @Operation(summary = "AI 검증 공모전 등록", description = "X-AI-API-Key 인증. 수신 즉시 PUBLISHED, AUTO_VERIFIED로 등록합니다.",
            parameters = @Parameter(name = "X-AI-API-Key", in = ParameterIn.HEADER, required = true,
                    description = "AI 서버 전용 비밀키"))
    public ResponseEntity<AdminCompetitionResponse> create(@Valid @RequestBody AiCompetitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competitionService.createByAi(request));
    }

    @PostMapping("/batch")
    @Operation(summary = "AI 검증 공모전 일괄 등록", description = "1~50건. 한 건이라도 실패하면 전체 저장을 취소합니다.",
            parameters = @Parameter(name = "X-AI-API-Key", in = ParameterIn.HEADER, required = true,
                    description = "AI 서버 전용 비밀키"))
    public ResponseEntity<List<AdminCompetitionResponse>> createBatch(@Valid @RequestBody AiCompetitionBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competitionService.createBatchByAi(request.competitions()));
    }
}
