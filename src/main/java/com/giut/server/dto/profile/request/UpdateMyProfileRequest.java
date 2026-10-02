package com.giut.server.dto.profile.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.giut.server.dto.profile.common.ActivityHistoryDto;
import com.giut.server.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "내 프로필 전체 수정 요청. 이름과 성별은 변경하지 않습니다.")
public record UpdateMyProfileRequest(
        @Schema(name = "departmentname", description = "학과명. 한글 학과명 또는 Enum 코드 모두 입력할 수 있습니다.", example = "컴퓨터과학부")
        @JsonProperty("departmentname")
        @NotNull(message = "학과는 필수입니다.")
        UserProfile.DepartmentType department,

        @Schema(description = "대표 역할 코드 목록", example = "[\"DEVELOPMENT\", \"PLANNING\"]")
        @NotEmpty(message = "대표 역할은 1개 이상 선택해야 합니다.")
        @Size(max = 4, message = "대표 역할은 최대 4개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "대표 역할 코드는 비어 있을 수 없습니다.") String> primaryRoles,

        @Schema(description = "세부 역할 코드 목록", example = "[\"BACKEND_DEVELOPER\", \"DATA_ANALYST\"]")
        @NotEmpty(message = "세부 역할은 1개 이상 선택해야 합니다.")
        @Size(max = 3, message = "세부 역할은 최대 3개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "세부 역할 코드는 비어 있을 수 없습니다.") String> roles,

        @Schema(description = "현재 활동 상태", example = "LOOKING_FOR_TEAM", allowableValues = {"LOOKING_FOR_TEAM", "OPEN_TO_OFFERS", "RESTING"})
        @NotNull(message = "현재 활동 상태는 필수입니다.")
        UserProfile.ActivityStatus activityStatus,

        @Schema(description = "학년", example = "3")
        @NotNull(message = "학년은 필수입니다.")
        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 5, message = "학년은 5 이하여야 합니다.")
        Short grade,

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.giut.com/profiles/12.png", nullable = true)
        @Size(max = 2048, message = "프로필 이미지 URL은 2048자 이하여야 합니다.")
        String profileImageUrl,

        @Schema(description = "자기소개", example = "백엔드와 AI 프로젝트에 관심이 있습니다.", nullable = true)
        @Size(max = 200, message = "자기소개는 200자 이하여야 합니다.")
        String bio,

        @Schema(description = "기웃허브 공개 여부", example = "true")
        @NotNull(message = "프로필 공개 여부는 필수입니다.")
        Boolean searchable,

        @Schema(description = "기술 스택 태그 ID 목록", example = "[1, 4]")
        @NotNull(message = "기술 스택 목록은 필수입니다. 선택하지 않았다면 빈 배열을 입력하세요.")
        @Size(max = 10, message = "기술 스택은 최대 10개까지 선택할 수 있습니다.")
        List<Long> skillTagIds,

        @Schema(description = "관심 분야 태그 ID 목록", example = "[21, 25]")
        @NotNull(message = "관심 분야 목록은 필수입니다. 선택하지 않았다면 빈 배열을 입력하세요.")
        @Size(max = 3, message = "관심 분야는 최대 3개까지 선택할 수 있습니다.")
        List<Long> interestTagIds,

        @Schema(description = "활동 경험 태그 ID 목록", example = "[31]")
        @NotNull(message = "활동 경험 목록은 필수입니다. 선택하지 않았다면 빈 배열을 입력하세요.")
        @Size(max = 3, message = "활동 경험은 최대 3개까지 선택할 수 있습니다.")
        List<Long> experienceTagIds,

        @Schema(
                description = "수정 후 남길 활동 이력 전체 목록. 기존 목록을 교체하며, 모두 삭제하려면 빈 배열을 입력합니다.",
                example = "[{\"category\":\"AWARD\",\"title\":\"서울시 데이터 활용 공모전 우수상\",\"organization\":\"서울특별시\",\"startMonth\":\"2025-03\",\"endMonth\":\"2025-06\"}]"
        )
        @NotNull(message = "활동 이력 목록은 필수입니다. 없으면 빈 배열을 입력하세요.")
        List<@NotNull(message = "활동 이력 목록에는 null을 넣을 수 없습니다.") @Valid ActivityHistoryDto> activityHistories
) {
}
