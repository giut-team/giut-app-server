package com.giut.server.team.dto.response;

import com.giut.server.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "팀 목록 항목")
public record TeamSummaryResponse(
        Long teamId,
        Long competitionId,
        Long leaderUserId,
        String name,
        Team.ActivityMode activityMode,
        Short maxMemberCount,
        long currentMemberCount,
        Team.Status status,
        Instant createdAt,
        @Schema(description = "현재 사용자의 팀 북마크 여부", example = "true")
        boolean scrapped
) {
    public static TeamSummaryResponse of(Team team, long currentMemberCount, boolean scrapped) {
        return new TeamSummaryResponse(
                team.getId(), team.getCompetition().getId(), team.getLeader().getId(),
                team.getName(), team.getActivityMode(), team.getMaxMemberCount(),
                currentMemberCount, team.getStatus(), team.getCreatedAt(), scrapped
        );
    }
}
