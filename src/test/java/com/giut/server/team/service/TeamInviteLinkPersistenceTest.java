package com.giut.server.team.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.global.exception.ConflictException;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.team.dto.response.TeamInviteLinkResponse;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamInviteLink;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.entity.TeamRecruitment;
import com.giut.server.team.repository.TeamInviteLinkRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRecruitmentRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:team_invite_links;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=",
        "DISCORD_SLOW_API_WEBHOOK_URL=",
        "logging.level.org.hibernate.SQL=OFF",
        "app.team-invite.frontend-base-url=https://example.com",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.push.worker-enabled=false"
})
class TeamInviteLinkPersistenceTest {

    private static final String ROLE_CODE = "BACKEND_DEVELOPER";
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired TeamService teamService;
    @Autowired UserRepository userRepository;
    @Autowired CompetitionRepository competitionRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired TeamMemberRepository teamMemberRepository;
    @Autowired TeamRecruitmentRepository teamRecruitmentRepository;
    @Autowired TeamInviteLinkRepository inviteLinkRepository;
    @Autowired WebApplicationContext context;
    @Autowired JwtProvider jwt;
    @MockitoBean Clock clock;

    private User leader;
    private Team team;
    private MockMvc mvc;

    @DynamicPropertySource
    static void useDisposableMySql(DynamicPropertyRegistry properties) {
        if ("true".equals(System.getenv("GIUT_INVITE_MYSQL_TEST"))) {
            // create-drop runs only against this dedicated loopback test schema.
            properties.add("spring.datasource.url", () ->
                    "jdbc:mysql://127.0.0.1:33468/giut_invite_test?sslMode=REQUIRED");
            properties.add("spring.datasource.username", () -> "giut_invite_test");
            properties.add("spring.datasource.password", () -> "giut-invite-local-test");
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        }
    }

    @BeforeEach
    void createRecruitingTeam() {
        when(clock.instant()).thenReturn(NOW);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader = createUser();
        Competition competition = competitionRepository.save(Competition.create(
                "팀 초대 공모전", Competition.Category.WEB_MOBILE_IT, "테스트 주최사", "대학생", "테스트",
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, leader
        ));
        team = teamRepository.save(Team.create(
                competition, leader, "초대 테스트 팀", "테스트", Team.ActivityMode.HYBRID,
                (short) 2, (short) 1, Team.MeetingPlace.CAMPUS
        ));
        teamMemberRepository.save(TeamMember.createLeader(team.getId(), leader.getId()));
        teamRecruitmentRepository.save(TeamRecruitment.create(team.getId(), ROLE_CODE, (short) 1));
    }

    @Test
    void issueRequiresLoginSchoolVerificationAndTeamLeader() throws Exception {
        String body = "{\"roleCode\":\"" + ROLE_CODE + "\"}";
        mvc.perform(jsonRequest("", body)).andExpect(status().isUnauthorized());
        mvc.perform(as(createStudent(false), jsonRequest("", body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("학교 이메일 인증 후 이용할 수 있습니다."));
        mvc.perform(as(createStudent(true), jsonRequest("", body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("팀장만 팀을 관리할 수 있습니다."));
        mvc.perform(as(leader, jsonRequest("", body))).andExpect(status().isCreated());
    }

    @Test
    void previewAndAcceptRejectMalformedToken() throws Exception {
        User student = createStudent(true);
        mvc.perform(as(student, jsonRequest("/preview", "{\"token\":\"invalid\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(student, jsonRequest("/accept", "{\"token\":\"invalid\"}")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(TeamMember.Status.class)
    void previousMembershipRejectsAcceptanceWithoutUsingLink(TeamMember.Status membershipStatus) throws Exception {
        TeamInviteLinkResponse link = issueLink();
        User invitee = createStudent(true);
        TeamMember member = TeamMember.createMember(team.getId(), invitee.getId(), ROLE_CODE);
        switch (membershipStatus) {
            case ACTIVE -> { }
            case LEFT -> member.leave();
            case KICKED -> member.kick();
        }
        teamMemberRepository.save(member);
        mvc.perform(as(invitee, jsonRequest("/accept", "{\"token\":\"" + tokenFrom(link) + "\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 이 팀에 참여한 이력이 있습니다."));
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void fullRoleAndClosedTeamRejectAcceptanceWithoutUsingLink() {
        team.updateInfo(team.getName(), team.getDescription(), team.getActivityMode(),
                (short) 3, team.getWeeklyMeetingCount(), team.getMeetingPlace());
        teamRepository.save(team);
        TeamInviteLinkResponse link = issueLink();
        User invitee = createUser();
        teamMemberRepository.save(TeamMember.createMember(team.getId(), createUser().getId(), ROLE_CODE));

        assertThatThrownBy(() -> teamService.joinViaInviteLink(invitee.getId(), team.getId(), tokenFrom(link)))
                .isInstanceOf(ConflictException.class).hasMessage("모집 분야 정원이 마감되었습니다.");
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getUsedAt()).isNull();

        team.closeRecruitment();
        teamRepository.save(team);
        assertThatThrownBy(() -> teamService.joinViaInviteLink(invitee.getId(), team.getId(), tokenFrom(link)))
                .isInstanceOf(ConflictException.class).hasMessage("모집 중인 팀이 아닙니다.");
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void activeLimitPreservesExistingLinksAndRevocationFreesOneSlot() {
        TeamInviteLinkResponse oldest = issueLink();
        for (int i = 1; i < 10; i++) {
            issueLink();
        }
        assertThatThrownBy(this::issueLink).isInstanceOf(ConflictException.class).hasMessageContaining("10개");
        assertThat(teamService.previewInviteLink(team.getId(), tokenFrom(oldest)).roleCode()).isEqualTo(ROLE_CODE);

        teamService.revokeInviteLink(leader.getId(), team.getId(), oldest.inviteLinkId());
        assertThat(issueLink().inviteLinkId()).isNotEqualTo(oldest.inviteLinkId());
        assertThatThrownBy(this::issueLink).isInstanceOf(ConflictException.class).hasMessageContaining("10개");
    }

    @Test
    void dailyLimitCountsRevokedLinksUntilTheirOriginalExpiry() {
        for (int i = 0; i < 30; i++) {
            TeamInviteLinkResponse link = issueLink();
            teamService.revokeInviteLink(leader.getId(), team.getId(), link.inviteLinkId());
        }
        assertThatThrownBy(this::issueLink).isInstanceOf(ConflictException.class).hasMessageContaining("30개");

        when(clock.instant()).thenReturn(NOW.plus(24, ChronoUnit.HOURS));
        assertThat(issueLink().expiresAt()).isEqualTo(NOW.plus(48, ChronoUnit.HOURS));
    }

    @Test
    void linkUsesLeaderSelectedRoleAndClosesTeamWhenItFills() {
        User invitee = createUser();
        assertThatThrownBy(() -> teamService.createInviteLink(invitee.getId(), team.getId(), ROLE_CODE))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> teamService.createInviteLink(leader.getId(), team.getId(), "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        TeamInviteLinkResponse link = issueLink();
        String token = tokenFrom(link);

        assertThat(link.inviteUrl()).startsWith("https://example.com/teams/" + team.getId() + "/invite#token=");
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(24, ChronoUnit.HOURS));
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getTokenHash())
                .hasSize(64).isNotEqualTo(token);
        assertThat(teamService.previewInviteLink(team.getId(), token).roleCode()).isEqualTo(ROLE_CODE);

        var joined = teamService.joinViaInviteLink(invitee.getId(), team.getId(), token);
        assertThat(teamMemberRepository.findById(joined.teamMemberId()).orElseThrow().getRoleCode())
                .isEqualTo(ROLE_CODE);
        assertThat(teamRepository.findById(team.getId()).orElseThrow().getStatus()).isEqualTo(Team.Status.CLOSED);
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void failedJoinDoesNotUseLinkAndLeaderCanRevokeIt() {
        TeamInviteLinkResponse link = issueLink();
        String token = tokenFrom(link);
        User invitee = createUser();
        teamMemberRepository.save(TeamMember.createMember(team.getId(), createUser().getId(), ROLE_CODE));

        assertThatThrownBy(() -> teamService.joinViaInviteLink(invitee.getId(), team.getId(), token))
                .isInstanceOf(ConflictException.class).hasMessage("팀 정원이 마감되었습니다.");
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getUsedAt()).isNull();

        assertThatThrownBy(() -> teamService.revokeInviteLink(invitee.getId(), team.getId(), link.inviteLinkId()))
                .isInstanceOf(ForbiddenException.class);
        teamService.revokeInviteLink(leader.getId(), team.getId(), link.inviteLinkId());
        assertThatThrownBy(() -> teamService.previewInviteLink(team.getId(), token))
                .isInstanceOf(ConflictException.class).hasMessageContaining("폐기");
    }

    @Test
    void linkExpiresAtTheExactDeadline() {
        TeamInviteLinkResponse link = issueLink();
        when(clock.instant()).thenReturn(link.expiresAt());

        assertThatThrownBy(() -> teamService.joinViaInviteLink(createUser().getId(), team.getId(), tokenFrom(link)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("만료");
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()).orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void concurrentRedemptionAllowsOnlyOneJoin() throws Exception {
        TeamInviteLinkResponse link = issueLink();
        String token = tokenFrom(link);
        Long firstUserId = createUser().getId();
        Long secondUserId = createUser().getId();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> redeemAfterBarrier(start, firstUserId, token));
            var second = executor.submit(() -> redeemAfterBarrier(start, secondUserId, token));
            assertThat(new boolean[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(teamMemberRepository.countByTeamIdAndStatus(team.getId(), TeamMember.Status.ACTIVE)).isEqualTo(2);
        assertThat(inviteLinkRepository.findById(link.inviteLinkId()))
                .map(TeamInviteLink::getUsedAt).contains(NOW);
    }

    private boolean redeemAfterBarrier(CyclicBarrier start, Long userId, String token) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            teamService.joinViaInviteLink(userId, team.getId(), token);
            return true;
        } catch (ConflictException e) {
            assertThat(e).hasMessageContaining("사용 완료");
            return false;
        }
    }

    private TeamInviteLinkResponse issueLink() {
        return teamService.createInviteLink(leader.getId(), team.getId(), ROLE_CODE);
    }

    private String tokenFrom(TeamInviteLinkResponse link) {
        return URI.create(link.inviteUrl()).getFragment().substring("token=".length());
    }

    private User createUser() {
        return userRepository.save(User.createAdmin(UUID.randomUUID() + "@example.com", "encoded", "초대 테스트"));
    }

    private User createStudent(boolean verified) {
        String key = UUID.randomUUID().toString();
        User user = User.createOAuthUser(key + "@example.com", "초대 테스트", User.OAuthProvider.KAKAO, key);
        if (verified) {
            user.verifyUniversityEmail(key + "@uos.ac.kr");
        }
        return userRepository.save(user);
    }

    private MockHttpServletRequestBuilder jsonRequest(String suffix, String body) {
        String path = "/api/teams/" + team.getId() + "/invite-links" + suffix;
        return post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor));
    }
}
