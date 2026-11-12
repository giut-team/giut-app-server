package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.AiCompetitionRequest;
import com.giut.server.competition.dto.request.AiActivityType;
import com.giut.server.competition.dto.request.AiTargetParticipants;
import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionIngestionFailure;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.repository.CompetitionIngestionFailureRepository;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.global.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in only: this schema is disposable and must never point to a shared database. */
@EnabledIfEnvironmentVariable(named = "GIUT_AI_BATCH_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33398/giut_ai_batch_test?useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL="
})
class AiCompetitionBatchMySqlTest {
    @Autowired CompetitionService service;
    @Autowired CompetitionRepository competitions;
    @Autowired CompetitionUrlRepository urls;
    @Autowired CompetitionVerificationLogRepository logs;
    @Autowired CompetitionIngestionFailureRepository failures;
    @Autowired AiCompetitionBatchService batchService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        failures.deleteAll();
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
    }

    @Test
    void concurrentSameOfficialUrlCreatesOnlyOneCompetition() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); return attemptCreate(); });
            var two = executor.submit(() -> { start.await(); return attemptCreate(); });
            start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder("created", "conflict");
        }
        assertThat(competitions.count()).isEqualTo(1);
        assertThat(urls.count()).isEqualTo(1);
    }

    private String attemptCreate() {
        try {
            service.createByAi(request("https://example.com/concurrent-official"));
            return "created";
        } catch (ConflictException e) {
            return "conflict";
        }
    }

    @Test
    void laterDuplicateIsRecordedWhileEarlierAndOtherItemsStayCommitted() {
        JsonNode first = item("https://example.com/batch-one");
        assertThat(batchService.ingest(List.of(first, first, item("https://example.com/batch-two")))).hasSize(2);
        assertThat(competitions.count()).isEqualTo(2);
        assertThat(urls.count()).isEqualTo(2);
        assertThat(logs.count()).isEqualTo(2);
        assertThat(failures.findAll()).singleElement().satisfies(failure -> {
            assertThat(failure.getFailureType()).isEqualTo(CompetitionIngestionFailure.FailureType.DUPLICATE);
            assertThat(failure.getItemIndex()).isEqualTo(1);
        });
        assertThat(competitions.findAll()).allSatisfy(competition -> {
            assertThat(competition.getPrizeInfo()).isEqualTo("대상 500만 원");
            assertThat(competition.getTargetParticipants()).isEqualTo("UNIV_ONLY");
        });
    }

    private JsonNode item(String url) {
        return objectMapper.readTree("""
                {"title":"공고", "category":["AI_DATA"], "targetParticipants":"UNIV_ONLY", "activityKind":"HACKATHON",
                 "applicationStartAt":"2026-11-01T00:00:00Z", "applicationEndAt":"2026-12-01T00:00:00Z",
                 "prizeInfo":"대상 500만 원", "totalPrizeAmount":10000000,
                 "urls":[{"type":"OFFICIAL","url":"%s","primary":true}]}
                """.formatted(url));
    }

    private AiCompetitionRequest request(String url) {
        return new AiCompetitionRequest("공고", List.of(Competition.Category.AI_DATA), null, AiTargetParticipants.UNIV_ONLY, null,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"),
                List.of(new CompetitionUrlRequest(CompetitionUrl.Type.OFFICIAL, url, true)),
                AiActivityType.HACKATHON, "대상 500만 원", 10000000L, null, null);
    }
}
