package com.giut.server.dto.request;

import com.giut.server.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "내 프로필 등록 또는 전체 수정 요청")
public record PutMyProfileRequest(
        @Schema(description = "학과명. 한글 학과명 또는 Enum 코드 모두 입력할 수 있습니다.", example = "컴퓨터과학부")
        @NotNull(message = "학과는 필수입니다.")
        UserProfile.DepartmentType department,

        @Schema(description = "대표 역할 코드 목록. DEVELOPMENT, DESIGN, PLANNING, MARKETING 중 복수 선택할 수 있습니다.", example = "[\"DEVELOPMENT\", \"PLANNING\"]")
        @NotEmpty(message = "대표 역할은 1개 이상 선택해야 합니다.")
        @Size(max = 4, message = "대표 역할은 최대 4개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "대표 역할 코드는 비어 있을 수 없습니다.") String> primaryRoles,

        @Schema(description = "세부 역할 목록. 대표 역할과 같은 분야의 역할만 선택할 수 있습니다.", example = "[\"BACKEND_DEVELOPER\", \"DATA_ANALYST\"]")
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

        @Schema(description = "성별", example = "MALE", allowableValues = {"MALE", "FEMALE"})
        @NotNull(message = "성별은 필수입니다.")
        UserProfile.Gender gender,

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.giut.com/profiles/12.png", nullable = true)
        @Size(max = 2048, message = "프로필 이미지 URL은 2048자 이하여야 합니다.")
        String profileImageUrl,

        @Schema(description = "자기소개", example = "백엔드와 AI 프로젝트에 관심이 있습니다.", nullable = true)
        @Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
        String bio,

        @Schema(description = "기웃허브 공개 여부", example = "true")
        @NotNull(message = "프로필 공개 여부는 필수입니다.")
        Boolean searchable,

        @Schema(description = "기존 기술 스택 태그 ID 목록", example = "[1, 4]")
        @NotNull(message = "기술 스택 목록은 필수입니다. 선택하지 않았다면 빈 배열을 입력하세요.")
        @Size(max = 3, message = "기술 스택은 최대 3개까지 선택할 수 있습니다.")
        List<Long> skillTagIds,

        @Schema(description = "직접 입력한 기술 스택 목록. 기존 기술 스택과 합쳐 최대 3개입니다.", example = "[\"Docker\"]")
        @NotNull(message = "직접 입력 기술 스택 목록은 필수입니다. 없다면 빈 배열을 입력하세요.")
        @Size(max = 3, message = "직접 입력 기술 스택은 최대 3개까지 입력할 수 있습니다.")
        List<@NotBlank(message = "직접 입력 기술 스택은 비어 있을 수 없습니다.") @Size(max = 80, message = "기술 스택 이름은 80자 이하여야 합니다.") String> customSkills,

        @Schema(description = "관심 분야 태그 ID 목록", example = "[21, 25]")
        @NotNull(message = "관심 분야 목록은 필수입니다. 선택하지 않았다면 빈 배열을 입력하세요.")
        @Size(max = 3, message = "관심 분야는 최대 3개까지 선택할 수 있습니다.")
        List<Long> interestTagIds,

        @Schema(description = "활동 경험 태그 ID 목록", example = "[]")
        @NotNull(message = "활동 경험 목록은 필수입니다. 선택하지 않았다면 빈 배열을 입력하세요.")
        @Size(max = 3, message = "활동 경험은 최대 3개까지 선택할 수 있습니다.")
        List<Long> experienceTagIds,

        @Schema(description = "GitHub, Notion, 포트폴리오 PDF 등 외부 링크 목록", example = "[{\"type\":\"GITHUB\",\"url\":\"https://github.com/giut\",\"title\":\"GitHub\"}]")
        @NotNull(message = "외부 링크 목록은 필수입니다. 없다면 빈 배열을 입력하세요.")
        @Size(max = 4, message = "외부 링크는 최대 4개까지 등록할 수 있습니다.")
        List<@jakarta.validation.Valid ProfileLinkRequest> links
) {
}
