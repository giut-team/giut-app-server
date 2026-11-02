package com.giut.server.competition.dto.response;

import com.giut.server.competition.entity.Competition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record PublicCompetitionDetailResponse(
        Long id,
        String title,
        Competition.ActivityType activityType,
        String activityTypeName,
        Competition.Category category,
        String categoryName,
        String hostOrganization,
        String targetParticipants,
        String summary,
        String prizeInfo,
        Instant applicationStartAt,
        Instant applicationEndAt,
        CompetitionRecruitmentStatus recruitmentStatus,
        String recruitmentStatusName,
        long viewCount,
        long scrapCount,
        boolean scrapped,
        List<CompetitionUrlResponse> urls,
        @Schema(description = "상태(RECRUITING/CLOSED/ARCHIVED)와 무관한 이 공모전의 전체 팀 수.", example = "3")
        long teamCount,
        @Schema(description = "RECRUITING 상태인 팀 수. 공모전 마감일과는 무관합니다.", example = "2")
        long recruitingTeamCount,
        @Schema(description = "RECRUITING 상태의 팀 목록. 공모전 마감일과 무관하며, 해당 팀이 없으면 빈 배열입니다.")
        List<CompetitionTeamResponse> teams,
        @Schema(description = "실제 검증 완료 여부. AUTO_VERIFIED 또는 MANUALLY_VERIFIED일 때만 true이며 게시·모집 상태와 무관합니다.", example = "true")
        boolean verified
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
                competition.getActivityType(),
                competition.getActivityType() == null ? null : competition.getActivityType().getDisplayName(),
                competition.getCategory(),
                competition.getCategory().getDisplayName(),
                competition.getHostOrganization(),
                competition.getTargetParticipants(),
                competition.getSummary(),
                competition.getPrizeInfo(),
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
                teams,
                competition.isVerified()
        );
    }
}
