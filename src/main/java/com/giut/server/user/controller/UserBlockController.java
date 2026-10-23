package com.giut.server.user.controller;

import com.giut.server.user.dto.response.BlockedUserListResponse;
import com.giut.server.user.dto.response.UserBlockResponse;
import com.giut.server.user.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "User Block", description = "프로필·추천에만 적용되는 사용자 차단. 채팅·팀 기능은 변경하지 않습니다.")
@SecurityRequirement(name = "JWT")
@RequestMapping("/api/users")
public class UserBlockController {
    private final UserBlockService service;

    @PostMapping("/{userId}/block")
    @Operation(summary = "사용자 차단", description = "서로의 공개 프로필·추천 접근을 제한합니다. 본인 차단은 불가하며 중복 요청은 안전합니다. 기존 추천·메시지·팀 기록은 삭제하지 않습니다. 비회원 공유 카드는 기존 토큰 정책을 유지합니다.")
    public ResponseEntity<UserBlockResponse> block(Authentication authentication, @PathVariable @Positive Long userId) {
        return response(service.block(currentUserId(authentication), userId));
    }

    @DeleteMapping("/{userId}/block")
    @Operation(summary = "내가 한 사용자 차단 해제", description = "내 차단만 해제합니다. 상대방도 나를 차단했다면 프로필·추천 제한은 유지됩니다.")
    public ResponseEntity<UserBlockResponse> unblock(Authentication authentication, @PathVariable @Positive Long userId) {
        return response(service.unblock(currentUserId(authentication), userId));
    }

    @GetMapping("/{userId}/block")
    @Operation(summary = "내 차단 여부 조회", description = "내가 이 사용자를 차단했는지만 반환하며 상대의 차단 여부는 노출하지 않습니다.")
    public ResponseEntity<UserBlockResponse> status(Authentication authentication, @PathVariable @Positive Long userId) {
        return response(service.getStatus(currentUserId(authentication), userId));
    }

    @GetMapping("/me/blocks")
    @Operation(summary = "내가 차단한 사용자 목록", description = "page는 0부터, 페이지당 20명. 최근 차단순으로 조회합니다.")
    public ResponseEntity<BlockedUserListResponse> list(Authentication authentication, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return response(service.getBlockedUsers(currentUserId(authentication), page));
    }

    private Long currentUserId(Authentication authentication) { return Long.valueOf(authentication.getName()); }
    private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
