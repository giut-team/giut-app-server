package com.giut.server.team.dto.response;

import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "내 소속 팀 목록 항목")
public record MyTeamResponse(
        Long teamId,
        Long competitionId,
        Long leaderUserId,
        String name,
        Team.ActivityMode activityMode,
        Short maxMemberCount,
        long currentMemberCount,
        Team.Status status,
        Instant createdAt,
        @Schema(description = "현재 사용자의 팀 내 역할", example = "LEADER")
        TeamMember.Role myRole,
        @Schema(description = "현재 사용자의 담당 분야 코드", example = "BACKEND_DEVELOPER")
        String myRoleCode,
        @Schema(description = "공모전 이름")
        String competitionTitle,
        @Schema(description = "미처리 참가 신청 수. 팀장이 아닌 경우 0입니다.", example = "2")
        long pendingApplicationCount
) {
    public static MyTeamResponse of(
            Team team,
            TeamMember membership,
            long currentMemberCount,
            long pendingApplicationCount
    ) {
        return new MyTeamResponse(
                team.getId(),
                team.getCompetition().getId(),
                team.getLeader().getId(),
                team.getName(),
                team.getActivityMode(),
                team.getMaxMemberCount(),
                currentMemberCount,
                team.getStatus(),
                team.getCreatedAt(),
                membership.getRole(),
                membership.getRoleCode(),
                team.getCompetition().getTitle(),
                pendingApplicationCount
        );
    }
}
