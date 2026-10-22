package com.giut.server.profile.controller;

import com.giut.server.profile.dto.response.ProfileScrapResponse;
import com.giut.server.profile.service.ProfileScrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "프로필 조회·신고·공유")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile/{userId}/scrap")
@SecurityRequirement(name = "JWT")
public class ProfileScrapController {
    private final ProfileScrapService service;

    @GetMapping
    @Operation(summary = "내 관심 프로필 등록 상태 조회")
    public ProfileScrapResponse get(Authentication authentication, @PathVariable Long userId) {
        return service.getStatus(Long.valueOf(authentication.getName()), userId);
    }

    @PostMapping
    @Operation(summary = "관심 프로필 등록", description = "공개·활성·휴식 중이 아닌 타인 프로필을 저장합니다. 중복 등록은 같은 상태를 반환합니다.")
    public ProfileScrapResponse add(Authentication authentication, @PathVariable Long userId) {
        return service.add(Long.valueOf(authentication.getName()), userId);
    }

    @DeleteMapping
    @Operation(summary = "관심 프로필 해제")
    public ProfileScrapResponse remove(Authentication authentication, @PathVariable Long userId) {
        return service.remove(Long.valueOf(authentication.getName()), userId);
    }
}
