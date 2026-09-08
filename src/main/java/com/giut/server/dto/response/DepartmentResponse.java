package com.giut.server.dto.response;

import com.giut.server.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학과 정보")
public record DepartmentResponse(
        @Schema(description = "학과 코드", example = "COMPUTER_SCIENCE")
        UserProfile.DepartmentType code,

        @Schema(description = "학과명", example = "컴퓨터과학부")
        String name
) {
    public static DepartmentResponse from(UserProfile.DepartmentType department) {
        return new DepartmentResponse(department, department.getDisplayName());
    }
}
