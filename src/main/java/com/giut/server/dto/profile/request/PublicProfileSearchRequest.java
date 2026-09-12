package com.giut.server.dto.profile.request;

import com.giut.server.entity.ProfileRole;
import com.giut.server.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record PublicProfileSearchRequest(
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        Integer page,

        @Schema(description = "대표 역할 코드", example = "DEVELOPMENT")
        ProfileRole.PrimaryRole primaryRole,

        @Schema(description = "세부 역할 코드", example = "BACKEND_DEVELOPER")
        String role,

        @Schema(description = "현재 활동 상태", example = "LOOKING_FOR_TEAM")
        UserProfile.ActivityStatus activityStatus,

        @Schema(description = "학과명 또는 학과 코드", example = "컴퓨터과학부")
        String department,

        @Schema(description = "기술 스택 태그 ID", example = "1")
        @Positive(message = "skillTagId는 양수여야 합니다.")
        Long skillTagId
) {

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public String normalizedRole() {
        return role == null || role.isBlank() ? null : role.trim();
    }

    public UserProfile.DepartmentType departmentType() {
        return department == null || department.isBlank()
                ? null
                : UserProfile.DepartmentType.from(department.trim());
    }
}
