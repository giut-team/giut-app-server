package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.team.request.ApplyTeamRequest;
import com.giut.server.dto.team.request.CreateTeamRequest;
import com.giut.server.dto.team.response.ApproveTeamApplicationResponse;
import com.giut.server.dto.team.response.CreateTeamResponse;
import com.giut.server.dto.team.response.TeamApplicationListResponse;
import com.giut.server.dto.team.response.TeamApplicationResponse;
import com.giut.server.service.TeamService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Team", description = "팀 생성 및 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @Operation(
            summary = "팀 생성",
            description = "대회에 참여할 팀을 생성합니다. 팀 생성 시 팀장은 팀원으로 자동 등록됩니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "팀 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateTeamResponse.class),
                            examples = @ExampleObject(value = "{\"teamId\":1,\"competitionId\":1,\"leaderUserId\":12,\"name\":\"기웃 백엔드팀\",\"status\":\"RECRUITING\",\"createdAt\":\"2026-09-08T10:30:00Z\",\"applicationQuestions\":[{\"questionId\":1,\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"required\":true,\"displayOrder\":1}]}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청값",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"팀 이름은 필수입니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "팀장 또는 대회를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 대회를 찾을 수 없습니다.\",\"code\":404}"))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}"))
            )
    })
    public ResponseEntity<CreateTeamResponse> createTeam(
            Authentication authentication,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(userId, request));
    }

    @PostMapping("/{teamId}/applications")
    @Operation(
            summary = "팀 참가 신청",
            description = "모집 중인 팀에 참가 신청을 생성합니다. 이미 팀원인 사용자나 승인 대기 중인 신청이 있는 사용자는 신청할 수 없습니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "팀 참가 신청 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamApplicationResponse.class),
                            examples = @ExampleObject(value = "{\"applicationId\":1,\"teamId\":1,\"userId\":15,\"message\":\"백엔드 개발로 참여하고 싶습니다.\",\"status\":\"PENDING\",\"appliedAt\":\"2026-09-08T10:40:00Z\",\"decidedAt\":null,\"answers\":[{\"questionId\":1,\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"answer\":\"서울시 공공데이터를 다뤄본 경험이 있습니다.\",\"displayOrder\":1}]}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "모집 중이 아니거나 이미 참여/신청 중인 팀",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"이미 승인 대기 중인 참가 신청이 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신청자 또는 팀을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 팀을 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<TeamApplicationResponse> applyTeam(
            Authentication authentication,
            @PathVariable Long teamId,
            @Valid @RequestBody ApplyTeamRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.applyTeam(userId, teamId, request));
    }

    @GetMapping("/{teamId}/applications")
    @Operation(
            summary = "팀 참가 신청 목록 조회",
            description = "팀장이 승인 대기 중인 참가 신청 목록과 지원서 답변을 조회합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 참가 신청 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamApplicationListResponse.class),
                            examples = @ExampleObject(value = "{\"teamId\":1,\"applications\":[{\"applicationId\":1,\"teamId\":1,\"userId\":15,\"message\":\"백엔드 개발로 참여하고 싶습니다.\",\"status\":\"PENDING\",\"appliedAt\":\"2026-09-08T10:40:00Z\",\"decidedAt\":null,\"answers\":[{\"questionId\":1,\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"answer\":\"서울시 공공데이터를 다뤄본 경험이 있습니다.\",\"displayOrder\":1}]}]}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "팀장이 아닌 사용자의 조회 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"팀장만 참가 신청을 처리할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "팀을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 팀을 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<TeamApplicationListResponse> getPendingApplications(
            Authentication authentication,
            @PathVariable Long teamId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.getPendingApplications(userId, teamId));
    }

    @PostMapping("/{teamId}/applications/{applicationId}/approve")
    @Operation(
            summary = "팀 참가 신청 승인",
            description = "팀장이 참가 신청을 승인합니다. 승인 시 신청자는 팀원으로 추가됩니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 참가 신청 승인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApproveTeamApplicationResponse.class),
                            examples = @ExampleObject(value = "{\"applicationId\":1,\"teamId\":1,\"userId\":15,\"status\":\"APPROVED\",\"teamMemberId\":3}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "팀장이 아니거나 정원 마감 또는 이미 참여 중인 사용자",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"팀장만 참가 신청을 처리할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "팀 또는 승인 대기 중인 신청 내역을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 승인 대기 중인 참가 신청을 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<ApproveTeamApplicationResponse> approveApplication(
            Authentication authentication,
            @PathVariable Long teamId,
            @PathVariable Long applicationId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.approveApplication(userId, teamId, applicationId));
    }

    @PostMapping("/{teamId}/applications/{applicationId}/reject")
    @Operation(
            summary = "팀 참가 신청 거절",
            description = "팀장이 승인 대기 중인 참가 신청을 거절합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 참가 신청 거절 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamApplicationResponse.class),
                            examples = @ExampleObject(value = "{\"applicationId\":1,\"teamId\":1,\"userId\":15,\"message\":\"백엔드 개발로 참여하고 싶습니다.\",\"status\":\"REJECTED\",\"appliedAt\":\"2026-09-08T10:40:00Z\",\"decidedAt\":\"2026-09-08T10:45:00Z\",\"answers\":[{\"questionId\":1,\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"answer\":\"서울시 공공데이터를 다뤄본 경험이 있습니다.\",\"displayOrder\":1}]}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "팀장이 아닌 사용자의 처리 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"팀장만 참가 신청을 처리할 수 있습니다.\",\"code\":400}"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}"))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "팀 또는 승인 대기 중인 신청 내역을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResultDto.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Resource not Found : 승인 대기 중인 참가 신청을 찾을 수 없습니다.\",\"code\":404}"))
            )
    })
    public ResponseEntity<TeamApplicationResponse> rejectApplication(
            Authentication authentication,
            @PathVariable Long teamId,
            @PathVariable Long applicationId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.rejectApplication(userId, teamId, applicationId));
    }
}
