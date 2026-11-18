package com.giut.server.notification;

import com.giut.server.notification.entity.Notification;
import com.giut.server.notification.entity.PushDevice;
import com.giut.server.notification.push.PushGateway;
import com.giut.server.notification.repository.NotificationRepository;
import com.giut.server.notification.repository.PushDeliveryRepository;
import com.giut.server.notification.repository.PushDeviceRepository;
import com.giut.server.notification.service.NotificationCommand;
import com.giut.server.notification.service.NotificationService;
import com.giut.server.notification.service.PushDeliveryStore;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in only: 루프백의 폐기 가능한 MySQL 스키마만 사용한다. 공유·운영 DB를 가리키게 바꾸지 않는다.
 * 실행: GIUT_EXTRACTION_MYSQL_TEST=true, 127.0.0.1:33397 root/giut-local-test, DB giut_notification_test·giut_v30_check.
 */
@EnabledIfEnvironmentVariable(named = "GIUT_EXTRACTION_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33397/giut_notification_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.push.worker-enabled=false"
})
class NotificationMySqlTest {
    private static final String ROOT_URL = "jdbc:mysql://127.0.0.1:33397/%s?useSSL=false&allowPublicKeyRetrieval=true"
            + "&createDatabaseIfNotExist=true";

    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired PushDeviceRepository devices;
    @Autowired PushDeliveryRepository deliveries;
    @Autowired NotificationService notificationService;
    @Autowired PushDeliveryStore store;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PushGateway gateway;

    private User member;

    @BeforeEach
    void setUp() {
        cleanUp();
        member = users.saveAndFlush(User.createOAuthUser("mysql-notify@example.com", "학생", User.OAuthProvider.KAKAO, "mysql-notify"));
    }

    @AfterEach
    void cleanUp() {
        deliveries.deleteAll();
        devices.deleteAll();
        notifications.deleteAll();
        users.deleteAll();
    }

    @Test
    void enumColumnsAreVarcharAndPayloadIsStoredAsJsonObject() {
        assertThat(columnType("notifications", "notification_type")).isEqualTo("varchar");
        assertThat(columnType("push_devices", "platform")).isEqualTo("varchar");
        assertThat(columnType("push_deliveries", "status")).isEqualTo("varchar");

        transactions.executeWithoutResult(status -> notificationService.createAll(List.of(new NotificationCommand(
                member.getId(), Notification.Type.COMPETITION_EXTRACTION_SUBMITTED, "제목", "본문", "/competitions/extractions/1",
                Map.of("jobId", 1, "sourceUrl", "https://example.com/contest"), "mysql-key")), Instant.now()));

        // 문자열로 한 번 더 감싼 JSON("\"{...}\"")이 아니라 JSON 객체로 저장돼야 MySQL JSON 함수로 읽을 수 있다.
        assertThat(jdbc.queryForObject("SELECT JSON_TYPE(payload) FROM notifications", String.class)).isEqualTo("OBJECT");
        assertThat(jdbc.queryForObject("SELECT JSON_EXTRACT(payload, '$.jobId') FROM notifications", Integer.class)).isEqualTo(1);
        Notification saved = notifications.findAll().getFirst();
        assertThat(notificationService.readPayload(saved.getPayload())).containsEntry("jobId", 1)
                .containsEntry("sourceUrl", "https://example.com/contest");
    }

    @Test
    void concurrentClaimsTakeDisjointDeliveries() throws Exception {
        devices.saveAndFlush(PushDevice.register(member, "install-mysql-1", "ExponentPushToken[mysql]",
                PushDevice.Platform.ANDROID, null, Instant.now()));
        List<NotificationCommand> commands = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            commands.add(new NotificationCommand(member.getId(), Notification.Type.SYSTEM, "제목 " + i, "본문", null, null,
                    "mysql-claim-" + i));
        }
        transactions.executeWithoutResult(status -> notificationService.createAll(commands, Instant.now()));
        assertThat(deliveries.count()).isEqualTo(30);

        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        Instant now = Instant.now().plusSeconds(1);
        List<Future<List<PushDeliveryStore.ClaimedPush>>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            Callable<List<PushDeliveryStore.ClaimedPush>> claim = () -> {
                start.await();
                return store.claim(10, now);
            };
            futures.add(pool.submit(claim));
        }
        start.countDown();
        Set<Long> claimed = new HashSet<>();
        int total = 0;
        for (Future<List<PushDeliveryStore.ClaimedPush>> future : futures) {
            for (PushDeliveryStore.ClaimedPush push : future.get(30, TimeUnit.SECONDS)) {
                claimed.add(push.deliveryId());
                total++;
            }
        }
        pool.shutdown();
        // 같은 행을 두 실행이 선점하지 않는다(SKIP LOCKED). 잠긴 후보를 건너뛰므로 한 번에 30건 전부가 아닐 수 있다.
        assertThat(total).isEqualTo(claimed.size());
        assertThat(total).isPositive().isLessThanOrEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_deliveries WHERE status = 'SENDING'", Integer.class))
                .isEqualTo(total);
    }

    @Test
    void manualV30ScriptUpgradesAnOldEnumNotificationTableAndIsRepeatable() throws Exception {
        DriverManagerDataSource scratch = new DriverManagerDataSource(String.format(ROOT_URL, "giut_v30_check"),
                "root", "giut-local-test");
        JdbcTemplate check = new JdbcTemplate(scratch);
        check.execute("DROP TABLE IF EXISTS push_deliveries");
        check.execute("DROP TABLE IF EXISTS push_devices");
        check.execute("DROP TABLE IF EXISTS notifications");
        check.execute("DROP TABLE IF EXISTS users");
        check.execute("CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY)");
        // 이전 ddl-auto=update가 만들었을 수 있는 형태: notification_type이 이전 유형만 가진 MySQL ENUM.
        check.execute("""
                CREATE TABLE notifications (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    notification_type ENUM('TEAM_INVITED','SYSTEM') NOT NULL,
                    title VARCHAR(120) NOT NULL, body TEXT NOT NULL, action_path TEXT NULL, payload JSON NULL,
                    dedup_key VARCHAR(200) NULL, read_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
                    CONSTRAINT uk_notifications_dedup_key UNIQUE (dedup_key),
                    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id))
                """);
        check.update("INSERT INTO users () VALUES ()");
        check.update("INSERT INTO notifications (user_id, notification_type, title, body, created_at) VALUES (1, 'SYSTEM', 't', 'b', NOW(6))");

        FileSystemResource script = new FileSystemResource("src/main/resources/db/manual/V30__create_push_notifications.sql");
        for (int run = 0; run < 2; run++) { // 두 번 적용해도 실패하지 않는다.
            try (Connection connection = scratch.getConnection()) {
                ScriptUtils.executeSqlScript(connection, script);
            }
        }

        assertThat(check.queryForObject("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = 'giut_v30_check' AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'notification_type'
                """, String.class)).isEqualTo("varchar");
        check.update("INSERT INTO notifications (user_id, notification_type, title, body, created_at) "
                + "VALUES (1, 'COMPETITION_EXTRACTION_NEEDS_REVIEW', 't', 'b', NOW(6))");
        assertThat(check.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(2);
        assertThat(check.queryForObject("SELECT notification_type FROM notifications WHERE id = 1", String.class)).isEqualTo("SYSTEM");
        assertThat(check.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'giut_v30_check' "
                + "AND TABLE_NAME IN ('push_devices', 'push_deliveries')", Integer.class)).isEqualTo(2);
        check.execute("DROP DATABASE giut_v30_check");
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, String.class, table, column);
    }
}
