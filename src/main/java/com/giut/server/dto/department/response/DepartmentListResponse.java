package com.giut.server.dto.department.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "학과 목록 조회 응답")
public record DepartmentListResponse(
        List<DepartmentResponse> departments
) {
}
