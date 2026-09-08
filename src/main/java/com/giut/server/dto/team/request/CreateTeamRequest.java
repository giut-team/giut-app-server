package com.giut.server.dto.team.request;

import com.giut.server.entity.Team;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        Short maxMemberCount
) {
}
