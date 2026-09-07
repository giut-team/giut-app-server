package com.giut.server.dto.request;

import com.giut.server.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "내 프로필 등록 또는 전체 수정 요청")
public record PutMyProfileRequest(
        @Schema(description = "학과 ID", example = "3")
        @NotNull(message = "학과는 필수입니다.")
        Short departmentId,

        @Schema(description = "학년", example = "3")
        @NotNull(message = "학년은 필수입니다.")
        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 6, message = "학년은 6 이하여야 합니다.")
        Short grade,

        @Schema(description = "성별", example = "UNSPECIFIED", allowableValues = {"MALE", "FEMALE", "OTHER", "UNSPECIFIED"})
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
        Boolean searchable
) {
}
