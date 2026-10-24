package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.chat.entity.ChatRoom;
import com.giut.server.chat.repository.ChatRoomRepository;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.entity.CompetitionScrap;
import com.giut.server.competition.entity.CompetitionUrl;
import com.giut.server.competition.entity.CompetitionVerificationLog;
import com.giut.server.competition.repository.*;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP service transactions commit independently; no outer test transaction or shared MySQL. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_competition_delete;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class AdminCompetitionDeletePersistenceTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionRepository competitions;
    @Autowired private CompetitionUrlRepository urls;
    @Autowired private CompetitionScrapRepository scraps;
    @Autowired private CompetitionVerificationLogRepository logs;
    @Autowired private TeamRepository teams;
    @Autowired private TeamMemberRepository members;
    @Autowired private ChatRoomRepository rooms;
    private MockMvc mvc;
    private User admin;
    private User student;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("delete-admin@example.com", "hash", "관리자"));
        student = User.createOAuthUser("delete-student@example.com", "학생", User.OAuthProvider.KAKAO, "delete-student");
        student.verifyUniversityEmail("delete-student@uos.ac.kr");
        student = users.saveAndFlush(student);
    }

    @AfterEach
    void cleanUp() {
        // Only this class's explicit disposable H2 database; dependency order preserves FK safety.
        rooms.deleteAll();
        members.deleteAll();
        teams.deleteAll();
        scraps.deleteAll();
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
        users.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(Competition.PublicationStatus.class)
    void adminWithoutUniversityVerificationArchivesAnyStateIdempotently(Competition.PublicationStatus state) throws Exception {
        Competition competition = competition(state);
        assertThat(admin.getUniversityVerifiedAt()).isNull();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(as(admin, delete(path(competition))))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
            Competition stored = competitions.findById(competition.getId()).orElseThrow();
            assertThat(stored.getPublicationStatus()).isEqualTo(Competition.PublicationStatus.ARCHIVED);
            assertThat(stored.getVerificationStatus()).isEqualTo(Competition.VerificationStatus.UNVERIFIED);
            assertThat(stored.getVerifiedAt()).isNull();
            assertThat(stored.getTitle()).isEqualTo("삭제 테스트 공모전");
        }
    }

    @Test
    void removesPublicVisibilityButPreservesAllRelatedFixturesAndOtherCompetitions() throws Exception {
        Competition target = competition(Competition.PublicationStatus.PUBLISHED);
        Competition other = competition(Competition.PublicationStatus.PUBLISHED);
        CompetitionUrl url = urls.saveAndFlush(CompetitionUrl.create(target, CompetitionUrl.Type.RECRUITMENT,
                "https://example.com/delete-test", "https://example.com/delete-test", "a".repeat(64), true));
        CompetitionVerificationLog log = logs.saveAndFlush(CompetitionVerificationLog.createAdminReviewLog(
                target, null, Competition.VerificationStatus.UNVERIFIED, admin, "기존 기록"));
        CompetitionScrap scrap = scraps.saveAndFlush(CompetitionScrap.create(student, target));
        Team team = teams.saveAndFlush(Team.create(target, student, "기존 팀", "보존 대상",
                Team.ActivityMode.ONLINE, (short) 4, (short) 1, Team.MeetingPlace.ANYWHERE));
        TeamMember member = members.saveAndFlush(TeamMember.createLeader(team.getId(), student.getId()));
        ChatRoom room = rooms.saveAndFlush(ChatRoom.createTeamRoom(team.getId()));

        mvc.perform(as(admin, get("/api/competitions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());
        mvc.perform(as(admin, get("/api/competitions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.competitions[0].id").value(other.getId()));
        for (String endpoint : List.of("/api/competitions/top5", "/api/competitions/closing-soon")) {
            mvc.perform(as(admin, get(endpoint))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(other.getId()));
        }
        mvc.perform(as(admin, get("/api/competitions/" + target.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(as(student, post("/api/competitions/" + target.getId() + "/scrap")))
                .andExpect(status().isNotFound());

        assertThat(competitions.findById(target.getId())).isPresent();
        assertThat(urls.findById(url.getId())).isPresent();
        assertThat(logs.findById(log.getId())).isPresent();
        assertThat(scraps.findById(scrap.getId())).isPresent();
        assertThat(teams.findById(team.getId()).orElseThrow().getStatus()).isEqualTo(Team.Status.RECRUITING);
        assertThat(members.findById(member.getId()).orElseThrow().getStatus()).isEqualTo(TeamMember.Status.ACTIVE);
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(ChatRoom.Status.ACTIVE);
        assertThat(competitions.findById(other.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.PUBLISHED);
    }

    @Test
    void rejectsAnonymousAndOrdinaryUsersWithoutChangingTarget() throws Exception {
        Competition target = competition(Competition.PublicationStatus.PUBLISHED);
        mvc.perform(delete(path(target))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(as(student, delete(path(target)))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        User unverified = users.saveAndFlush(User.createOAuthUser(
                "delete-unverified@example.com", "미인증", User.OAuthProvider.KAKAO, "delete-unverified"));
        mvc.perform(as(unverified, delete(path(target)))).andExpect(status().isForbidden());
        assertThat(competitions.findById(target.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.PUBLISHED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "SUSPENDED", "DELETED"})
    void rejectsStaleAdminJwtWhenCurrentDatabasePrivilegesAreGone(String changed) throws Exception {
        Competition target = competition(Competition.PublicationStatus.PUBLISHED);
        String oldToken = jwt.generateAccessToken(admin);
        if ("STUDENT".equals(changed)) ReflectionTestUtils.setField(admin, "role", User.Role.STUDENT);
        else ReflectionTestUtils.setField(admin, "status", User.Status.valueOf(changed));
        users.saveAndFlush(admin);

        mvc.perform(withToken(oldToken, delete(path(target)))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        assertThat(competitions.findById(target.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.PUBLISHED);
    }

    @Test
    void invalidOrMissingIdDoesNotChangeOtherRows() throws Exception {
        Competition target = competition(Competition.PublicationStatus.PUBLISHED);
        for (String id : List.of("0", "-1", "not-a-number", "99999999999999999999999")) {
            mvc.perform(as(admin, delete("/api/admin/competitions/" + id)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(as(admin, delete("/api/admin/competitions/" + Long.MAX_VALUE)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        assertThat(competitions.findById(target.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.PUBLISHED);
    }

    @Test
    void archivedCompetitionCannotBeResurrectedByExistingUpdateOrPublish() throws Exception {
        Competition target = competition(Competition.PublicationStatus.DRAFT);
        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());
        mvc.perform(as(admin, put(path(target))).contentType(MediaType.APPLICATION_JSON).content(updateBody()))
                .andExpect(status().isNotFound());
        mvc.perform(as(admin, patch(path(target) + "/publish"))).andExpect(status().isNotFound());
        assertThat(competitions.findById(target.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.ARCHIVED);
        assertThat(urls.count()).isZero();
    }

    @Test
    void creatingNewTeamAfterDeletionIsRejectedWithoutPartialWrites() throws Exception {
        Competition target = competition(Competition.PublicationStatus.PUBLISHED);
        mvc.perform(as(admin, delete(path(target)))).andExpect(status().isNoContent());
        mvc.perform(as(student, post("/api/teams")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitionId\":" + target.getId() + ",\"name\":\"새 팀\","
                                + "\"activityMode\":\"ONLINE\",\"maxMemberCount\":4,\"weeklyMeetingCount\":1,"
                                + "\"meetingPlace\":\"ANYWHERE\",\"recruitments\":[],\"applicationQuestions\":[]}"))
                .andExpect(status().isNotFound());
        assertThat(teams.count()).isZero();
        assertThat(members.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "publish"})
    void concurrentDeleteAndAdminMutationCannotLeaveCompetitionPublished(String mutation) throws Exception {
        Competition target = competition(Competition.PublicationStatus.DRAFT);
        List<Integer> statuses = race(
                () -> mvc.perform(as(admin, delete(path(target)))).andReturn().getResponse().getStatus(),
                () -> mvc.perform(as(admin, "update".equals(mutation)
                        ? put(path(target)).contentType(MediaType.APPLICATION_JSON).content(updateBody())
                        : patch(path(target) + "/publish"))).andReturn().getResponse().getStatus());
        assertThat(statuses.getFirst()).isEqualTo(204);
        assertThat(statuses.getLast()).isIn(200, 404);
        assertThat(competitions.findById(target.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.ARCHIVED);
    }

    @Test
    void concurrentDeletesBothSucceedWithoutRemovingRow() throws Exception {
        Competition target = competition(Competition.PublicationStatus.PUBLISHED);
        assertThat(race(
                () -> mvc.perform(as(admin, delete(path(target)))).andReturn().getResponse().getStatus(),
                () -> mvc.perform(as(admin, delete(path(target)))).andReturn().getResponse().getStatus()))
                .containsExactly(204, 204);
        assertThat(competitions.findById(target.getId()).orElseThrow().getPublicationStatus())
                .isEqualTo(Competition.PublicationStatus.ARCHIVED);
    }

    private List<Integer> race(java.util.concurrent.Callable<Integer> first,
                               java.util.concurrent.Callable<Integer> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.function.Function<java.util.concurrent.Callable<Integer>, java.util.concurrent.Callable<Integer>> gated = action -> () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test start timed out");
                return action.call();
            };
            var a = executor.submit(gated.apply(first));
            var b = executor.submit(gated.apply(second));
            try { assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private Competition competition(Competition.PublicationStatus state) {
        return competitions.saveAndFlush(Competition.create("삭제 테스트 공모전", Competition.Category.WEB_MOBILE_IT,
                "테스트 주최", "대학생", "보존할 소개", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600), state, Competition.VerificationStatus.UNVERIFIED, null));
    }

    private String updateBody() {
        return "{\"title\":\"수정 제목\",\"category\":\"WEB_MOBILE_IT\",\"summary\":\"수정 소개\","
                + "\"publicationStatus\":\"PUBLISHED\",\"urls\":[{\"type\":\"RECRUITMENT\","
                + "\"url\":\"https://example.com/updated\",\"primary\":true}]}";
    }

    private String path(Competition target) { return "/api/admin/competitions/" + target.getId(); }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return withToken(jwt.generateAccessToken(actor), request);
    }

    private MockHttpServletRequestBuilder withToken(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
