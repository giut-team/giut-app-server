package com.giut.server.team.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamApplication;
import com.giut.server.team.entity.TeamApplicationAnswer;
import com.giut.server.team.entity.TeamApplicationQuestion;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.repository.TeamApplicationAnswerRepository;
import com.giut.server.team.repository.TeamApplicationQuestionRepository;
import com.giut.server.team.repository.TeamApplicationRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:my_team_dashboard;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=",
        "DISCORD_SLOW_API_WEBHOOK_URL=",
        "app.ai-extraction.jobs.worker-enabled=false",
        "app.push.worker-enabled=false"
})
class MyTeamDashboardPersistenceTest {

    private static final String ROOT = "/api/teams";
    private static final String COMPETITION_TITLE = "대학생 공모전";

    @Autowired WebApplicationContext context;
    @Autowired JwtProvider jwt;
    @Autowired UserRepository users;
    @Autowired CompetitionRepository competitions;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TeamApplicationRepository applications;
    @Autowired TeamApplicationAnswerRepository answers;
    @Autowired TeamApplicationQuestionRepository questions;
    @Autowired TeamService teamService;
    @Autowired EntityManagerFactory entityManagerFactory;

    private MockMvc mvc;
    private User leader;
    private User applicant;
    private User teammate;
    private Competition competition;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader = user("팀장");
        applicant = user("지원자");
        teammate = user("팀원");
        Instant now = Instant.now();
        competition = competitions.save(Competition.create(
                COMPETITION_TITLE, Competition.Category.WEB_MOBILE_IT, "주최사", "대학생", "공모전 소개",
                now.minusSeconds(3600), now.plusSeconds(3600),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, leader
        ));
    }

    @Test
    void teamCardsCountOnlyPendingApplicationsAndHideCountsFromMembers() throws Exception {
        Team team = team("모집 팀");
        members.save(TeamMember.createMember(team.getId(), teammate.getId(), "BACKEND_DEVELOPER"));
        applications.save(application(team, applicant));
        TeamApplication rejected = application(team, user("거절된 지원자"));
        rejected.reject("다른 분야를 모집합니다.");
        applications.save(rejected);
        applications.save(invitation(team, user("제안 받은 사용자")));

        readAs(leader, "/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].competitionTitle").value(COMPETITION_TITLE))
                .andExpect(jsonPath("$.teams[0].pendingApplicationCount").value(1))
                .andExpect(jsonPath("$.teams[0].currentMemberCount").value(2))
                .andExpect(jsonPath("$.teams[0].myRole").value("LEADER"));
        readAs(teammate, "/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].competitionTitle").value(COMPETITION_TITLE))
                .andExpect(jsonPath("$.teams[0].pendingApplicationCount").value(0))
                .andExpect(jsonPath("$.teams[0].myRole").value("MEMBER"));
    }

    @Test
    void historiesSeparateApplicationsAndInvitationsAndKeepArchivedTeamSummary() throws Exception {
        Team appliedTeam = team("지원한 팀");
        appliedTeam.archive();
        teams.save(appliedTeam);
        TeamApplication submitted = saveApplicationWithAnswer(appliedTeam, "제출한 답변");
        Team invitedTeam = team("제안 받은 팀");
        applications.save(invitation(invitedTeam, applicant));

        readAs(applicant, "/applications/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.length()").value(1))
                .andExpect(jsonPath("$.applications[0].applicationId").value(submitted.getId()))
                .andExpect(jsonPath("$.applications[0].team.name").value("지원한 팀"))
                .andExpect(jsonPath("$.applications[0].team.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.applications[0].team.currentMemberCount").value(1))
                .andExpect(jsonPath("$.applications[0].team.maxMemberCount").value(4))
                .andExpect(jsonPath("$.applications[0].team.competitionTitle").value(COMPETITION_TITLE))
                .andExpect(jsonPath("$.applications[0].answers[0].answer").value("제출한 답변"));
        readAs(applicant, "/invitations/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.length()").value(1))
                .andExpect(jsonPath("$.applications[0].teamId").value(invitedTeam.getId()));
        readAs(teammate, "/invitations/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.applications").isEmpty());
    }

    @Test
    void leaderSeesPendingApplicantNicknameAndAnswersButMembersCannotReadThem() throws Exception {
        Team team = team("받은 지원 팀");
        TeamApplication submitted = saveApplicationWithAnswer(team, "지원서 답변");
        applications.save(invitation(team, user("제안 대상")));
        TeamApplication approved = application(team, user("승인된 지원자"));
        approved.approve("BACKEND_DEVELOPER");
        applications.save(approved);
        members.save(TeamMember.createMember(team.getId(), teammate.getId(), "BACKEND_DEVELOPER"));

        String path = "/" + team.getId() + "/applications";
        readAs(leader, path).andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.length()").value(1))
                .andExpect(jsonPath("$.applications[0].applicationId").value(submitted.getId()))
                .andExpect(jsonPath("$.applications[0].applicantNickname").value("지원자"))
                .andExpect(jsonPath("$.applications[0].roleCode").value("BACKEND_DEVELOPER"))
                .andExpect(jsonPath("$.applications[0].appliedAt").exists())
                .andExpect(jsonPath("$.applications[0].answers[0].answer").value("지원서 답변"));
        readAs(teammate, path).andExpect(status().isForbidden());
    }

    @Test
    void moreTeamsAndApplicationsDoNotIncreaseQueryCountPerList() {
        saveApplicationWithAnswer(team("첫 번째 팀"), "첫 번째 답변");
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            assertThat(teamService.getMyApplications(applicant.getId()).applications()).hasSize(1);
            long firstHistoryQueries = stats.getPrepareStatementCount();
            stats.clear();
            assertThat(teamService.getMyTeams(leader.getId()).teams()).hasSize(1);
            long firstMyTeamsQueries = stats.getPrepareStatementCount();

            saveApplicationWithAnswer(team("두 번째 팀"), "두 번째 답변");
            saveApplicationWithAnswer(team("세 번째 팀"), "세 번째 답변");
            stats.clear();
            assertThat(teamService.getMyApplications(applicant.getId()).applications()).hasSize(3);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(firstHistoryQueries).isLessThanOrEqualTo(5);
            stats.clear();
            assertThat(teamService.getMyTeams(leader.getId()).teams()).hasSize(3);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(firstMyTeamsQueries).isLessThanOrEqualTo(4);
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    private TeamApplication saveApplicationWithAnswer(Team team, String answer) {
        TeamApplication submitted = applications.save(application(team, applicant));
        TeamApplicationQuestion question = questions.save(
                TeamApplicationQuestion.create(team.getId(), "지원 동기는 무엇인가요?", true, 1)
        );
        answers.save(TeamApplicationAnswer.create(submitted.getId(), question.getId(), answer));
        return submitted;
    }

    private TeamApplication application(Team team, User user) {
        return TeamApplication.create(team.getId(), user.getId(), "BACKEND_DEVELOPER", null);
    }

    private TeamApplication invitation(Team team, User user) {
        return TeamApplication.createInvitation(team.getId(), user.getId(), "BACKEND_DEVELOPER", null);
    }

    private Team team(String name) {
        Team team = teams.save(Team.create(competition, leader, name, null,
                Team.ActivityMode.HYBRID, (short) 4, (short) 1, Team.MeetingPlace.CAMPUS));
        members.save(TeamMember.createLeader(team.getId(), leader.getId()));
        return team;
    }

    private User user(String nickname) {
        return users.save(User.createAdmin(UUID.randomUUID() + "@example.com", "encoded", nickname));
    }

    private ResultActions readAs(User user, String path) throws Exception {
        return mvc.perform(get(ROOT + path).servletPath(ROOT + path)
                .header("Authorization", "Bearer " + jwt.generateAccessToken(user)));
    }
}
