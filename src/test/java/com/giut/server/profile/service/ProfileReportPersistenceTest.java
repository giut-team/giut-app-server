package com.giut.server.profile.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.profile.entity.ProfileReport;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.repository.ProfileReportRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import com.giut.server.user.repository.UserBlockRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No outer test transaction: HTTP service commits and rollbacks are visible to later requests.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_reports;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "DISCORD_ALERT_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class ProfileReportPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private UserBlockRepository blocks;
    @Autowired private UserProfileRepository profiles;
    @Autowired private ProfileReportRepository reports;
    @Autowired private JwtProvider jwt;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private User reporter;
    private User target;
    private User other;
    private User admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reporter = user("reporter", true);
        target = user("target", true);
        other = user("other", true);
        admin = users.saveAndFlush(User.createAdmin("report-admin@example.com", "hash", "관리자"));
        profiles.saveAndFlush(UserProfile.create(target, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null,
                "원래 소개", true, "[\"DEVELOPMENT\"]"));
    }

    @AfterEach
    void cleanUp() {
        // Only this class's isolated in-memory database is used.
        reports.deleteAll();
        blocks.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
    }

    @Test
    void retrievesOwnPendingReasonAndDescriptionAfterCommitWithoutAdminOrSnapshotData() throws Exception {
        long id = submit(reporter, "OTHER", "  추가 설명  ");
        mvc.perform(as(reporter, get(pendingPath(target))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.report.id").value(id))
                .andExpect(jsonPath("$.report.userId").value(target.getId()))
                .andExpect(jsonPath("$.report.status").value("PENDING"))
                .andExpect(jsonPath("$.report.reason").value("OTHER"))
                .andExpect(jsonPath("$.report.description").value("추가 설명"))
                .andExpect(jsonPath("$.report.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.report.reviewNote").doesNotExist())
                .andExpect(jsonPath("$.report.reviewedBy").doesNotExist())
                .andExpect(jsonPath("$.report.reporter").doesNotExist())
                .andExpect(jsonPath("$.report.snapshotBio").doesNotExist());
    }

    @Test
    void rejectsDuplicateRegardlessOfReasonAndKeepsOriginalContent() throws Exception {
        submit(reporter, "OTHER", "원래 설명");
        mvc.perform(as(reporter, post(reportPath(target))).contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPAM_ADVERTISING", "다른 설명")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        assertThat(reports.count()).isEqualTo(1);
        mvc.perform(as(reporter, get(pendingPath(target)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.report.description").value("원래 설명"))
                .andExpect(jsonPath("$.report.reason").value("OTHER"));
    }

    @Test
    void returnsFalseAndNullWhenNoPendingReportExistsIncludingUnknownTarget() throws Exception {
        for (long id : List.of(target.getId(), Long.MAX_VALUE)) {
            mvc.perform(as(reporter, get("/api/profile/" + id + "/reports/pending")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(false))
                    .andExpect(jsonPath("$.report", nullValue()));
        }
        assertThat(reports.count()).isZero();
    }

    @Test
    void neverReturnsAnotherReportersPendingReportEvenWithForgedReporterParameter() throws Exception {
        long first = submit(reporter, "OTHER", "본인 신고");
        mvc.perform(as(other, get(pendingPath(target))).param("reporterUserId", reporter.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.report", nullValue()));
        long second = submit(other, "SPAM_ADVERTISING", "다른 사용자 신고");
        assertThat(second).isNotEqualTo(first);
        mvc.perform(as(other, get(pendingPath(target))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.id").value(second));
        mvc.perform(as(reporter, get(pendingPath(target))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.id").value(first));
        mvc.perform(as(admin, get(pendingPath(target))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(false));
    }

    @Test
    void actionedReportAllowsNewReportAndKeepsCompletedHistory() throws Exception {
        completesThenResubmits("ACTIONED");
    }

    @Test
    void dismissedReportAllowsNewReportAndKeepsCompletedHistory() throws Exception {
        completesThenResubmits("DISMISSED");
    }

    @Test
    void restoresNullDescriptionEvenWhenTargetIsBlockedOrNoLongerPublicOrActive() throws Exception {
        long id = submit(reporter, "OTHER", null);
        mvc.perform(as(reporter, post("/api/users/" + target.getId() + "/block")))
                .andExpect(status().isOk());
        mvc.perform(as(reporter, get(pendingPath(target)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.report.id").value(id));
        // Remove the block immediately to keep fixture cleanup independent of block storage.
        mvc.perform(as(reporter, delete("/api/users/" + target.getId() + "/block")))
                .andExpect(status().isOk());
        UserProfile profile = profiles.findById(target.getId()).orElseThrow();
        ReflectionTestUtils.setField(profile, "searchable", false);
        ReflectionTestUtils.setField(profile, "activityStatus", UserProfile.ActivityStatus.RESTING);
        profiles.saveAndFlush(profile);
        ReflectionTestUtils.setField(target, "status", User.Status.DELETED);
        users.saveAndFlush(target);
        mvc.perform(as(reporter, get(pendingPath(target)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.report.id").value(id))
                .andExpect(jsonPath("$.report.description", nullValue()));
    }

    @Test
    void enforcesLoginSchoolVerificationAndActiveReporterWhileAllowingAdminSchoolException() throws Exception {
        submit(reporter, "OTHER", "비공개 신고");
        mvc.perform(get(pendingPath(target))).andExpect(status().isUnauthorized());
        User unverified = user("unverified", false);
        mvc.perform(as(unverified, get(pendingPath(target)))).andExpect(status().isForbidden());
        mvc.perform(as(unverified, post(reportPath(target))).contentType(MediaType.APPLICATION_JSON)
                .content(body("OTHER", null))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get(pendingPath(target))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(false));
        ReflectionTestUtils.setField(reporter, "status", User.Status.DELETED);
        users.saveAndFlush(reporter);
        mvc.perform(as(reporter, get(pendingPath(target)))).andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidIdsAndReportPayloadWithoutSaving() throws Exception {
        for (String id : List.of("0", "-1", "invalid")) {
            mvc.perform(as(reporter, get("/api/profile/" + id + "/reports/pending")))
                    .andExpect(status().isBadRequest());
            mvc.perform(as(reporter, post("/api/profile/" + id + "/reports"))
                    .contentType(MediaType.APPLICATION_JSON).content(body("OTHER", null)))
                    .andExpect(status().isBadRequest());
        }
        for (String payload : List.of("{}", "{\"reason\":\"INVALID\"}", body("OTHER", "x".repeat(1001)))) {
            mvc.perform(as(reporter, post(reportPath(target))).contentType(MediaType.APPLICATION_JSON)
                    .content(payload)).andExpect(status().isBadRequest());
        }
        assertThat(reports.count()).isZero();
    }

    @Test
    void concurrentSubmissionsPersistOnlyOnePendingReport() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test start timed out");
                return mvc.perform(as(reporter, post(reportPath(target)))
                        .contentType(MediaType.APPLICATION_JSON).content(body("OTHER", null)))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(reports.count()).isEqualTo(1);
    }

    private void completesThenResubmits(String decision) throws Exception {
        long first = submit(reporter, "OTHER", "첫 신고");
        mvc.perform(as(admin, patch("/api/admin/profile-reports/" + first + "/review"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + decision + "\",\"reviewNote\":\"관리자 전용 메모\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(reporter, get(pendingPath(target)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false)).andExpect(jsonPath("$.report", nullValue()));
        long second = submit(reporter, "INAPPROPRIATE_BEHAVIOR", "새 신고");
        assertThat(second).isNotEqualTo(first);
        assertThat(reports.count()).isEqualTo(2);
        assertThat(reports.findById(first).orElseThrow().getStatus().name()).isEqualTo(decision);
        mvc.perform(as(reporter, get(pendingPath(target)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.report.id").value(second))
                .andExpect(jsonPath("$.report.reason").value("INAPPROPRIATE_BEHAVIOR"));
    }

    private long submit(User actor, String reason, String description) throws Exception {
        String response = mvc.perform(as(actor, post(reportPath(target))).contentType(MediaType.APPLICATION_JSON)
                        .content(body(reason, description))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String body(String reason, String description) {
        return "{\"reason\":\"" + reason + "\",\"description\":"
                + (description == null ? "null" : "\"" + description + "\"") + "}";
    }

    private User user(String name, boolean verified) {
        User user = User.createOAuthUser(name + "@example.com", name, User.OAuthProvider.KAKAO, name);
        if (verified) user.verifyUniversityEmail(name + "@uos.ac.kr");
        return users.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor))
                .with(servletRequest -> {
                    servletRequest.setServletPath(servletRequest.getRequestURI());
                    return servletRequest;
                });
    }

    private String reportPath(User user) { return "/api/profile/" + user.getId() + "/reports"; }
    private String pendingPath(User user) { return reportPath(user) + "/pending"; }
}
