package com.giut.server.team.dto.request;

import com.giut.server.team.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "팀 정보 수정 요청")
public record UpdateTeamRequest(
        @Schema(description = "팀 이름", example = "기웃 서버팀")
        @NotBlank(message = "팀 이름은 필수입니다.")
        @Size(max = 100, message = "팀 이름은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "팀 소개", example = "백엔드와 인프라를 함께 개발하는 팀입니다.", nullable = true)
        @Size(max = 1000, message = "팀 소개는 1000자 이하여야 합니다.")
        String description,

        @Schema(description = "활동 방식", example = "HYBRID", allowableValues = {"ONLINE", "OFFLINE", "HYBRID"})
        @NotNull(message = "활동 방식은 필수입니다.")
        Team.ActivityMode activityMode,

        @Schema(description = "최대 팀원 수", example = "5")
        @NotNull(message = "최대 팀원 수는 필수입니다.")
        @Min(value = 2, message = "최대 팀원 수는 2명 이상이어야 합니다.")
        @Max(value = 20, message = "최대 팀원 수는 20명 이하여야 합니다.")
        Short maxMemberCount,

        @Schema(description = "주간 회의 횟수", example = "2")
        @NotNull(message = "주간 회의 횟수는 필수입니다.")
        @Min(value = 0, message = "주간 회의 횟수는 0회 이상이어야 합니다.")
        @Max(value = 7, message = "주간 회의 횟수는 7회 이하여야 합니다.")
        Short weeklyMeetingCount,

        @Schema(description = "주로 만나는 곳", example = "SEOUL", allowableValues = {"CAMPUS", "SEOUL", "METROPOLITAN_AREA", "ANYWHERE"})
        @NotNull(message = "주로 만나는 곳은 필수입니다.")
        Team.MeetingPlace meetingPlace
) {
}
