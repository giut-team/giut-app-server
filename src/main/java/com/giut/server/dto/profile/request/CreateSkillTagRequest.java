package com.giut.server.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "직접 입력한 기술 스택 추가 요청")
public record CreateSkillTagRequest(
        @Schema(description = "추가할 기술 스택 이름", example = "Docker")
        @NotBlank(message = "기술 스택 이름은 필수입니다.")
        @Size(max = 80, message = "기술 스택 이름은 80자 이하여야 합니다.")
        String name,

        @Schema(description = "기술 스택과 연결할 세부 역할 코드 목록", example = "[\"BACKEND_DEVELOPER\"]")
        @NotEmpty(message = "연결할 세부 역할은 1개 이상 선택해야 합니다.")
        @Size(max = 3, message = "연결할 세부 역할은 최대 3개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "세부 역할 코드는 비어 있을 수 없습니다.") String> relatedRoleCodes
) {
}
