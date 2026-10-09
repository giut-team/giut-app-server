package com.giut.server.profile.controller;

import com.giut.server.global.swagger.SwaggerExamples;
import com.giut.server.profile.dto.response.ProfileShareLinkResponse;
import com.giut.server.profile.dto.response.SharedProfileResponse;
import com.giut.server.profile.service.ProfileShareLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 조회·신고·공유")
@RestController
@RequiredArgsConstructor
public class ProfileShareController {

    private final ProfileShareLinkService profileShareLinkService;

    @PostMapping("/api/users/me/profile-share-links")
    @Operation(summary = "프로필 공유 링크 발급", description = "3시간 동안 유효한 새 공유 토큰을 발급합니다. 토큰을 가진 비회원은 만료 전까지 공유 프로필을 볼 수 있습니다. 검색 노출 여부와 무관하며, 프론트엔드는 반환된 token으로 공유 URL을 구성합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ProfileShareLinkResponse> createLink(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.status(201).body(profileShareLinkService.createLink(userId));
    }

    @PostMapping("/api/users/{userId}/profile-share-links")
    @Operation(summary = "다른 사용자 프로필 공유 링크 발급", description = "공개 프로필에 대해 3시간 동안 유효한 공유 토큰을 발급합니다. 비공개·휴식 중·비활성 사용자의 프로필은 공유할 수 없습니다. 반환된 token으로 공유 URL을 구성합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ProfileShareLinkResponse> createLinkForProfile(@PathVariable Long userId) {
        return ResponseEntity.status(201).body(profileShareLinkService.createLinkForPublicProfile(userId));
    }

    @DeleteMapping("/api/users/me/profile-share-links/{linkId}")
    @Operation(summary = "내 프로필 공유 링크 해제")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<Void> revokeLink(Authentication authentication, @PathVariable Long linkId) {
        Long userId = Long.valueOf(authentication.getName());
        profileShareLinkService.revokeLink(userId, linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/profile/shares/{token}")
    @Operation(summary = "비회원 공유 프로필 조회", description = "유효한 공유 링크 토큰으로 프로필 카드 정보만 조회합니다. 포트폴리오와 활동 이력은 반환하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "공유 프로필 조회 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SharedProfileResponse.class), examples = @ExampleObject(value = SwaggerExamples.SHARED_PROFILE)))
    public ResponseEntity<SharedProfileResponse> getSharedProfile(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(profileShareLinkService.getSharedProfile(token));
    }
}
