package com.giut.server.controller;

import com.giut.server.dto.ResultDto;
import com.giut.server.dto.department.response.DepartmentListResponse;
import com.giut.server.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Department")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "서울시립대학교 학과 목록 조회", description = "프로필 등록 화면에서 선택할 학부 과정의 학과 Enum 목록을 조회합니다.")
    @SecurityRequirement(name = "JWT")
    @ApiResponses({
            // 성공 응답
            @ApiResponse(
                    responseCode = "200",
                    description = "학과 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DepartmentListResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"departments\":[{\"code\":\"PUBLIC_ADMINISTRATION\",\"name\":\"행정학과\"},{\"code\":\"COMPUTER_SCIENCE\",\"name\":\"컴퓨터과학부\"},{\"code\":\"ARTIFICIAL_INTELLIGENCE\",\"name\":\"인공지능학과\"}]}"
                            )
                    )
            ),
            // 실패 응답
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 토큰 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"code\":401}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResultDto.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Internal server error\",\"code\":500}")
                    )
            )
    })
    public ResponseEntity<DepartmentListResponse> getDepartments() {
        return ResponseEntity.ok(departmentService.getDepartments());
    }
}
