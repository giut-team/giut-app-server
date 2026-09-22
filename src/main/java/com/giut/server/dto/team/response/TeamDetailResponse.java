package com.giut.server.dto.team.response;

import com.giut.server.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "팀 상세 응답")
public record TeamDetailResponse(
        @Schema(description = "팀 ID", example = "1")
        Long teamId,

        @Schema(description = "대회 ID", example = "1")
        Long competitionId,

        @Schema(description = "팀장 사용자 ID", example = "12")
        Long leaderUserId,

        @Schema(description = "팀 이름", example = "기웃 백엔드팀")
        String name,

        @Schema(description = "팀 소개", example = "서울시립대 학생 공모전 팀입니다.")
        String description,

        @Schema(description = "활동 방식", example = "HYBRID")
        Team.ActivityMode activityMode,

        @Schema(description = "최대 팀원 수", example = "4")
        Short maxMemberCount,

        @Schema(description = "현재 활성 팀원 수", example = "2")
        int currentMemberCount,

        @Schema(description = "주간 회의 횟수", example = "1")
        Short weeklyMeetingCount,

        @Schema(description = "주로 만나는 곳", example = "CAMPUS")
        Team.MeetingPlace meetingPlace,

        @Schema(description = "팀 모집 분야 목록")
        List<TeamRecruitmentResponse> recruitments,

        @Schema(description = "팀 상태", example = "RECRUITING")
        Team.Status status,

        @Schema(description = "생성일시", example = "2026-09-08T10:30:00Z")
        Instant createdAt,

        @Schema(description = "팀 지원서 질문 목록")
        List<TeamApplicationQuestionResponse> applicationQuestions
) {
}
