package com.giut.server.controller.Admin;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.competition.request.UpsertCompetitionRequest;
import com.giut.server.dto.competition.response.AdminCompetitionResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Competition", description = "관리자 공모전 등록 및 수정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/competitions")
public class AdminCompetitionController {

    private final CompetitionService competitionService;

    @PostMapping
    @Operation(summary = "관리자 공모전 등록", description = "관리자가 공모전 정보와 URL을 직접 등록합니다. 대표 URL은 정확히 하나여야 합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "공모전 등록 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminCompetitionResponse.class))),
            @ApiResponse(responseCode = "400", description = "필수값, 일정 또는 대표 URL 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"IllegalArgumentException : 대표 URL은 정확히 하나여야 합니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @ApiResponse(responseCode = "409", description = "다른 공모전에 이미 등록된 URL"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<AdminCompetitionResponse> createCompetition(
            @Valid @RequestBody UpsertCompetitionRequest request,
            Authentication authentication
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        AdminCompetitionResponse response = competitionService.createByAdmin(adminUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{competitionId}")
    @Operation(summary = "관리자 공모전 수정", description = "관리자가 공모전 기본 정보와 URL 전체 목록을 수정합니다. URL 목록은 요청 값으로 완전히 교체됩니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공모전 수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminCompetitionResponse.class))),
            @ApiResponse(responseCode = "400", description = "필수값, 일정 또는 대표 URL 오류"),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @ApiResponse(responseCode = "404", description = "공모전을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "다른 공모전에 이미 등록된 URL"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<AdminCompetitionResponse> updateCompetition(
            @PathVariable Long competitionId,
            @Valid @RequestBody UpsertCompetitionRequest request,
            Authentication authentication
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(competitionService.updateByAdmin(adminUserId, competitionId, request));
    }

    @PatchMapping("/{competitionId}/publish")
    @Operation(
            summary = "관리자 공모전 공개 승인",
            description = "DRAFT 상태인 공모전을 PUBLISHED 상태로 변경합니다. 공개된 공모전은 전체 공모전 목록에 노출됩니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공모전 공개 승인 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminCompetitionResponse.class))),
            @ApiResponse(responseCode = "400", description = "DRAFT 상태가 아닌 공모전 승인 시도"),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @ApiResponse(responseCode = "404", description = "공모전을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<AdminCompetitionResponse> publishCompetition(
            @PathVariable Long competitionId,
            Authentication authentication
    ) {
        Long adminUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(competitionService.publishByAdmin(adminUserId, competitionId));
    }
}
