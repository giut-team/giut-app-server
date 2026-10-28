package com.giut.server.competition.service;

import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionScrapRepository;
import com.giut.server.team.entity.Team;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.team.repository.TeamScrapRepository;
import com.giut.server.team.service.TeamScrapService;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in only: hard-coded disposable, loopback MySQL test database. */
@EnabledIfSystemProperty(named = "giut.scrap.mysql.test", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:33389/giut_scrap_test?useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=giut-local-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class ScrapMySqlConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired CompetitionRepository competitions;
    @Autowired CompetitionScrapRepository competitionScraps;
    @Autowired TeamRepository teams;
    @Autowired TeamScrapRepository teamScraps;
    @Autowired CompetitionService competitionService;
    @Autowired TeamScrapService teamService;
    @Autowired TransactionTemplate transactions;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingDuplicateRegistrationObservesCommittedScrap(boolean team) throws Exception {
        var fixture = fixture();
        runOrdered(fixture, team, false, false);
        assertThat(count(fixture, team)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingRemovalObservesCommittedRegistration(boolean team) throws Exception {
        var fixture = fixture();
        runOrdered(fixture, team, false, true);
        assertThat(count(fixture, team)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitingRegistrationObservesCommittedRemoval(boolean team) throws Exception {
        var fixture = fixture();
        add(fixture, team);
        runOrdered(fixture, team, true, false);
        assertThat(count(fixture, team)).isEqualTo(1);
    }

    private void runOrdered(Fixture fixture, boolean team, boolean firstRemove, boolean secondRemove) throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                if (firstRemove) remove(fixture, team); else add(fixture, team);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    started.countDown();
                    if (secondRemove) remove(fixture, team); else add(fixture, team);
                });
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private Fixture fixture() {
        String unique = java.util.UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.createOAuthUser(unique + "@example.com", "회원", User.OAuthProvider.KAKAO, unique));
        Instant now = Instant.now();
        Competition competition = competitions.saveAndFlush(Competition.create("테스트", Competition.Category.WEB_MOBILE_IT,
                "주최", "대학생", "소개", now.minusSeconds(3600), now.plusSeconds(7200),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.UNVERIFIED, null));
        Team team = teams.saveAndFlush(Team.create(competition, user, "팀", "소개", Team.ActivityMode.ONLINE,
                (short) 5, (short) 1, Team.MeetingPlace.ANYWHERE));
        return new Fixture(user.getId(), competition.getId(), team.getId());
    }

    private void add(Fixture f, boolean team) {
        if (team) assertThat(teamService.scrap(f.userId, f.teamId).scrapped()).isTrue();
        else assertThat(competitionService.scrapCompetition(f.userId, f.competitionId).scrapped()).isTrue();
    }

    private void remove(Fixture f, boolean team) {
        if (team) assertThat(teamService.removeScrap(f.userId, f.teamId).scrapped()).isFalse();
        else assertThat(competitionService.removeCompetitionScrap(f.userId, f.competitionId).scrapped()).isFalse();
    }

    private long count(Fixture f, boolean team) {
        return team ? teamScraps.countByUser_Id(f.userId) : competitionScraps.countByUser_Id(f.userId);
    }

    private record Fixture(Long userId, Long competitionId, Long teamId) {}
}
