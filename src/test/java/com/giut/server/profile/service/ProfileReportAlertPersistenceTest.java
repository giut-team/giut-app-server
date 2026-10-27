package com.giut.server.profile.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.profile.dto.request.CreateProfileReportRequest;
import com.giut.server.profile.entity.ProfileReport;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.event.ProfileReportCreatedEvent;
import com.giut.server.profile.repository.ProfileReportRepository;
import com.giut.server.profile.repository.UserProfileRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Explicit test DB and fake transport only; no outer test transaction hides commit behavior.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_report_alerts;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_ENABLED=false",
        "DISCORD_PROFILE_REPORT_WEBHOOK_URL=https://discord.com/api/webhooks/123456/FAKE_TOKEN",
        "DISCORD_PROFILE_REPORT_ENABLED=true", "APP_ENVIRONMENT=dev",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Import(ProfileReportAlertPersistenceTest.TransportConfig.class)
class ProfileReportAlertPersistenceTest {
    @Autowired private ProfileReportService service;
    @Autowired private ProfileReportAlertService alerts;
    @Autowired private ProfileReportRepository reports;
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private JwtProvider jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private FakeDiscord transport;
    private User reporter;
    private User target;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        transport.payloads.clear();
        transport.response = () -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reporter = user("reporter");
        target = user("target");
        profiles.saveAndFlush(UserProfile.create(target, UserProfile.DepartmentType.COMPUTER_SCIENCE,
                UserProfile.ActivityStatus.LOOKING_FOR_TEAM, (short) 3, null, null,
                "PRIVATE_BIO", true, "[\"DEVELOPMENT\"]"));
    }

    @AfterEach
    void cleanUp() throws Exception {
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        reports.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
    }

    @Test
    void realTransactionNotifiesOnlyAfterCommitAndExcludesUserContent() throws Exception {
        var response = new TransactionTemplate(transactionManager).execute(status -> {
            var report = service.reportPublicProfile(reporter.getId(), target.getId(), request());
            assertThat(transport.payloads).isEmpty();
            return report;
        });
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(reports.existsById(response.id())).isTrue();
        assertThat(transport.payloads).hasSize(1);
        assertThat(transport.payloads.getFirst()).contains("신고 번호: " + response.id(), "PENDING")
                .doesNotContain("PRIVATE_DESCRIPTION", "PRIVATE_BIO", "reporter@example.com",
                        "target@example.com", "FAKE_TOKEN");
    }

    @Test
    void rollbackNeverSendsEvenAfterTheIdentityInsertWasFlushed() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.reportPublicProfile(reporter.getId(), target.getId(), request());
            reports.flush();
            assertThat(reports.count()).isEqualTo(1);
            assertThat(transport.payloads).isEmpty();
            status.setRollbackOnly();
        });
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(reports.count()).isZero();
        assertThat(transport.payloads).isEmpty();
    }

    @Test
    void eventOutsideATransactionDoesNotTriggerDelivery() throws Exception {
        publisher.publishEvent(ProfileReportAlertServiceTest.event(ProfileReport.Reason.OTHER));
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(transport.payloads).isEmpty();
    }

    @Test
    void discordRejectionDoesNotChangeCreatedResponseOrStoredPendingReport() throws Exception {
        transport.response = () -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .body("PRIVATE_REMOTE_ERROR").build());
        postReport(reporter, target, HttpStatus.CREATED);
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(reports.count()).isEqualTo(1);
        assertThat(reports.findAll().getFirst().getStatus()).isEqualTo(ProfileReport.Status.PENDING);
        assertThat(transport.payloads).hasSize(1);
    }

    @Test
    void slowDiscordDoesNotBlockTheHttpResponse() throws Exception {
        var gate = Sinks.<Boolean>one();
        transport.response = () -> gate.asMono()
                .map(ignored -> ClientResponse.create(HttpStatus.NO_CONTENT).build());
        try (var caller = Executors.newSingleThreadExecutor()) {
            try {
                caller.submit(() -> {
                    postReport(reporter, target, HttpStatus.CREATED);
                    return null;
                }).get(2, TimeUnit.SECONDS);
                assertThat(reports.count()).isEqualTo(1);
            } finally {
                gate.tryEmitValue(true);
            }
        }
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(transport.payloads).hasSize(1);
    }

    @Test
    void duplicatesInvalidRequestsAndUnauthorizedRequestsDoNotNotify() throws Exception {
        postReport(reporter, target, HttpStatus.CREATED);
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        postReport(reporter, target, HttpStatus.CONFLICT);
        postReport(reporter, reporter, HttpStatus.NOT_FOUND); // This user has no profile.
        postReport(target, target, HttpStatus.BAD_REQUEST);
        mvc.perform(post(path(target)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(target)).header("Authorization", token(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(transport.payloads).hasSize(1);
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void completedReportCanBeResubmittedAndBothAcceptedReportsNotify() throws Exception {
        postReport(reporter, target, HttpStatus.CREATED);
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        User admin = users.saveAndFlush(User.createAdmin("admin@example.com", "hash", "관리자"));
        service.reviewReport(admin.getId(), reports.findAll().getFirst().getId(),
                new com.giut.server.profile.dto.request.ReviewProfileReportRequest(
                        ProfileReport.Status.DISMISSED, "검토 완료"));
        postReport(reporter, target, HttpStatus.CREATED);
        ProfileReportAlertServiceTest.awaitFinished(alerts);
        assertThat(transport.payloads).hasSize(2);
        assertThat(reports.count()).isEqualTo(2);
    }

    private CreateProfileReportRequest request() {
        return new CreateProfileReportRequest(ProfileReport.Reason.OTHER, "PRIVATE_DESCRIPTION");
    }

    private void postReport(User actor, User reported, HttpStatus expected) throws Exception {
        mvc.perform(post(path(reported)).header("Authorization", token(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"PRIVATE_DESCRIPTION\"}"))
                .andExpect(status().is(expected.value()));
    }

    private String token(User actor) {
        return "Bearer " + jwt.generateAccessToken(actor);
    }

    private String path(User reported) {
        return "/api/profile/" + reported.getId() + "/reports";
    }

    private User user(String name) {
        var user = User.createOAuthUser(name + "@example.com", name, User.OAuthProvider.KAKAO, name);
        user.verifyUniversityEmail(name + "@uos.ac.kr");
        return users.saveAndFlush(user);
    }

    static class FakeDiscord {
        final List<String> payloads = new CopyOnWriteArrayList<>();
        volatile Supplier<Mono<ClientResponse>> response;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransportConfig {
        @Bean FakeDiscord fakeDiscord() { return new FakeDiscord(); }

        @Bean @Primary
        WebClient reportTestWebClient(FakeDiscord transport) {
            return WebClient.builder().exchangeFunction(request -> {
                var capture = new MockClientHttpRequest(request.method(), request.url());
                return request.writeTo(capture, ExchangeStrategies.withDefaults())
                        .then(Mono.defer(capture::getBodyAsString)).flatMap(body -> {
                            transport.payloads.add(body);
                            return transport.response.get();
                        });
            }).build();
        }
    }
}
