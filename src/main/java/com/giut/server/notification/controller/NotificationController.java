package com.giut.server.notification.controller;

import com.giut.server.notification.dto.NotificationListResponse;
import com.giut.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

@Tag(name = "Notification")
@SecurityRequirement(name = "JWT")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final Clock clock;

    @GetMapping
    @Operation(summary = "내 알림 목록", description = "최신순 커서 페이지입니다. 다음 페이지는 응답의 nextCursor를 cursor로 보냅니다. "
            + "size 기본 20, 최대 50. 푸시를 받지 못했거나 지운 알림도 여기서 확인할 수 있습니다.")
    public ResponseEntity<NotificationListResponse> list(@RequestParam(required = false) Long cursor,
                                                         @RequestParam(required = false) Integer size,
                                                         Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(notifications.list(userId(authentication), cursor, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "안 읽은 알림 수", description = "알림 아이콘 배지에 표시할 숫자입니다.")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("count", notifications.unreadCount(userId(authentication))));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "알림함에서 열거나 푸시를 눌러 앱이 열렸을 때 호출합니다. "
            + "푸시가 도착한 것만으로는 읽음이 아닙니다. 이미 읽은 알림은 처음 읽은 시각을 유지합니다. 다른 회원의 알림은 404입니다.")
    public ResponseEntity<Void> markRead(@PathVariable Long notificationId, Authentication authentication) {
        notifications.markRead(userId(authentication), notificationId, clock.instant());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "알림 모두 읽음", description = "요청 시각까지 만들어진 안 읽은 알림을 모두 읽음 처리합니다.")
    public ResponseEntity<Map<String, Integer>> markAllRead(Authentication authentication) {
        return ResponseEntity.ok(Map.of("updatedCount", notifications.markAllRead(userId(authentication), clock.instant())));
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
