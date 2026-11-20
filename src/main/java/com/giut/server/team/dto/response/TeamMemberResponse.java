package com.giut.server.team.dto.response;

import com.giut.server.profile.entity.ProfileRole;
import com.giut.server.team.entity.TeamMember;
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

        @Schema(description = "담당 모집 분야 코드", example = "BACKEND_DEVELOPER", nullable = true)
        String roleCode,

        @Schema(description = "팀원 상태", example = "ACTIVE")
        TeamMember.Status status,

        @Schema(description = "팀 합류일시", example = "2026-09-08T10:45:00Z")
        Instant joinedAt,

        @Schema(description = "세부 담당 분야 이름", example = "백엔드 개발자", nullable = true)
        String roleName,

        @Schema(description = "상위 포지션 코드", example = "DEVELOPMENT", nullable = true)
        ProfileRole.PrimaryRole primaryRole,

        @Schema(description = "상위 포지션 이름", example = "개발", nullable = true)
        String primaryRoleName
) {
}
