package com.giut.server.competition.dto.response;

import com.giut.server.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공모전에 연결된 팀 요약")
public record CompetitionTeamResponse(
        @Schema(description = "팀 ID", example = "3") Long teamId,
        @Schema(description = "팀 이름", example = "서울시 데이터 분석팀") String name,
        @Schema(description = "현재 사용자가 만든 팀인지", example = "true") boolean myTeam,
        @Schema(description = "팀 소개", example = "데이터 분석과 서비스 개발을 함께할 팀원 모집") String description,
        @Schema(description = "팀 정원", example = "5") Short maxMemberCount,
        @Schema(description = "현재 활성 팀원 수", example = "2") long currentMemberCount,
        @Schema(description = "팀 상태", example = "RECRUITING") Team.Status status
) {
    public static CompetitionTeamResponse from(Team team, Long currentUserId, long currentMemberCount) {
        return new CompetitionTeamResponse(
                team.getId(),
                team.getName(),
                team.getLeader().getId().equals(currentUserId),
                team.getDescription(),
                team.getMaxMemberCount(),
                currentMemberCount,
                team.getStatus()
        );
    }
}
