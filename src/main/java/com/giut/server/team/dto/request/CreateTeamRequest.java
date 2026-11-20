package com.giut.server.team.dto.request;

import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.Team.CollaborationTool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "팀 생성 요청")
public record CreateTeamRequest(
        @Schema(description = "대회 ID", example = "1")
        @NotNull(message = "대회 ID는 필수입니다.")
        Long competitionId,

        @Schema(description = "팀 이름", example = "기웃 백엔드팀")
        @NotBlank(message = "팀 이름은 필수입니다.")
        @Size(max = 100, message = "팀 이름은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "팀 소개", example = "서울시립대 학생 공모전 팀입니다.", nullable = true)
        @Size(max = 1000, message = "팀 소개는 1000자 이하여야 합니다.")
        String description,

        @Schema(description = "활동 방식", example = "HYBRID", allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
        @NotNull(message = "활동 방식은 필수입니다.")
        Team.ActivityMode activityMode,

        @Schema(description = "최대 팀원 수", example = "4")
        @NotNull(message = "최대 팀원 수는 필수입니다.")
        @Min(value = 2, message = "최대 팀원 수는 2명 이상이어야 합니다.")
        @Max(value = 20, message = "최대 팀원 수는 20명 이하여야 합니다.")
        Short maxMemberCount,

        @Schema(description = "주간 회의 횟수. 새 화면은 4회 이상 선택 시 4를 전송합니다.", example = "1")
        @NotNull(message = "주간 회의 횟수는 필수입니다.")
        @Min(value = 0, message = "주간 회의 횟수는 0회 이상이어야 합니다.")
        @Max(value = 7, message = "주간 회의 횟수는 7회 이하여야 합니다.")
        Short weeklyMeetingCount,

        @Schema(description = "회의 장소. 오프라인·혼합일 때 필수이며 온라인은 생략합니다.", example = "CAMPUS")
        Team.MeetingPlace meetingPlace,

        @Schema(description = "팀 모집 분야 목록", example = "[{\"roleCode\":\"BACKEND_DEVELOPER\",\"requiredCount\":1}]")
        @Size(max = 10, message = "모집 분야는 최대 10개까지 등록할 수 있습니다.")
        List<@Valid CreateTeamRecruitmentRequest> recruitments,

        @Schema(description = "팀장이 설정한 지원서 질문 목록", example = "[{\"question\":\"이 팀에 지원한 이유를 알려주세요.\",\"required\":true}]")
        @Size(max = 5, message = "지원서 질문은 최대 5개까지 등록할 수 있습니다.")
        List<@Valid CreateTeamQuestionRequest> applicationQuestions,

        @Schema(description = "팀장의 세부 역할 코드", example = "BACKEND_DEVELOPER")
        @NotBlank(message = "팀장의 담당 분야는 필수입니다.")
        @Size(max = 40, message = "팀장의 담당 분야 코드는 40자 이하여야 합니다.")
        String leaderRoleCode,

        @Schema(description = "협업 도구. 온라인·혼합일 때 1개 이상 선택하며 오프라인은 생략합니다.",
                example = "[\"DISCORD\", \"NOTION\"]")
        @Size(max = 5, message = "협업 도구는 최대 5개까지 선택할 수 있습니다.")
        List<@NotNull(message = "협업 도구 코드는 필수입니다.") CollaborationTool> collaborationTools
) {
}
