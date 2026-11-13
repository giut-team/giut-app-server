package com.giut.server.competition.service;

import com.giut.server.competition.entity.CompetitionExtractionJob.Status;
import com.giut.server.competition.repository.CompetitionExtractionJobRepository;
import com.giut.server.competition.util.PublicCompetitionSource;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** 실제 Spring @Scheduled 등록으로(수동 pollOnce 없이) 접수된 작업이 실행·저장되는지 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_extraction_scheduling;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "app.ai-extraction.jobs.worker-enabled=true",
        "app.ai-extraction.jobs.poll-interval=200ms"
})
class CompetitionExtractionWorkerSchedulingTest {
    @Autowired CompetitionExtractionJobService service;
    @Autowired CompetitionExtractionJobRepository jobs;
    @Autowired UserRepository users;
    @MockitoBean PublicCompetitionSource publicSource;
    @MockitoBean AiCompetitionExtractClient extractor;

    @AfterEach
    void cleanUp() {
        jobs.deleteAll();
        users.deleteAll();
    }

    @Test
    void scheduledPollerRunsAcceptedJobWithConfiguredCallTimeout() throws Exception {
        User user = User.createOAuthUser("schedule@example.com", "학생", User.OAuthProvider.KAKAO, "schedule");
        user.verifyUniversityEmail("schedule@uos.ac.kr");
        Long userId = users.saveAndFlush(user).getId();
        when(extractor.isConfigured()).thenReturn(true);
        when(extractor.extract(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            assertThat(invocation.getArgument(1, Duration.class)).isEqualTo(Duration.ofSeconds(90));
            assertThat(Thread.currentThread().getName()).startsWith("competition-extraction-");
            return new AiCompetitionExtractClient.Extraction("공모전", null, null, null, null,
                    LocalDate.of(2099, 1, 2), null, null, "CONTEST", List.of("IDEA"), null, "소개", null, null);
        });

        Long id = service.accept(userId, "https://example.com/scheduled").response().jobId();

        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline && jobs.findById(id).orElseThrow().getStatus() != Status.SUCCEEDED) {
            Thread.sleep(50);
        }
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(Status.SUCCEEDED);
    }
}
