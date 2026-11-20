package com.giut.server.team.dto.response;

import com.giut.server.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 지원 내역에 표시할 팀 요약")
public record AppliedTeamResponse(
        String name,
        Team.Status status,
        long currentMemberCount,
        Short maxMemberCount,
        Long competitionId,
        String competitionTitle
) {
    public static AppliedTeamResponse of(Team team, long currentMemberCount) {
        return new AppliedTeamResponse(
                team.getName(), team.getStatus(), currentMemberCount, team.getMaxMemberCount(),
                team.getCompetition().getId(), team.getCompetition().getTitle()
        );
    }
}
