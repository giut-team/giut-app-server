package com.giut.server.notification;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.notification.entity.Notification;
import com.giut.server.notification.entity.PushDevice;
import com.giut.server.notification.repository.NotificationRepository;
import com.giut.server.notification.repository.PushDeliveryRepository;
import com.giut.server.notification.repository.PushDeviceRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 Spring Security/JWT·MockMvc·H2(MySQL 모드)에서 알림함·기기 등록 API 계약을 commit된 상태로 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_notifications_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.push.worker-enabled=false"
})
class NotificationApiPersistenceTest {
    private static final String NOTIFICATIONS = "/api/notifications";
    private static final String DEVICES = "/api/push-devices";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private NotificationRepository notifications;
    @Autowired private PushDeviceRepository devices;
    @Autowired private PushDeliveryRepository deliveries;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private User member;
    private User other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        member = verified("notify-member", "notify-member@uos.ac.kr");
        other = verified("notify-other", "notify-other@uos.ac.kr");
    }

    @AfterEach
    void cleanUp() {
        deliveries.deleteAll();
        devices.deleteAll();
        notifications.deleteAll();
        users.deleteAll();
    }

    @Test
    void listIsNewestFirstCursorPagedAndOnlyShowsOwnNotifications() throws Exception {
        List<Notification> mine = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            mine.add(save(member, "알림 " + i, "{\"jobId\":" + i + "}"));
        }
        save(other, "다른 회원 알림", null);

        JsonNode first = body(mvc.perform(as(get(NOTIFICATIONS).param("size", "2"), member))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].title").value("알림 5"))
                .andExpect(jsonPath("$.notifications[0].type").value("COMPETITION_EXTRACTION_SUBMITTED"))
                .andExpect(jsonPath("$.notifications[0].actionPath").value("/competitions/extractions/1"))
                .andExpect(jsonPath("$.notifications[0].payload.jobId").value(5))
                .andExpect(jsonPath("$.notifications[0].read").value(false))
                .andExpect(jsonPath("$.notifications[1].title").value("알림 4"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn());
        long cursor = first.get("nextCursor").asLong();
        assertThat(cursor).isEqualTo(mine.get(3).getId());

        mvc.perform(as(get(NOTIFICATIONS).param("size", "2").param("cursor", Long.toString(cursor)), member))
                .andExpect(jsonPath("$.notifications[0].title").value("알림 3"))
                .andExpect(jsonPath("$.notifications[1].title").value("알림 2"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(as(get(NOTIFICATIONS).param("size", "2").param("cursor", mine.get(1).getId().toString()), member))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].title").value("알림 1"))
                .andExpect(jsonPath("$.notifications[0].payload.jobId").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mvc.perform(as(get(NOTIFICATIONS).param("size", "51"), member)).andExpect(status().isBadRequest());
        mvc.perform(as(get(NOTIFICATIONS).param("size", "0"), member)).andExpect(status().isBadRequest());
        mvc.perform(as(get(NOTIFICATIONS).param("cursor", "abc"), member)).andExpect(status().isBadRequest());
        mvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
    }

    @Test
    void readMarksOnlyOwnNotificationAndReadAllKeepsCountsConsistent() throws Exception {
        Notification first = save(member, "첫 알림", null);
        save(member, "둘째 알림", null);
        Notification othersNotification = save(other, "다른 회원 알림", null);

        mvc.perform(as(get(NOTIFICATIONS + "/unread-count"), member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));

        mvc.perform(as(patch(NOTIFICATIONS + "/" + first.getId() + "/read"), member)).andExpect(status().isNoContent());
        Instant firstReadAt = notifications.findById(first.getId()).orElseThrow().getReadAt();
        assertThat(firstReadAt).isNotNull();
        // 다시 읽어도 처음 읽은 시각을 유지한다.
        mvc.perform(as(patch(NOTIFICATIONS + "/" + first.getId() + "/read"), member)).andExpect(status().isNoContent());
        assertThat(notifications.findById(first.getId()).orElseThrow().getReadAt()).isEqualTo(firstReadAt);

        // 다른 회원의 알림과 없는 알림은 같은 404다.
        mvc.perform(as(patch(NOTIFICATIONS + "/" + othersNotification.getId() + "/read"), member))
                .andExpect(status().isNotFound());
        mvc.perform(as(patch(NOTIFICATIONS + "/999999/read"), member)).andExpect(status().isNotFound());
        assertThat(notifications.findById(othersNotification.getId()).orElseThrow().getReadAt()).isNull();

        mvc.perform(as(get(NOTIFICATIONS + "/unread-count"), member)).andExpect(jsonPath("$.count").value(1));
        mvc.perform(as(patch(NOTIFICATIONS + "/read-all"), member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(1));
        mvc.perform(as(get(NOTIFICATIONS + "/unread-count"), member)).andExpect(jsonPath("$.count").value(0));
        mvc.perform(as(get(NOTIFICATIONS + "/unread-count"), other)).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void deviceRegistrationUpsertsPerInstallationAndHandlesTokenAndAccountChanges() throws Exception {
        String installation = "install-0001";
        mvc.perform(json(put(DEVICES + "/" + installation), member,
                        "{\"pushToken\":\"ExponentPushToken[aaa]\",\"platform\":\"IOS\",\"appVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationId").value(installation))
                .andExpect(jsonPath("$.platform").value("IOS"))
                .andExpect(jsonPath("$.active").value(true));
        // 토큰이 바뀌면 같은 행을 갱신한다.
        mvc.perform(json(put(DEVICES + "/" + installation), member,
                        "{\"pushToken\":\"ExponentPushToken[bbb]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isOk());
        assertThat(devices.findAll()).singleElement().satisfies(device -> {
            assertThat(device.getPushToken()).isEqualTo("ExponentPushToken[bbb]");
            assertThat(device.getUser().getId()).isEqualTo(member.getId());
            assertThat(device.getAppVersion()).isNull();
        });

        // 같은 폰에서 다른 계정으로 로그인하면 소유자가 바뀐다.
        mvc.perform(json(put(DEVICES + "/" + installation), other,
                        "{\"pushToken\":\"ExponentPushToken[bbb]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isOk());
        assertThat(devices.findAll()).singleElement()
                .satisfies(device -> assertThat(device.getUser().getId()).isEqualTo(other.getId()));

        // 재설치로 설치 id가 바뀌었는데 토큰이 같으면 이전 설치 행을 비활성화한다(중복 발송 방지).
        mvc.perform(json(put(DEVICES + "/install-0002"), other,
                        "{\"pushToken\":\"ExponentPushToken[bbb]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isOk());
        PushDevice old = devices.findAll().stream().filter(d -> d.getInstallationId().equals(installation)).findFirst().orElseThrow();
        assertThat(old.isActive()).isFalse();
        assertThat(old.getDeactivationReason()).isEqualTo(PushDevice.Reasons.TOKEN_REASSIGNED);

        // 다른 회원의 해제 요청은 영향이 없고, 본인 해제는 비활성화한다. 둘 다 204.
        mvc.perform(as(delete(DEVICES + "/install-0002"), member)).andExpect(status().isNoContent());
        assertThat(device("install-0002").isActive()).isTrue();
        mvc.perform(as(delete(DEVICES + "/install-0002"), other)).andExpect(status().isNoContent());
        assertThat(device("install-0002").isActive()).isFalse();
        assertThat(device("install-0002").getDeactivationReason()).isEqualTo(PushDevice.Reasons.LOGGED_OUT);
        // 다시 로그인해 등록하면 활성으로 돌아온다.
        mvc.perform(json(put(DEVICES + "/install-0002"), other,
                        "{\"pushToken\":\"ExponentPushToken[bbb]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(device("install-0002").getDeactivationReason()).isNull();
    }

    @Test
    void deviceRegistrationRejectsInvalidInput() throws Exception {
        mvc.perform(json(put(DEVICES + "/install-0001"), member, "{\"pushToken\":\"fcm-raw-token\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(DEVICES + "/install-0001"), member, "{\"pushToken\":\"ExponentPushToken[aaa]\",\"platform\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(DEVICES + "/install-0001"), member, "{\"pushToken\":\"ExponentPushToken[aaa]\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(DEVICES + "/short"), member, "{\"pushToken\":\"ExponentPushToken[aaa]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(DEVICES + "/install-0001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pushToken\":\"ExponentPushToken[aaa]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(devices.count()).isZero();
        // 새 형식 토큰 이름(ExpoPushToken)도 허용한다.
        mvc.perform(json(put(DEVICES + "/install-0001"), member, "{\"pushToken\":\"ExpoPushToken[aaa]\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isOk());
    }

    private PushDevice device(String installationId) {
        return devices.findAll().stream().filter(d -> d.getInstallationId().equals(installationId)).findFirst().orElseThrow();
    }

    private Notification save(User user, String title, String payload) {
        return notifications.saveAndFlush(Notification.create(user, Notification.Type.COMPETITION_EXTRACTION_SUBMITTED,
                title, "본문", "/competitions/extractions/1", payload, null, Instant.now()));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User verified(String key, String universityEmail) {
        User user = User.createOAuthUser(key + "@example.com", key, User.OAuthProvider.KAKAO, key);
        user.verifyUniversityEmail(universityEmail);
        return users.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, User actor, String body) {
        return as(request, actor).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User actor) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
