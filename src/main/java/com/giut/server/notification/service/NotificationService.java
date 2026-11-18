package com.giut.server.notification.service;

import com.giut.server.global.exception.ResourceNotFoundException;
import com.giut.server.notification.dto.NotificationListResponse;
import com.giut.server.notification.dto.NotificationResponse;
import com.giut.server.notification.entity.Notification;
import com.giut.server.notification.entity.PushDelivery;
import com.giut.server.notification.entity.PushDevice;
import com.giut.server.notification.repository.NotificationRepository;
import com.giut.server.notification.repository.PushDeliveryRepository;
import com.giut.server.notification.repository.PushDeviceRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 앱 내 알림함과 푸시 발송 대기(outbox) 생성.
 *
 * <p>{@link #createAll}은 원인 도메인의 트랜잭션 안에서만 호출된다({@code MANDATORY}). 원인 상태 변경(예: 추출 완료)과
 * 알림·발송 대기가 함께 commit되거나 함께 롤백되므로, "완료됐는데 알림이 없음"이나 "롤백됐는데 알림이 감" 상태가 생기지 않는다.
 * 외부 호출(Expo)은 여기서 하지 않고 {@link PushDispatchWorker}가 commit 뒤에 따로 한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final NotificationRepository notifications;
    private final PushDeviceRepository devices;
    private final PushDeliveryRepository deliveries;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    /**
     * 활성 회원에게만 알림을 만들고, 그 회원의 활성 기기마다 발송 대기를 만든다. 이미 있는 dedupKey는 건너뛴다.
     * 같은 사건의 중복은 원인 행 잠금(추출 작업 행 등)이 직렬화하므로 존재 확인 뒤 저장해도 UNIQUE 충돌이 나지 않는다.
     * UNIQUE는 마지막 방어선이며, 충돌하면 원인 트랜잭션 전체가 롤백된다.
     *
     * @return 새로 만든 알림 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int createAll(List<NotificationCommand> commands, Instant now) {
        if (commands.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(notifications.findExistingDedupKeys(
                commands.stream().map(NotificationCommand::dedupKey).filter(Objects::nonNull).toList()));
        Map<Long, User> activeUsers = users.findAllById(commands.stream().map(NotificationCommand::userId).toList())
                .stream().filter(user -> user.getStatus() == User.Status.ACTIVE)
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Notification> created = new ArrayList<>();
        for (NotificationCommand command : commands) {
            User user = activeUsers.get(command.userId());
            if (user == null || (command.dedupKey() != null && !existing.add(command.dedupKey()))) {
                continue;
            }
            created.add(Notification.create(user, command.type(), truncate(command.title(), Notification.MAX_TITLE_LENGTH),
                    command.body(), command.actionPath(), writePayload(command.payload()), command.dedupKey(), now));
        }
        if (created.isEmpty()) {
            return 0;
        }
        notifications.saveAll(created);

        Map<Long, List<PushDevice>> devicesByUser = devices.findActiveByUserIds(
                        created.stream().map(n -> n.getUser().getId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.groupingBy(device -> device.getUser().getId()));
        List<PushDelivery> pending = new ArrayList<>();
        for (Notification notification : created) {
            for (PushDevice device : devicesByUser.getOrDefault(notification.getUser().getId(), List.of())) {
                pending.add(PushDelivery.pending(notification, device, now));
            }
        }
        deliveries.saveAll(pending);
        return created.size();
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, Long cursor, Integer size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        if (cursor != null && cursor < 1) {
            throw new IllegalArgumentException("cursor가 올바르지 않습니다.");
        }
        // 한 건 더 읽어 다음 페이지가 있는지 판단한다(count 쿼리 없음).
        List<Notification> rows = notifications.findPage(userId, cursor, Limit.of(pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<Notification> page = hasNext ? rows.subList(0, pageSize) : rows;
        List<NotificationResponse> items = page.stream()
                .map(n -> NotificationResponse.of(n, readPayload(n.getPayload()))).toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new NotificationListResponse(items, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUser_IdAndReadAtIsNull(userId);
    }

    /** 다른 회원의 알림이나 없는 알림은 존재를 드러내지 않도록 같은 404로 응답한다. */
    @Transactional
    public void markRead(Long userId, Long notificationId, Instant now) {
        notifications.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."))
                .markRead(now);
    }

    @Transactional
    public int markAllRead(Long userId, Instant now) {
        return notifications.markAllRead(userId, now);
    }

    /** 회원 id → 안 읽은 알림 수. 푸시 배지 숫자에 쓴다. */
    @Transactional(readOnly = true)
    public Map<Long, Long> unreadCounts(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return notifications.countUnreadByUserIds(userIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private String writePayload(Map<String, Object> payload) {
        return payload == null || payload.isEmpty() ? null : objectMapper.writeValueAsString(payload);
    }

    public Map<String, Object> readPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, MAP);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
