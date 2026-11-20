package com.giut.server.team.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.team.entity.Team;
import com.giut.server.team.entity.TeamApplication;
import com.giut.server.team.entity.TeamApplicationQuestion;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.entity.TeamRecruitment;
import com.giut.server.team.repository.TeamApplicationQuestionRepository;
import com.giut.server.team.repository.TeamApplicationRepository;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.team.repository.TeamRecruitmentRepository;
import com.giut.server.team.repository.TeamRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:team_application_availability;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TeamApplicationAvailabilityPersistenceTest {

    private static final String MY_APPLICATIONS = "/api/teams/applications/me";
    private static final String ROLE_CODE = "BACKEND_DEVELOPER";

    @Autowired WebApplicationContext context;
    @Autowired JwtProvider jwt;
    @Autowired UserRepository users;
    @Autowired CompetitionRepository competitions;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TeamRecruitmentRepository recruitments;
    @Autowired TeamApplicationQuestionRepository questions;
    @Autowired TeamApplicationRepository applications;

    private MockMvc mvc;
    private User leader;
    private User applicant;
    private Team team;
    private TeamApplicationQuestion question;
    private String teamApplications;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader = createUser("팀장");
        applicant = createUser("지원자");
        Instant now = Instant.now();
        Competition competition = competitions.save(Competition.create(
                "지원서 테스트 공모전", Competition.Category.WEB_MOBILE_IT, "주최사", "대학생", "공모전 소개",
                now.minusSeconds(3600), now.plusSeconds(3600),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, leader
        ));
        team = teams.save(Team.create(competition, leader, "지원할 팀", null,
                Team.ActivityMode.HYBRID, (short) 4, (short) 1, Team.MeetingPlace.CAMPUS));
        members.save(TeamMember.createLeader(team.getId(), leader.getId()));
        recruitments.save(TeamRecruitment.create(team.getId(), ROLE_CODE, (short) 1));
        question = questions.save(TeamApplicationQuestion.create(team.getId(), "지원 동기는 무엇인가요?", true, 1));
        teamApplications = "/api/teams/" + team.getId() + "/applications";
    }

    @Test
    void submittedAvailabilityAndAnswersAreVisibleToApplicantAndTeamLeader() throws Exception {
        submit(",\"weeklyAvailableHours\":20,\"meetingTimeSlots\":[\"EVENING\",\"MORNING\"]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.weeklyAvailableHours").value(20))
                .andExpect(jsonPath("$.meetingTimeSlots[0]").value("MORNING"))
                .andExpect(jsonPath("$.meetingTimeSlots[1]").value("EVENING"));

        for (ResultActions response : new ResultActions[]{
                mvc.perform(as(applicant, get(MY_APPLICATIONS))),
                mvc.perform(as(leader, get(teamApplications)))
        }) {
            response.andExpect(status().isOk())
                    .andExpect(jsonPath("$.applications[0].userId").value(applicant.getId()))
                    .andExpect(jsonPath("$.applications[0].weeklyAvailableHours").value(20))
                    .andExpect(jsonPath("$.applications[0].meetingTimeSlots[0]").value("MORNING"))
                    .andExpect(jsonPath("$.applications[0].meetingTimeSlots[1]").value("EVENING"))
                    .andExpect(jsonPath("$.applications[0].answers[0].answer").value("백엔드 구현에 참여하고 싶습니다."));
        }
        User other = createUser("다른 사용자");
        mvc.perform(as(other, get(teamApplications))).andExpect(status().isForbidden());
        mvc.perform(as(other, get(MY_APPLICATIONS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\"weeklyAvailableHours\":1",
            ",\"weeklyAvailableHours\":1,\"meetingTimeSlots\":[]"
    })
    void supportsSubmittingHoursWithoutAnyMeetingTimeSlot(String fields) throws Exception {
        submit(fields).andExpect(status().isCreated())
                .andExpect(jsonPath("$.weeklyAvailableHours").value(1))
                .andExpect(jsonPath("$.meetingTimeSlots").isEmpty());
        mvc.perform(as(applicant, get(MY_APPLICATIONS))).andExpect(status().isOk())
                .andExpect(jsonPath("$.applications[0].weeklyAvailableHours").value(1))
                .andExpect(jsonPath("$.applications[0].meetingTimeSlots").isEmpty());
    }

    @Test
    void olderRequestCanOmitBothFields() throws Exception {
        submit("").andExpect(status().isCreated())
                .andExpect(jsonPath("$.weeklyAvailableHours").isEmpty())
                .andExpect(jsonPath("$.meetingTimeSlots").isEmpty());
        mvc.perform(as(leader, get(teamApplications))).andExpect(status().isOk())
                .andExpect(jsonPath("$.applications[0].weeklyAvailableHours").isEmpty())
                .andExpect(jsonPath("$.applications[0].meetingTimeSlots").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",\"weeklyAvailableHours\":0",
            ",\"weeklyAvailableHours\":21",
            ",\"meetingTimeSlots\":[\"MORNING\"]",
            ",\"weeklyAvailableHours\":5,\"meetingTimeSlots\":[\"NIGHT\"]",
            ",\"weeklyAvailableHours\":5,\"meetingTimeSlots\":[null]",
            ",\"weeklyAvailableHours\":5,\"meetingTimeSlots\":[\"MORNING\",\"MORNING\"]"
    })
    void invalidAvailabilityIsRejectedWithoutSavingApplication(String fields) throws Exception {
        submit(fields).andExpect(status().isBadRequest());
        assertThat(applications.findAllByUserIdOrderByAppliedAtDesc(applicant.getId())).isEmpty();
    }

    @Test
    void directInvitationsDoNotRequireAvailability() {
        TeamApplication invitation = applications.saveAndFlush(
                TeamApplication.createInvitation(team.getId(), applicant.getId(), ROLE_CODE, null)
        );
        assertThat(applications.findById(invitation.getId()).orElseThrow().getWeeklyAvailableHours()).isNull();
    }

    private ResultActions submit(String availabilityFields) throws Exception {
        String body = """
                {"roleCode":"%s","answers":[{"questionId":%d,"answer":"백엔드 구현에 참여하고 싶습니다."}]%s}
                """.formatted(ROLE_CODE, question.getId(), availabilityFields);
        return mvc.perform(as(applicant, post(teamApplications)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private User createUser(String nickname) {
        return users.save(User.createAdmin(UUID.randomUUID() + "@example.com", "encoded", nickname));
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(user)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
