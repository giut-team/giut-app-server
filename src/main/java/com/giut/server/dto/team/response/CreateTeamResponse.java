package com.giut.server.dto.team.response;

import com.giut.server.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "팀 생성 응답")
public record CreateTeamResponse(
        @Schema(description = "생성된 팀 ID", example = "1")
        Long teamId,

        @Schema(description = "대회 ID", example = "1")
        Long competitionId,

        @Schema(description = "팀장 사용자 ID", example = "12")
        Long leaderUserId,

        @Schema(description = "팀 이름", example = "기웃 백엔드팀")
        String name,

        @Schema(description = "팀 상태", example = "RECRUITING")
        Team.Status status,

        @Schema(description = "생성일시", example = "2026-09-08T10:30:00Z")
        Instant createdAt
) {

    public static CreateTeamResponse from(Team team) {
        return new CreateTeamResponse(
                team.getId(),
                team.getCompetition().getId(),
                team.getLeaderUserId(),
                team.getName(),
                team.getStatus(),
                team.getCreatedAt()
        );
    }
}
