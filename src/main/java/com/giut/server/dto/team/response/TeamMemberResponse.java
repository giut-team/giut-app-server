package com.giut.server.dto.team.response;

import com.giut.server.entity.TeamMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "팀원 응답")
public record TeamMemberResponse(
        @Schema(description = "팀원 관계 ID", example = "3")
        Long teamMemberId,

        @Schema(description = "사용자 ID", example = "15")
        Long userId,

        @Schema(description = "사용자 닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "팀 내 역할", example = "MEMBER")
        TeamMember.Role role,

        @Schema(description = "팀원 상태", example = "ACTIVE")
        TeamMember.Status status,

        @Schema(description = "팀 합류일시", example = "2026-09-08T10:45:00Z")
        Instant joinedAt
) {
}
