package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.profile.common.ActivityHistoryDto;
import com.giut.server.dto.profile.response.ActivityHistoryListResponse;
import com.giut.server.service.ActivityHistoryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Activity History", description = "내 프로필 활동 이력 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/activity-histories")
public class ActivityHistoryController {

    private final ActivityHistoryService activityHistoryService;

    @GetMapping
    @Operation(summary = "내 활동 이력 목록 조회", description = "등록한 활동 이력을 최근 활동순으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActivityHistoryListResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<ActivityHistoryListResponse> getMyActivityHistories(Authentication authentication) {
        return ResponseEntity.ok(activityHistoryService.getMyActivityHistories(currentUserId(authentication)));
    }

    @PostMapping
    @Operation(summary = "활동 이력 추가", description = "수상, 대외활동, 동아리, 인턴, 자격증 중 하나의 활동 이력을 추가합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActivityHistoryDto.class), examples = @ExampleObject(value = "{\"id\":1,\"category\":\"AWARD\",\"categoryName\":\"수상\",\"title\":\"서울시 데이터 활용 공모전 우수상\",\"organization\":\"서울특별시\",\"startMonth\":\"2025-03\",\"endMonth\":\"2025-06\"}"))),
            @ApiResponse(responseCode = "400", description = "요청값 오류 또는 기간 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<ActivityHistoryDto> createActivityHistory(
            Authentication authentication,
            @Valid @RequestBody ActivityHistoryDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(activityHistoryService.createActivityHistory(currentUserId(authentication), request));
    }

    @PutMapping("/{activityHistoryId}")
    @Operation(summary = "활동 이력 수정", description = "내 활동 이력 하나를 요청 본문 전체로 수정합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActivityHistoryDto.class))),
            @ApiResponse(responseCode = "400", description = "요청값 오류 또는 기간 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "활동 이력을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<ActivityHistoryDto> updateActivityHistory(
            Authentication authentication,
            @PathVariable Long activityHistoryId,
            @Valid @RequestBody ActivityHistoryDto request
    ) {
        return ResponseEntity.ok(activityHistoryService.updateActivityHistory(
                currentUserId(authentication), activityHistoryId, request
        ));
    }

    @DeleteMapping("/{activityHistoryId}")
    @Operation(summary = "활동 이력 삭제", description = "내 활동 이력 하나를 삭제합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "404", description = "활동 이력을 찾을 수 없음", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class)))
    })
    public ResponseEntity<Void> deleteActivityHistory(
            Authentication authentication,
            @PathVariable Long activityHistoryId
    ) {
        activityHistoryService.deleteActivityHistory(currentUserId(authentication), activityHistoryId);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
