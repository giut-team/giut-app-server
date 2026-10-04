package com.giut.server.team.controller;

import com.giut.server.team.dto.response.TeamScrapResponse;
import com.giut.server.team.service.TeamScrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Team", description = "팀 생성 및 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
public class TeamScrapController {

    private final TeamScrapService teamScrapService;

    @PostMapping("/{teamId}/scrap")
    @Operation(summary = "팀 스크랩 추가", description = "팀을 내 스크랩에 저장합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<TeamScrapResponse> scrap(
            Authentication authentication,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(teamScrapService.scrap(Long.valueOf(authentication.getName()), teamId));
    }

    @DeleteMapping("/{teamId}/scrap")
    @Operation(summary = "팀 스크랩 삭제", description = "팀을 내 스크랩에서 제거합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<TeamScrapResponse> removeScrap(
            Authentication authentication,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(teamScrapService.removeScrap(Long.valueOf(authentication.getName()), teamId));
    }
}
