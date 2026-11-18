package com.giut.server.notification.service;

import com.giut.server.notification.config.PushProperties;
import com.giut.server.notification.entity.Notification;
import com.giut.server.notification.entity.PushDelivery;
import com.giut.server.notification.entity.PushDelivery.Status;
import com.giut.server.notification.entity.PushDevice;
import com.giut.server.notification.push.PushGateway.Errors;
import com.giut.server.notification.push.PushGateway.PushMessage;
import com.giut.server.notification.push.PushGateway.PushReceipt;
import com.giut.server.notification.push.PushGateway.PushTicket;
import com.giut.server.notification.push.PushGatewayException;
import com.giut.server.notification.repository.PushDeliveryRepository;
import com.giut.server.notification.repository.PushDeviceRepository;
import com.giut.server.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 푸시 발송 기록의 모든 DB 상태 전이. 각 public 메서드가 하나의 짧은 트랜잭션이며 외부 I/O(Expo)를 하지 않는다.
 * 작업자와 다른 bean으로 두어 트랜잭션 proxy가 항상 적용되게 한다(추출 작업의 JobStore와 같은 구조).
 */
@Component
@RequiredArgsConstructor
public class PushDeliveryStore {
    private static final int CANDIDATE_FACTOR = 4;
    private static final int MAX_CANDIDATES = 400;

    private final PushDeliveryRepository deliveries;
    private final PushDeviceRepository devices;
    private final NotificationService notificationService;
    private final PushProperties properties;

    /** 이번 실행이 선점한 발송. leaseToken이 일치할 때만 결과를 저장한다. */
    public record ClaimedPush(Long deliveryId, String leaseToken, PushMessage message) {
    }

    public record ReceiptCheck(Long deliveryId, String ticketId) {
    }

    /**
     * 보낼 차례인 PENDING을 최대 limit건 SENDING으로 선점하고 보낼 메시지를 만든다. 보내기 직전에 수신 조건을 다시 확인한다.
     * 기기가 비활성화됐거나, 다른 회원 계정으로 넘어갔거나, 수신 회원이 활성 상태가 아니면 보내지 않고 실패로 끝낸다.
     */
    @Transactional
    public List<ClaimedPush> claim(int limit, Instant now) {
        List<PushDelivery> locked = lockMatching(
                deliveries.findDueIds(now, Math.min(limit * CANDIDATE_FACTOR, MAX_CANDIDATES)),
                delivery -> delivery.getStatus() == Status.PENDING && delivery.getNextAttemptAt() != null
                        && !delivery.getNextAttemptAt().isAfter(now),
                limit);
        if (locked.isEmpty()) {
            return List.of();
        }
        List<PushDelivery> sendable = new ArrayList<>();
        for (PushDelivery delivery : deliveries.findWithTargets(locked.stream().map(PushDelivery::getId).toList())) {
            String blocked = blockedReason(delivery);
            if (blocked == null) {
                sendable.add(delivery);
            } else {
                delivery.fail(blocked, "보내기 직전 수신 조건을 만족하지 않아 보내지 않았습니다.", now);
            }
        }
        Map<Long, Long> unread = notificationService.unreadCounts(
                sendable.stream().map(d -> d.getNotification().getUser().getId()).collect(Collectors.toSet()));
        List<ClaimedPush> claimed = new ArrayList<>(sendable.size());
        for (PushDelivery delivery : sendable) {
            String token = delivery.claim(now.plus(properties.leaseDuration()), now);
            claimed.add(new ClaimedPush(delivery.getId(), token, toMessage(delivery,
                    unread.getOrDefault(delivery.getNotification().getUser().getId(), 0L))));
        }
        return claimed;
    }

    /**
     * Expo 발송 결과를 반영한다. 입력과 tickets는 같은 순서다. 임대를 잃은(회수·재선점된) 행은 건드리지 않는다.
     *
     * @return 자격 증명 설정 오류 건수(운영자 조치 필요)
     */
    @Transactional
    public int recordTickets(List<ClaimedPush> claimed, List<PushTicket> tickets, Instant now) {
        int configurationErrors = 0;
        for (int i = 0; i < claimed.size(); i++) {
            ClaimedPush push = claimed.get(i);
            PushTicket ticket = tickets.get(i);
            PushDelivery delivery = leased(push);
            if (delivery == null) {
                continue;
            }
            if (ticket.ok()) {
                delivery.markSent(ticket.id(), now.plus(properties.receiptDelay()), now);
            } else {
                configurationErrors += applyError(delivery, ticket.error(), ticket.message(), now);
            }
        }
        return configurationErrors;
    }

    /** 요청 전체가 실패했다. 재시도 가능한 오류면 backoff 뒤 다시 대기시키고, 아니면 실패로 끝낸다. */
    @Transactional
    public void recordRequestFailure(List<ClaimedPush> claimed, PushGatewayException failure, Instant now) {
        for (ClaimedPush push : claimed) {
            PushDelivery delivery = leased(push);
            if (delivery == null) {
                continue;
            }
            if (failure.retryable()) {
                delivery.retryOrFail(properties.maxAttempts(), now.plus(properties.retryDelay(delivery.getAttemptCount())),
                        failure.code(), failure.getMessage(), now);
            } else {
                delivery.fail(failure.code(), failure.getMessage(), now);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ReceiptCheck> findReceiptDue(Instant now) {
        return deliveries.findReceiptDue(now, Limit.of(properties.receiptBatchSize())).stream()
                .map(delivery -> new ReceiptCheck(delivery.getId(), delivery.getTicketId())).toList();
    }

    /**
     * 영수증을 반영한다. 아직 영수증이 없으면 다음 확인을 미루고, Expo 보관 기간이 지나도록 없으면 UNCONFIRMED로 끝낸다.
     *
     * @return 자격 증명 설정 오류 건수
     */
    @Transactional
    public int recordReceipts(List<ReceiptCheck> checks, Map<String, PushReceipt> receipts, Instant now) {
        int configurationErrors = 0;
        for (ReceiptCheck check : checks) {
            PushDelivery delivery = deliveries.lockById(check.deliveryId())
                    .filter(found -> found.getStatus() == Status.SENT && check.ticketId().equals(found.getTicketId()))
                    .orElse(null);
            if (delivery == null) {
                continue;
            }
            PushReceipt receipt = receipts.get(check.ticketId());
            if (receipt == null) {
                if (!delivery.getSentAt().plus(properties.receiptRetention()).isAfter(now)) {
                    delivery.markUnconfirmed(now);
                } else {
                    delivery.postponeReceiptCheck(now.plus(properties.receiptDelay()), now);
                }
            } else if (receipt.ok()) {
                delivery.markDelivered(now);
            } else {
                configurationErrors += applyError(delivery, receipt.error(), receipt.message(), now);
            }
        }
        return configurationErrors;
    }

    /** 결과를 저장하기 전에 서버가 멈춘 SENDING을 회수한다. Expo가 이미 받았을 수 있어 같은 푸시가 한 번 더 갈 수 있다. */
    @Transactional
    public int recoverExpiredLeases(Instant now) {
        List<PushDelivery> expired = lockMatching(deliveries.findExpiredLeaseIds(now, properties.maintenanceBatchSize()),
                delivery -> delivery.getStatus() == Status.SENDING && delivery.getLeaseExpiresAt() != null
                        && !delivery.getLeaseExpiresAt().isAfter(now),
                properties.maintenanceBatchSize());
        expired.forEach(delivery -> delivery.retryOrFail(properties.maxAttempts(),
                now.plus(properties.retryDelay(delivery.getAttemptCount())), "LEASE_EXPIRED",
                "발송 결과를 저장하기 전에 임대가 만료됐습니다.", now));
        return expired.size();
    }

    /** 너무 늦어진 푸시는 보내지 않는다. 알림함 기록은 그대로 남는다. */
    @Transactional
    public int expireStalePending(Instant now) {
        Instant cutoff = now.minus(properties.maxPendingAge());
        List<PushDelivery> stale = lockMatching(deliveries.findStalePendingIds(cutoff, properties.maintenanceBatchSize()),
                delivery -> delivery.getStatus() == Status.PENDING && !delivery.getCreatedAt().isAfter(cutoff),
                properties.maintenanceBatchSize());
        stale.forEach(delivery -> delivery.fail("EXPIRED", "발송 가능 시간이 지나 보내지 않았습니다.", now));
        return stale.size();
    }

    @Transactional
    public int purgeCompleted(Instant now) {
        List<Long> ids = deliveries.findCompletedBefore(now.minus(properties.deliveryRetention()),
                Limit.of(properties.maintenanceBatchSize()));
        if (!ids.isEmpty()) {
            deliveries.deleteAllByIdInBatch(ids);
        }
        return ids.size();
    }

    @Transactional
    public int deactivateStaleDevices(Instant now) {
        List<PushDevice> stale = devices.findStale(now.minus(properties.deviceStaleAfter()),
                Limit.of(properties.maintenanceBatchSize()));
        stale.forEach(device -> device.deactivate(PushDevice.Reasons.STALE, now));
        return stale.size();
    }

    /** @return 자격 증명 설정 오류면 1 */
    private int applyError(PushDelivery delivery, String error, String message, Instant now) {
        String code = error == null ? "EXPO_ERROR" : truncate(error, 40);
        if (Errors.DEVICE_NOT_REGISTERED.equals(error)) {
            // 앱 삭제·권한 회수 등으로 토큰이 더는 유효하지 않다. 앱이 다시 등록할 때까지 보내지 않는다.
            delivery.fail(code, message, now);
            delivery.getDevice().deactivate(PushDevice.Reasons.DEVICE_NOT_REGISTERED, now);
            return 0;
        }
        if (Errors.MESSAGE_RATE_EXCEEDED.equals(error)) {
            delivery.retryOrFail(properties.maxAttempts(), now.plus(properties.retryDelay(delivery.getAttemptCount())),
                    code, message, now);
            return 0;
        }
        delivery.fail(code, message, now);
        return Errors.isConfiguration(error) ? 1 : 0;
    }

    private PushDelivery leased(ClaimedPush push) {
        return deliveries.lockById(push.deliveryId()).filter(found -> found.isLeasedBy(push.leaseToken())).orElse(null);
    }

    private static String blockedReason(PushDelivery delivery) {
        PushDevice device = delivery.getDevice();
        Notification notification = delivery.getNotification();
        if (!device.isActive()) {
            return "DEVICE_INACTIVE";
        }
        if (!device.getUser().getId().equals(notification.getUser().getId())) {
            return "RECIPIENT_CHANGED";
        }
        if (notification.getUser().getStatus() != User.Status.ACTIVE) {
            return "USER_INACTIVE";
        }
        return null;
    }

    private PushMessage toMessage(PushDelivery delivery, long unread) {
        Notification notification = delivery.getNotification();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificationId", notification.getId());
        data.put("type", notification.getNotificationType().name());
        if (notification.getActionPath() != null) {
            data.put("actionPath", notification.getActionPath());
        }
        Map<String, Object> payload = notificationService.readPayload(notification.getPayload());
        if (!payload.isEmpty()) {
            data.put("payload", payload);
        }
        return new PushMessage(delivery.getDevice().getPushToken(), notification.getTitle(), notification.getBody(), data,
                (int) Math.min(unread, Integer.MAX_VALUE));
    }

    /** 후보를 PK 단건 SKIP LOCKED로 잠그고, 잠근 시점의 최신 값이 여전히 조건에 맞는 행만 최대 limit건 돌려준다. */
    private List<PushDelivery> lockMatching(List<Long> candidates, Predicate<PushDelivery> condition, int limit) {
        List<PushDelivery> locked = new ArrayList<>();
        for (Long id : candidates) {
            if (locked.size() >= limit) {
                break;
            }
            deliveries.lockIfAvailable(id).filter(condition).ifPresent(locked::add);
        }
        return locked;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
