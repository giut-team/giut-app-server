package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.dto.request.CreateCompetitionRequest;
import com.giut.server.competition.dto.request.UpsertCompetitionRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.competition.seed.SampleCompetitionCatalog;
import com.giut.server.competition.util.CompetitionUrlNormalizer;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/security/transactions on a disposable H2 database, not the shared MySQL DB. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_sample_batches;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class CompetitionSeedPersistenceTest {
    private static final String PATH = "/api/admin/competitions/samples";
    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionRepository competitions;
    @Autowired private CompetitionUrlRepository urls;
    @Autowired private CompetitionVerificationLogRepository logs;
    @Autowired private CompetitionSeedService seeds;
    @Autowired private CompetitionService service;
    @MockitoSpyBean private SampleCompetitionCatalog catalog;
    private MockMvc mvc;
    private User admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("sample-admin@example.com", "hash", "관리자"));
    }

    @AfterEach
    void cleanUp() {
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @Test
    void repeatedHttpCallsCommitNewBatchesWithOpenThirtyDayPeriodsAndPreserveExistingData() throws Exception {
        var draft = service.createByAdmin(admin.getId(), existing("수정한 기존 제목", "https://daker.ai/public/hackathons/kic-ai-agent-competition"));
        var archived = service.createByAdmin(admin.getId(), existing("보관한 기존 목업", "https://example.com/old-mock"));
        service.deleteByAdmin(admin.getId(), archived.id());

        Instant before = Instant.now().minusMillis(1); // DB timestamp precision may truncate sub-microseconds.
        createViaHttp();
        var first = competitions.findAll().stream().filter(c -> c.getId() > archived.id()).toList();
        assertThat(first).hasSize(10);
        Instant firstStart = first.getFirst().getApplicationStartAt();
        assertThat(firstStart).isBetween(before, Instant.now());
        createViaHttp();
        var all = competitions.findAll();
        var samples = all.stream().filter(c -> c.getId() > archived.id()).toList();
        assertThat(samples).hasSize(20);
        assertThat(first.stream().map(Competition::getId).toList()).doesNotHaveDuplicates();
        assertThat(samples).allSatisfy(c -> {
            assertThat(Duration.between(c.getApplicationStartAt(), c.getApplicationEndAt())).isEqualTo(Duration.ofDays(30));
            assertThat(c.getApplicationStartAt()).isBetween(before, Instant.now());
            assertThat(c.getPublicationStatus()).isEqualTo(Competition.PublicationStatus.PUBLISHED);
            assertThat(c.getVerificationStatus()).isEqualTo(Competition.VerificationStatus.MANUALLY_VERIFIED);
            assertThat(c.getVerifiedByUser().getId()).isEqualTo(admin.getId());
        });
        assertThat(first).allSatisfy(c -> assertThat(c.getApplicationStartAt()).isEqualTo(firstStart));
        assertThat(competitions.findById(first.getFirst().getId()).orElseThrow().getApplicationStartAt()).isEqualTo(firstStart);
        var storedDraft = competitions.findById(draft.id()).orElseThrow();
        assertThat(storedDraft.getTitle()).isEqualTo("수정한 기존 제목");
        assertThat(storedDraft.getPublicationStatus()).isEqualTo(Competition.PublicationStatus.DRAFT);
        assertThat(storedDraft.getApplicationStartAt()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(competitions.findById(archived.id()).orElseThrow().getPublicationStatus()).isEqualTo(Competition.PublicationStatus.ARCHIVED);
        assertThat(urls.count()).isEqualTo(22);
        assertThat(logs.count()).isEqualTo(22);
        var normalizer = new CompetitionUrlNormalizer();
        assertThat(urls.findAll()).allSatisfy(url -> {
            assertThat(url.getNormalizedUrlHash()).isEqualTo(normalizer.normalize(url.getUrl()).hash());
            assertThat(url.isPrimary()).isTrue();
        }).extracting(CompetitionUrl::getNormalizedUrlHash).doesNotHaveDuplicates();
        mvc.perform(get("/api/admin/competitions").header("Authorization", "Bearer " + jwt.generateAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21));
        mvc.perform(get("/api/competitions").param("status", "OPEN")
                        .header("Authorization", "Bearer " + jwt.generateAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(20));
    }

    @Test
    void midBatchConflictRollsBackCompetitionsUrlsAndAdminCreationLogs() {
        var batch = new ArrayList<>(catalog.competitions());
        var third = batch.get(2);
        batch.set(2, new UpsertCompetitionRequest(third.title(), third.category(), third.hostOrganization(),
                third.targetParticipants(), third.summary(), third.applicationStartAt(), third.applicationEndAt(),
                third.publicationStatus(), batch.getFirst().urls()));
        doReturn(List.copyOf(batch)).when(catalog).competitions();

        assertThatThrownBy(() -> seeds.seedSamples(admin.getId())).isInstanceOf(ConflictException.class);
        assertThat(competitions.count()).isZero();
        assertThat(urls.count()).isZero();
        assertThat(logs.count()).isZero();
    }

    @Test
    void concurrentCallsCommitTwentyDistinctCompetitionsRatherThanSkippingOrColliding() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return seeds.seedSamples(admin.getId()); });
            var second = executor.submit(() -> { start.await(); return seeds.seedSamples(admin.getId()); });
            start.countDown();
            var one = first.get(30, TimeUnit.SECONDS);
            var two = second.get(30, TimeUnit.SECONDS);
            assertThat(one.createdCount()).isEqualTo(10);
            assertThat(two.createdCount()).isEqualTo(10);
            assertThat(two.competitionIds()).hasSize(10).doesNotContainAnyElementsOf(one.competitionIds());
        }
        assertThat(competitions.count()).isEqualTo(20);
        assertThat(urls.count()).isEqualTo(20);
        assertThat(logs.count()).isEqualTo(20);
        assertThat(urls.findAll()).extracting(CompetitionUrl::getNormalizedUrlHash).doesNotHaveDuplicates();
    }

    private void createViaHttp() throws Exception {
        mvc.perform(post(PATH).header("Authorization", "Bearer " + jwt.generateAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(10))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.competitionIds.length()").value(10));
    }

    private CreateCompetitionRequest existing(String title, String url) {
        return new CreateCompetitionRequest(title, Competition.Category.WEB_MOBILE_IT, "기존 주최", "기존 대상", "기존 소개",
                Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-02-01T00:00:00Z"),
                Competition.PublicationStatus.DRAFT, Competition.VerificationStatus.UNVERIFIED,
                List.of(new CompetitionUrlRequest(CompetitionUrl.Type.SOURCE_ORIGINAL, url, true)));
    }
}
