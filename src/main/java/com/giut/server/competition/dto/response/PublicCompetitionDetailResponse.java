package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record PublicCompetitionDetailResponse(
        Long id,
        String title,
        Competition.Category category,
        String categoryName,
        String hostOrganization,
        String targetParticipants,
        String summary,
        Instant applicationStartAt,
        Instant applicationEndAt,
        CompetitionRecruitmentStatus recruitmentStatus,
        String recruitmentStatusName,
        long viewCount,
        long scrapCount,
        boolean scrapped,
        List<CompetitionUrlResponse> urls,
        @Schema(description = "응답에 포함된 RECRUITING 상태의 팀 수. 공모전 마감일과 무관합니다.", example = "2")
        long teamCount,
        @Schema(description = "RECRUITING 상태의 팀 수. teamCount와 동일하며 공모전 마감일과 무관합니다.", example = "2")
        long recruitingTeamCount,
        @Schema(description = "RECRUITING 상태의 팀 목록. 공모전 마감일과 무관하며, 해당 팀이 없으면 빈 배열입니다.")
        List<CompetitionTeamResponse> teams
) {

    public static PublicCompetitionDetailResponse from(
            Competition competition,
            CompetitionRecruitmentStatus recruitmentStatus,
            long scrapCount,
            boolean scrapped,
            List<CompetitionUrlResponse> urls,
            long teamCount,
            long recruitingTeamCount,
            List<CompetitionTeamResponse> teams
    ) {
        return new PublicCompetitionDetailResponse(
                competition.getId(),
                competition.getTitle(),
                competition.getCategory(),
                competition.getCategory().getDisplayName(),
                competition.getHostOrganization(),
                competition.getTargetParticipants(),
                competition.getSummary(),
                competition.getApplicationStartAt(),
                competition.getApplicationEndAt(),
                recruitmentStatus,
                recruitmentStatus.getDisplayName(),
                competition.getViewCount(),
                scrapCount,
                scrapped,
                urls,
                teamCount,
                recruitingTeamCount,
                teams
        );
    }
}
