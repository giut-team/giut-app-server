package com.giut.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultDto {

    @Schema(description = "요청 성공 여부", example = "false")
    private boolean success;

    @Schema(description = "응답 메시지", example = "요청 처리에 실패했습니다.")
    private String message;

    @Schema(description = "HTTP 상태 코드", example = "400")
    private int code;
}
