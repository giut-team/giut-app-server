package com.giut.server.profile.dto.request;

import com.giut.server.profile.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자 학과 변경 요청")
public record UpdateDepartmentRequest(
        @Schema(description = "변경할 학과명 또는 학과 코드", example = "컴퓨터과학부")
        @NotNull(message = "학과는 필수입니다.")
        UserProfile.DepartmentType department
) {
}
