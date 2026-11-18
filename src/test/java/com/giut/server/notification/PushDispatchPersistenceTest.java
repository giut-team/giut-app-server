package com.giut.server.notification;

import com.giut.server.notification.config.PushProperties;
import com.giut.server.notification.entity.Notification;
import com.giut.server.notification.entity.PushDelivery;
import com.giut.server.notification.entity.PushDelivery.Status;
import com.giut.server.notification.entity.PushDevice;
import com.giut.server.notification.push.PushGateway;
import com.giut.server.notification.push.PushGateway.PushMessage;
import com.giut.server.notification.push.PushGateway.PushReceipt;
import com.giut.server.notification.push.PushGateway.PushTicket;
import com.giut.server.notification.push.PushGatewayException;
import com.giut.server.notification.repository.NotificationRepository;
import com.giut.server.notification.repository.PushDeliveryRepository;
import com.giut.server.notification.repository.PushDeviceRepository;
import com.giut.server.notification.service.NotificationCommand;
import com.giut.server.notification.service.NotificationService;
import com.giut.server.notification.service.PushDeliveryStore;
import com.giut.server.notification.service.PushDispatchWorker;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Expo 대신 가짜 gateway로 발송 상태 전이를 H2(MySQL 모드)에서 commit된 상태로 검증한다.
 * H2 행 잠금은 InnoDB SKIP LOCKED 동시 선점을 보장하지 않는다(선점 쿼리 구조는 추출 작업과 같다).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_push_dispatch;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.push.worker-enabled=false",
        "app.push.max-attempts=3"
})
class PushDispatchPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private NotificationRepository notifications;
    @Autowired private PushDeviceRepository devices;
    @Autowired private PushDeliveryRepository deliveries;
    @Autowired private NotificationService notificationService;
    @Autowired private PushDeliveryStore store;
    @Autowired private PushDispatchWorker worker;
    @Autowired private PushProperties properties;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private PushGateway gateway;

    private User member;
    private User other;

    @BeforeEach
    void setUp() {
        member = users.saveAndFlush(User.createOAuthUser("push-member@example.com", "회원", User.OAuthProvider.KAKAO, "push-member"));
        other = users.saveAndFlush(User.createOAuthUser("push-other@example.com", "다른", User.OAuthProvider.KAKAO, "push-other"));
    }

    @AfterEach
    void cleanUp() {
        deliveries.deleteAll();
        devices.deleteAll();
        notifications.deleteAll();
        users.deleteAll();
    }

    @Test
    void createRequiresTheCallersTransaction() {
        assertThatThrownBy(() -> notificationService.createAll(List.of(command(member, "key")), Instant.now()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void sendsPushWithNotificationIdActionPathAndUnreadBadgeThenConfirmsByReceipt() {
        PushDevice phone = device(member, "install-member-1", "ExponentPushToken[member]");
        notify(member, "older");          // 안 읽은 알림이 2건이면 배지는 2
        notify(member, "newest");
        when(gateway.send(anyList())).thenAnswer(invocation -> {
            List<PushMessage> messages = invocation.getArgument(0);
            return messages.stream().map(m -> PushTicket.ok("ticket-" + m.data().get("notificationId"))).toList();
        });

        assertThat(worker.dispatchOnce()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(gateway).send(sent.capture());
        assertThat(sent.getValue()).hasSize(2).allSatisfy(message -> {
            assertThat(message.to()).isEqualTo("ExponentPushToken[member]");
            assertThat(message.title()).isEqualTo("제목");
            assertThat(message.badge()).isEqualTo(2);
            assertThat(message.data()).containsEntry("type", "COMPETITION_EXTRACTION_SUBMITTED")
                    .containsEntry("actionPath", "/competitions/extractions/7")
                    .containsKey("notificationId");
            assertThat(message.data().get("payload")).isEqualTo(Map.of("jobId", 7));
        });
        assertThat(deliveries.findAll()).allSatisfy(delivery -> {
            assertThat(delivery.getStatus()).isEqualTo(Status.SENT);
            assertThat(delivery.getTicketId()).startsWith("ticket-");
            assertThat(delivery.getAttemptCount()).isEqualTo(1);
            assertThat(delivery.getLeaseToken()).isNull();
            assertThat(delivery.getReceiptCheckAt()).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
        });
        // 보낼 것이 없으면 다시 보내지 않는다.
        assertThat(worker.dispatchOnce()).isZero();
        // 영수증 확인 시각 전에는 조회하지 않는다.
        assertThat(store.findReceiptDue(Instant.now())).isEmpty();

        Instant later = Instant.now().plus(Duration.ofMinutes(16));
        List<PushDeliveryStore.ReceiptCheck> due = store.findReceiptDue(later);
        assertThat(due).hasSize(2);
        String firstTicket = due.get(0).ticketId();
        String secondTicket = due.get(1).ticketId();
        // 하나는 ok, 하나는 아직 영수증 없음.
        store.recordReceipts(due, Map.of(firstTicket, PushReceipt.accepted()), later);
        assertThat(byTicket(firstTicket).getStatus()).isEqualTo(Status.DELIVERED);
        assertThat(byTicket(firstTicket).getCompletedAt()).isNotNull();
        assertThat(byTicket(secondTicket).getStatus()).isEqualTo(Status.SENT);
        assertThat(byTicket(secondTicket).getReceiptCheckAt()).isAfter(later);
        // Expo 영수증 보관 기간이 지나도록 없으면 확인 불가로 끝낸다.
        Instant afterRetention = Instant.now().plus(properties.receiptRetention()).plusSeconds(1);
        store.recordReceipts(store.findReceiptDue(afterRetention), Map.of(), afterRetention);
        assertThat(byTicket(secondTicket).getStatus()).isEqualTo(Status.UNCONFIRMED);
        assertThat(phone.getId()).isNotNull();
    }

    @Test
    void deviceNotRegisteredDeactivatesTheDeviceSoLaterNotificationsAreNotQueuedForIt() {
        PushDevice phone = device(member, "install-member-1", "ExponentPushToken[gone]");
        notify(member, "first");
        when(gateway.send(anyList())).thenReturn(List.of(PushTicket.error("DeviceNotRegistered", "not registered")));

        worker.dispatchOnce();

        PushDelivery failed = deliveries.findAll().getFirst();
        assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("DeviceNotRegistered");
        PushDevice reloaded = devices.findById(phone.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getDeactivationReason()).isEqualTo(PushDevice.Reasons.DEVICE_NOT_REGISTERED);

        notify(member, "second");
        assertThat(notifications.count()).isEqualTo(2); // 알림함에는 남는다
        assertThat(deliveries.count()).isEqualTo(1);    // 비활성 기기로는 발송 대기를 만들지 않는다
    }

    @Test
    void receiptErrorDeviceNotRegisteredAlsoDeactivatesTheDevice() {
        PushDevice phone = device(member, "install-member-1", "ExponentPushToken[member]");
        notify(member, "first");
        when(gateway.send(anyList())).thenReturn(List.of(PushTicket.ok("ticket-1")));
        worker.dispatchOnce();

        Instant later = Instant.now().plus(Duration.ofMinutes(16));
        store.recordReceipts(store.findReceiptDue(later), Map.of("ticket-1", PushReceipt.error("DeviceNotRegistered", "gone")), later);

        assertThat(byTicket("ticket-1").getStatus()).isEqualTo(Status.FAILED);
        assertThat(devices.findById(phone.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void retryableRequestFailureBacksOffUntilAttemptsAreExhausted() {
        device(member, "install-member-1", "ExponentPushToken[member]");
        notify(member, "first");
        when(gateway.send(anyList())).thenThrow(new PushGatewayException("HTTP_503", "Expo 503", true));
        Instant t0 = Instant.now();

        assertThat(store.claim(10, t0)).hasSize(1);
        // 위 claim은 임대 중이므로 다시 선점되지 않는다.
        assertThat(store.claim(10, t0)).isEmpty();
        store.recoverExpiredLeases(t0.plus(properties.leaseDuration()).plusSeconds(1));
        PushDelivery recovered = deliveries.findAll().getFirst();
        assertThat(recovered.getStatus()).isEqualTo(Status.PENDING);
        assertThat(recovered.getErrorCode()).isEqualTo("LEASE_EXPIRED");
        assertThat(recovered.getAttemptCount()).isEqualTo(1);

        // 대기 시각이 지나면 다시 보낸다. 503이 계속되면 시도 상한(3)에서 끝난다.
        for (int attempt = 2; attempt <= 3; attempt++) {
            Instant due = deliveries.findAll().getFirst().getNextAttemptAt();
            assertThat(store.claim(10, due.minusSeconds(1))).isEmpty();
            List<PushDeliveryStore.ClaimedPush> claimed = store.claim(10, due);
            assertThat(claimed).hasSize(1);
            store.recordRequestFailure(claimed, new PushGatewayException("HTTP_503", "Expo 503", true), due);
        }
        PushDelivery exhausted = deliveries.findAll().getFirst();
        assertThat(exhausted.getStatus()).isEqualTo(Status.FAILED);
        assertThat(exhausted.getAttemptCount()).isEqualTo(3);
        assertThat(exhausted.getErrorCode()).isEqualTo("HTTP_503");
    }

    @Test
    void retryableFailureFromWorkerSchedulesTheNextAttempt() {
        device(member, "install-member-1", "ExponentPushToken[member]");
        notify(member, "first");
        when(gateway.send(anyList())).thenThrow(new PushGatewayException("NETWORK_ERROR", "timeout", true));
        Instant before = Instant.now();

        worker.dispatchOnce();

        PushDelivery waiting = deliveries.findAll().getFirst();
        assertThat(waiting.getStatus()).isEqualTo(Status.PENDING);
        assertThat(waiting.getNextAttemptAt()).isAfter(before.plus(properties.retryBaseDelay()).minusSeconds(1));
        assertThat(waiting.getLeaseToken()).isNull();
    }

    @Test
    void nonRetryableRequestFailureFailsImmediately() {
        device(member, "install-member-1", "ExponentPushToken[member]");
        notify(member, "first");
        when(gateway.send(anyList())).thenThrow(new PushGatewayException("HTTP_401", "UNAUTHORIZED", false));

        worker.dispatchOnce();

        PushDelivery failed = deliveries.findAll().getFirst();
        assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void deviceThatChangedOwnerOrLoggedOutOrInactiveUserIsNeverSent() {
        PushDevice shared = device(member, "install-shared-1", "ExponentPushToken[shared]");
        PushDevice loggedOut = device(member, "install-member-2", "ExponentPushToken[member2]");
        notify(member, "for member");
        assertThat(deliveries.count()).isEqualTo(2);
        transactions.executeWithoutResult(status -> {
            devices.findById(shared.getId()).orElseThrow()
                    .refresh(users.findById(other.getId()).orElseThrow(), "ExponentPushToken[shared]",
                            PushDevice.Platform.IOS, null, Instant.now());
            devices.findById(loggedOut.getId()).orElseThrow().deactivate(PushDevice.Reasons.LOGGED_OUT, Instant.now());
        });

        assertThat(worker.dispatchOnce()).isZero();

        verify(gateway, never()).send(anyList());
        assertThat(deliveries.findAll()).extracting(PushDelivery::getErrorCode)
                .containsExactlyInAnyOrder("RECIPIENT_CHANGED", "DEVICE_INACTIVE");
        assertThat(deliveries.findAll()).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(Status.FAILED));
    }

    @Test
    void notificationsAreOnlyCreatedForActiveUsersAndOncePerDedupKey() {
        device(member, "install-member-1", "ExponentPushToken[member]");
        transactions.executeWithoutResult(status -> users.findById(other.getId()).orElseThrow().withdraw());

        transactions.executeWithoutResult(status -> notificationService.createAll(
                List.of(command(member, "same-key"), command(member, "same-key"), command(other, "other-key")), Instant.now()));
        transactions.executeWithoutResult(status ->
                notificationService.createAll(List.of(command(member, "same-key")), Instant.now()));

        assertThat(notifications.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getUser().getId()).isEqualTo(member.getId()));
        assertThat(deliveries.count()).isEqualTo(1);
    }

    @Test
    void stalePendingIsNotSentLateAndOldFinishedDeliveriesArePurged() {
        device(member, "install-member-1", "ExponentPushToken[member]");
        notify(member, "first");
        Instant tooLate = Instant.now().plus(properties.maxPendingAge()).plusSeconds(1);

        assertThat(store.expireStalePending(tooLate)).isEqualTo(1);
        PushDelivery expired = deliveries.findAll().getFirst();
        assertThat(expired.getStatus()).isEqualTo(Status.FAILED);
        assertThat(expired.getErrorCode()).isEqualTo("EXPIRED");
        assertThat(notifications.count()).isEqualTo(1);

        assertThat(store.purgeCompleted(tooLate)).isZero();
        assertThat(store.purgeCompleted(tooLate.plus(properties.deliveryRetention()))).isZero(); // 경계는 아직 보관
        assertThat(store.purgeCompleted(tooLate.plus(properties.deliveryRetention()).plusSeconds(1))).isEqualTo(1);
        assertThat(deliveries.count()).isZero();
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void devicesThatStopRefreshingTheirTokenAreDeactivated() {
        PushDevice phone = device(member, "install-member-1", "ExponentPushToken[member]");

        assertThat(store.deactivateStaleDevices(Instant.now().plus(properties.deviceStaleAfter()).plusSeconds(1))).isEqualTo(1);

        assertThat(devices.findById(phone.getId()).orElseThrow().getDeactivationReason()).isEqualTo(PushDevice.Reasons.STALE);
        verify(gateway, never()).receipts(anyCollection());
    }

    private PushDelivery byTicket(String ticketId) {
        return deliveries.findAll().stream().filter(d -> ticketId.equals(d.getTicketId())).findFirst().orElseThrow();
    }

    private PushDevice device(User user, String installationId, String token) {
        return devices.saveAndFlush(PushDevice.register(user, installationId, token, PushDevice.Platform.IOS, null, Instant.now()));
    }

    private void notify(User user, String key) {
        transactions.executeWithoutResult(status ->
                notificationService.createAll(List.of(command(user, key)), Instant.now()));
    }

    private static NotificationCommand command(User user, String key) {
        return new NotificationCommand(user.getId(), Notification.Type.COMPETITION_EXTRACTION_SUBMITTED, "제목", "본문",
                "/competitions/extractions/7", Map.of("jobId", 7), key);
    }
}
