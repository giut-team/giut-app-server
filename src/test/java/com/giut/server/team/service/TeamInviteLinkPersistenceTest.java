package com.giut.server.team.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
        "app.team-invite.frontend-base-url=https://example.com"
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
    @MockitoBean Clock clock;

    private User leader;
    private Team team;

    @BeforeEach
    void createRecruitingTeam() {
        when(clock.instant()).thenReturn(NOW);
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
}
