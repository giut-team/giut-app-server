package com.giut.server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "직접 입력한 기술 스택 추가 요청")
public record CreateSkillTagRequest(
        @Schema(description = "추가할 기술 스택 이름", example = "Docker")
        @NotBlank(message = "기술 스택 이름은 필수입니다.")
        @Size(max = 80, message = "기술 스택 이름은 80자 이하여야 합니다.")
        String name
) {
}
