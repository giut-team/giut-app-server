package com.giut.server.team.controller;

import com.giut.server.team.dto.request.CreateTeamInviteLinkRequest;
import com.giut.server.team.dto.request.TeamInviteTokenRequest;
import com.giut.server.team.dto.response.TeamInviteJoinResponse;
import com.giut.server.team.dto.response.TeamInviteLinkResponse;
import com.giut.server.team.dto.response.TeamInvitePreviewResponse;
import com.giut.server.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Team", description = "팀 생성 및 관리")
@SecurityRequirement(name = "JWT")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams/{teamId}/invite-links")
public class TeamInviteLinkController {

    private final TeamService teamService;

    @PostMapping
    @Operation(summary = "팀 초대 링크 발급", description = "팀장이 모집 분야를 지정해 24시간 유효한 일회용 링크를 발급합니다.")
    @ApiResponse(responseCode = "201", description = "링크 발급 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 모집 분야 또는 요청")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "팀장 권한 또는 학교 인증 필요")
    @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "팀 모집 또는 정원 마감")
    public ResponseEntity<TeamInviteLinkResponse> create(
            Authentication authentication,
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTeamInviteLinkRequest request
    ) {
        TeamInviteLinkResponse response = teamService.createInviteLink(
                Long.valueOf(authentication.getName()), teamId, request.roleCode()
        );
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/preview")
    @Operation(summary = "팀 초대 링크 미리보기", description = "공유 URL의 토큰으로 팀 이름과 지정된 모집 분야를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "미리보기 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 토큰 형식")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "학교 인증 필요")
    @ApiResponse(responseCode = "404", description = "팀 또는 링크를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "만료·폐기·사용된 링크")
    public ResponseEntity<TeamInvitePreviewResponse> preview(
            @PathVariable Long teamId,
            @Valid @RequestBody TeamInviteTokenRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(teamService.previewInviteLink(teamId, request.token()));
    }

    @PostMapping("/accept")
    @Operation(summary = "팀 초대 링크 수락", description = "링크에 지정된 모집 분야로 합류합니다. 합류에 성공하면 링크가 사용 처리됩니다.")
    @ApiResponse(responseCode = "201", description = "팀 합류 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 토큰 형식 또는 현재 모집하지 않는 분야")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "학교 인증 필요")
    @ApiResponse(responseCode = "404", description = "팀 또는 링크를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "링크 사용 불가, 정원 마감 또는 기존 팀 참여 이력")
    public ResponseEntity<TeamInviteJoinResponse> accept(
            Authentication authentication,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamInviteTokenRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                teamService.joinViaInviteLink(Long.valueOf(authentication.getName()), teamId, request.token())
        );
    }

    @DeleteMapping("/{inviteLinkId}")
    @Operation(summary = "팀 초대 링크 폐기")
    @ApiResponse(responseCode = "204", description = "링크 폐기 완료")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "팀장 권한 또는 학교 인증 필요")
    @ApiResponse(responseCode = "404", description = "팀 또는 링크를 찾을 수 없음")
    public ResponseEntity<Void> revoke(
            Authentication authentication,
            @PathVariable Long teamId,
            @PathVariable Long inviteLinkId
    ) {
        teamService.revokeInviteLink(Long.valueOf(authentication.getName()), teamId, inviteLinkId);
        return ResponseEntity.noContent().build();
    }
}
