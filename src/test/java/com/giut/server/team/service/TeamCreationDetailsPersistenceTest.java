package com.giut.server.team.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.Competition;
import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.profile.service.DefaultProfileRoleService;
import com.giut.server.team.entity.TeamMember;
import com.giut.server.team.repository.TeamMemberRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:team_creation_details;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class TeamCreationDetailsPersistenceTest {

    private static final String TEAMS = "/api/teams";

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired CompetitionRepository competitions;
    @Autowired TeamMemberRepository members;
    @Autowired DefaultProfileRoleService profileRoles;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper objectMapper;

    private MockMvc mvc;
    private User leader;
    private Competition competition;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader = createUser();
        profileRoles.seedDefaults(leader.getId());
        Instant now = Instant.now();
        competition = competitions.save(Competition.create(
                "팀 생성 화면 테스트", Competition.Category.WEB_MOBILE_IT, "주최사", "대학생", "공모전 소개",
                now.minusSeconds(3600), now.plusSeconds(3600),
                Competition.PublicationStatus.PUBLISHED, Competition.VerificationStatus.MANUALLY_VERIFIED, leader
        ));
    }

    @Test
    void persistsLeaderRoleAndToolsAndLeavesSameRoleOpenForApplicants() throws Exception {
        Long teamId = createOnlineTeam();
        String teamPath = TEAMS + "/" + teamId;
        assertThat(members.findByTeamIdAndUserId(teamId, leader.getId())).hasValueSatisfying(member -> {
            assertThat(member.getRole()).isEqualTo(TeamMember.Role.LEADER);
            assertThat(member.getRoleCode()).isEqualTo("BACKEND_DEVELOPER");
        });

        mvc.perform(as(leader, get(teamPath + "/members")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].role").value("LEADER"))
                .andExpect(jsonPath("$.members[0].roleName").value("백엔드 개발자"))
                .andExpect(jsonPath("$.members[0].primaryRole").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.members[0].primaryRoleName").value("개발"));
        mvc.perform(as(leader, get(teamPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderRoleCode").value("BACKEND_DEVELOPER"))
                .andExpect(jsonPath("$.currentMemberCount").value(1))
                .andExpect(jsonPath("$.meetingPlace").isEmpty())
                .andExpect(jsonPath("$.collaborationTools.length()").value(2))
                .andExpect(jsonPath("$.recruitments[0].filledCount").value(0));
        mvc.perform(as(leader, get(teamPath + "/recruitments")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitments[0].filledCount").value(0));

        User applicant = createUser();
        var applied = mvc.perform(as(applicant, json(post(teamPath + "/applications"),
                        """
                        {"roleCode":"BACKEND_DEVELOPER","answers":[]}
                        """)))
                .andExpect(status().isCreated()).andReturn();
        long applicationId = objectMapper.readTree(applied.getResponse().getContentAsString())
                .get("applicationId").asLong();
        mvc.perform(as(leader, json(post(teamPath + "/applications/" + applicationId + "/approve"),
                        """
                        {"roleCode":"BACKEND_DEVELOPER"}
                        """)))
                .andExpect(status().isOk());
        mvc.perform(as(leader, get(teamPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMemberCount").value(2))
                .andExpect(jsonPath("$.recruitments[0].filledCount").value(1));
    }

    @Test
    void changingActivityModeClearsSettingsThatNoLongerApply() throws Exception {
        String teamPath = TEAMS + "/" + createOnlineTeam();
        updateActivity(teamPath, "HYBRID", ",\"meetingPlace\":\"CAMPUS\",\"collaborationTools\":[\"GOOGLE_MEET\"]")
                .andExpect(jsonPath("$.meetingPlace").value("CAMPUS"))
                .andExpect(jsonPath("$.collaborationTools[0]").value("GOOGLE_MEET"));

        updateActivity(teamPath, "OFFLINE", ",\"meetingPlace\":\"SEOUL\"")
                .andExpect(jsonPath("$.meetingPlace").value("SEOUL"))
                .andExpect(jsonPath("$.collaborationTools.length()").value(0));

        updateActivity(teamPath, "ONLINE", ",\"collaborationTools\":[\"SLACK\"]")
                .andExpect(jsonPath("$.meetingPlace").isEmpty())
                .andExpect(jsonPath("$.collaborationTools[0]").value("SLACK"));

        mvc.perform(as(leader, get(teamPath)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPlace").isEmpty())
                .andExpect(jsonPath("$.collaborationTools[0]").value("SLACK"));
    }

    @Test
    void invalidRoleAndActivitySettingsReturnBadRequest() throws Exception {
        String online = createPayload("ONLINE", ",\"collaborationTools\":[\"DISCORD\"]");
        for (String payload : new String[]{
                online.replace("\"leaderRoleCode\":\"BACKEND_DEVELOPER\",", ""),
                online.replace("\"leaderRoleCode\":\"BACKEND_DEVELOPER\"", "\"leaderRoleCode\":\"UNKNOWN\""),
                createPayload("ONLINE", ""),
                createPayload("ONLINE", ",\"meetingPlace\":\"CAMPUS\",\"collaborationTools\":[\"DISCORD\"]"),
                createPayload("OFFLINE", ",\"meetingPlace\":\"CAMPUS\",\"collaborationTools\":[\"DISCORD\"]"),
                createPayload("HYBRID", ",\"meetingPlace\":\"CAMPUS\""),
                createPayload("HYBRID", ",\"collaborationTools\":[\"DISCORD\"]"),
                createPayload("ONLINE", ",\"collaborationTools\":[\"DISCORD\",\"DISCORD\"]"),
                createPayload("ONLINE", ",\"collaborationTools\":[\"UNKNOWN\"]")
        }) {
            mvc.perform(as(leader, json(post(TEAMS), payload))).andExpect(status().isBadRequest());
        }
        createOnlineTeam();
    }

    private Long createOnlineTeam() throws Exception {
        var response = mvc.perform(as(leader, json(post(TEAMS),
                        createPayload("ONLINE", ",\"collaborationTools\":[\"DISCORD\",\"NOTION\"]"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaderRoleCode").value("BACKEND_DEVELOPER"))
                .andExpect(jsonPath("$.collaborationTools.length()").value(2))
                .andExpect(jsonPath("$.recruitments[0].requiredCount").value(1))
                .andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString()).get("teamId").asLong();
    }

    private ResultActions updateActivity(
            String path, String mode, String activityDetails
    ) throws Exception {
        return mvc.perform(as(leader, json(put(path), """
                        {"name":"팀","activityMode":"%s","maxMemberCount":4,"weeklyMeetingCount":4%s}
                        """.formatted(mode, activityDetails))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyMeetingCount").value(4));
    }

    private String createPayload(String mode, String activityDetails) {
        return """
                {"competitionId":%d,"name":"팀","activityMode":"%s","maxMemberCount":4,"weeklyMeetingCount":1,
                 "leaderRoleCode":"BACKEND_DEVELOPER","recruitments":[{"roleCode":"BACKEND_DEVELOPER","requiredCount":1}]%s}
                """.formatted(competition.getId(), mode, activityDetails);
    }

    private User createUser() {
        return users.save(User.createAdmin(UUID.randomUUID() + "@example.com", "encoded", "팀 테스트"));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor)).with(servletRequest -> {
            servletRequest.setServletPath(servletRequest.getRequestURI());
            return servletRequest;
        });
    }
}
