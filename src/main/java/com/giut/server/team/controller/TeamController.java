package com.giut.server.team.controller;

import com.giut.server.global.dto.ResultDto;
import com.giut.server.team.dto.request.ApplyTeamRequest;
import com.giut.server.team.dto.request.ApproveTeamApplicationRequest;
import com.giut.server.team.dto.request.CreateTeamRequest;
import com.giut.server.team.dto.request.RejectTeamApplicationRequest;
import com.giut.server.team.dto.response.ApproveTeamApplicationResponse;
import com.giut.server.team.dto.response.CreateTeamResponse;
import com.giut.server.team.dto.response.TeamApplicationListResponse;
import com.giut.server.team.dto.response.TeamApplicationResponse;
import com.giut.server.team.dto.response.TeamDetailResponse;
import com.giut.server.team.dto.response.TeamMemberListResponse;
import com.giut.server.team.dto.response.TeamRecruitmentListResponse;
import com.giut.server.team.dto.response.MyTeamApplicationListResponse;
import com.giut.server.team.dto.response.TeamPageResponse;
import com.giut.server.team.service.TeamService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Team", description = "팀 생성 및 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @Operation(summary = "공모전별 모집 중인 팀 목록 조회", description = "공모전 ID로 모집 중인 팀을 최신순으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponse(responseCode = "200", description = "팀 목록 조회 성공")
    public ResponseEntity<TeamPageResponse> getRecruitingTeams(
            @RequestParam Long competitionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(teamService.getRecruitingTeams(competitionId, page, size));
    }

    @GetMapping("/applications/me")
    @Operation(summary = "내 팀 참가 신청 조회", description = "내가 제출한 신청과 처리 상태를 최신순으로 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponse(responseCode = "200", description = "내 신청 목록 조회 성공")
    public ResponseEntity<MyTeamApplicationListResponse> getMyApplications(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.getMyApplications(userId));
    }

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

    @GetMapping("/{teamId}")
    @Operation(
            summary = "팀 상세 조회",
            description = "팀의 기본 정보, 현재 팀원 수, 모집 상태와 지원서 질문을 조회합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamDetailResponse.class),
                            examples = @ExampleObject(value = "{\"teamId\":1,\"competitionId\":1,\"leaderUserId\":12,\"name\":\"기웃 백엔드팀\",\"description\":\"서울시립대 학생 공모전 팀입니다.\",\"activityMode\":\"HYBRID\",\"maxMemberCount\":4,\"currentMemberCount\":2,\"status\":\"RECRUITING\",\"applicationQuestions\":[{\"questionId\":1,\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"required\":true,\"displayOrder\":1}]}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    })
    public ResponseEntity<TeamDetailResponse> getTeamDetail(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamService.getTeamDetail(teamId));
    }

    @GetMapping("/{teamId}/members")
    @Operation(
            summary = "팀원 목록 조회",
            description = "팀에 현재 소속된 활성 팀원 목록을 조회합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀원 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamMemberListResponse.class),
                            examples = @ExampleObject(value = "{\"teamId\":1,\"members\":[{\"teamMemberId\":1,\"userId\":12,\"nickname\":\"팀장\",\"role\":\"LEADER\",\"status\":\"ACTIVE\",\"joinedAt\":\"2026-09-08T10:30:00Z\"}]}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    })
    public ResponseEntity<TeamMemberListResponse> getTeamMembers(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamService.getTeamMembers(teamId));
    }

    @GetMapping("/{teamId}/recruitments")
    @Operation(
            summary = "팀 모집 분야 목록 조회",
            description = "팀에서 모집 중인 분야와 분야별 필요 인원 및 현재 충원 인원을 조회합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 모집 분야 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamRecruitmentListResponse.class),
                            examples = @ExampleObject(value = "{\"teamId\":1,\"recruitments\":[{\"recruitmentId\":1,\"roleCode\":\"BACKEND_DEVELOPER\",\"requiredCount\":1,\"filledCount\":0},{\"recruitmentId\":2,\"roleCode\":\"FRONTEND_DEVELOPER\",\"requiredCount\":2,\"filledCount\":1}]}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패 또는 토큰 누락"),
            @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    })
    public ResponseEntity<TeamRecruitmentListResponse> getTeamRecruitments(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamService.getTeamRecruitments(teamId));
    }

    @PostMapping("/{teamId}/applications")
    @Operation(
            summary = "팀 참가 신청",
            description = "지원 분야와 지원서 답변을 제출합니다. 이미 팀원인 사용자나 승인 대기 중인 신청이 있는 사용자는 신청할 수 없습니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "팀 참가 신청 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TeamApplicationResponse.class),
                            examples = @ExampleObject(value = "{\"applicationId\":1,\"teamId\":1,\"userId\":15,\"roleCode\":\"BACKEND_DEVELOPER\",\"assignedRoleCode\":null,\"rejectionReason\":null,\"message\":\"백엔드 개발로 참여하고 싶습니다.\",\"status\":\"PENDING\",\"appliedAt\":\"2026-09-08T10:40:00Z\",\"decidedAt\":null,\"answers\":[{\"questionId\":1,\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"answer\":\"서울시 공공데이터를 다뤄본 경험이 있습니다.\",\"displayOrder\":1}]}")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
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
                    responseCode = "403",
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

    @DeleteMapping("/{teamId}/applications/{applicationId}")
    @Operation(summary = "팀 참가 신청 취소", description = "신청자가 자신의 승인 대기 중인 참가 신청을 취소합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponse(responseCode = "204", description = "신청 취소 성공")
    public ResponseEntity<Void> cancelApplication(
            Authentication authentication,
            @PathVariable Long teamId,
            @PathVariable Long applicationId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        teamService.cancelApplication(userId, teamId, applicationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{teamId}/close")
    @Operation(summary = "팀 모집 마감", description = "팀장이 팀의 모집 상태를 CLOSED로 변경합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponse(responseCode = "200", description = "모집 마감 성공")
    public ResponseEntity<TeamDetailResponse> closeRecruitment(
            Authentication authentication,
            @PathVariable Long teamId
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.closeRecruitment(userId, teamId));
    }

    @PostMapping("/{teamId}/applications/{applicationId}/approve")
    @Operation(
            summary = "팀 참가 신청 승인",
            description = "팀장이 합류 분야를 지정하여 신청을 승인합니다. 해당 분야 정원과 팀 전체 정원을 확인합니다."
    )
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "팀 참가 신청 승인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApproveTeamApplicationResponse.class),
                            examples = @ExampleObject(value = "{\"applicationId\":1,\"teamId\":1,\"userId\":15,\"status\":\"APPROVED\",\"roleCode\":\"BACKEND_DEVELOPER\",\"teamMemberId\":3}")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "정원 마감 또는 이미 참여 중인 사용자",
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
            @PathVariable Long applicationId,
            @Valid @RequestBody ApproveTeamApplicationRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.approveApplication(userId, teamId, applicationId, request.roleCode()));
    }

    @PostMapping("/{teamId}/applications/{applicationId}/reject")
    @Operation(
            summary = "팀 참가 신청 거절",
            description = "팀장이 승인 대기 중인 참가 신청을 거절합니다. 거절 사유는 선택적으로 전달할 수 있습니다."
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
                    responseCode = "403",
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
            @PathVariable Long applicationId,
            @Valid @RequestBody(required = false) RejectTeamApplicationRequest request
    ) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(teamService.rejectApplication(
                userId, teamId, applicationId, request == null ? null : request.reason()
        ));
    }
}
