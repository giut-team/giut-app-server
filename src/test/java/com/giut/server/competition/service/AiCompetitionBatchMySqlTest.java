package com.giut.server.competition.service;

import com.giut.server.competition.dto.request.AiCompetitionRequest;
import com.giut.server.competition.dto.request.AiActivityType;
import com.giut.server.competition.dto.request.AiTargetParticipants;
import com.giut.server.competition.dto.request.CompetitionUrlRequest;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import com.giut.server.global.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void laterDuplicateRollsBackEarlierCompetitionUrlAndAuditLog() {
        AiCompetitionRequest first = request("https://example.com/batch-one");
        assertThatThrownBy(() -> service.createBatchByAi(List.of(first, first))).isInstanceOf(ConflictException.class);
        assertThat(competitions.count()).isZero();
        assertThat(urls.count()).isZero();
        assertThat(logs.count()).isZero();
        assertThat(service.createBatchByAi(List.of(first, request("https://example.com/batch-two")))).hasSize(2);
        assertThat(competitions.count()).isEqualTo(2);
        assertThat(urls.count()).isEqualTo(2);
        assertThat(logs.count()).isEqualTo(2);
        assertThat(competitions.findAll()).allSatisfy(competition -> {
            assertThat(competition.getPrizeInfo()).isEqualTo("대상 500만 원");
            assertThat(competition.getTargetParticipants()).isEqualTo("UNIV_ONLY");
        });
    }

    private AiCompetitionRequest request(String url) {
        return new AiCompetitionRequest("공고", Competition.Category.AI_DATA, null, AiTargetParticipants.UNIV_ONLY, null,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"),
                List.of(new CompetitionUrlRequest(CompetitionUrl.Type.RECRUITMENT, url, true)),
                AiActivityType.HACKATHON, "대상 500만 원");
    }
}
