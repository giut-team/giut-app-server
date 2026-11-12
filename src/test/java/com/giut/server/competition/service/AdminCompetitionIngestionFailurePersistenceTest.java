package com.giut.server.competition.service;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.competition.entity.CompetitionIngestionFailure;
import com.giut.server.competition.entity.CompetitionIngestionFailure.FailureType;
import com.giut.server.competition.repository.CompetitionIngestionFailureRepository;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security + H2; cleanup targets this disposable DB only. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_ingestion_failures;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL="
})
class AdminCompetitionIngestionFailurePersistenceTest {
    private static final String PATH = "/api/admin/competitions/ingestion-failures";
    private static final Instant DAY1 = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-11-12T15:00:00Z");
    private static final Instant DAY3 = Instant.parse("2026-11-13T15:00:00Z");

    @Autowired private WebApplicationContext context;
    @Autowired private JwtProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private CompetitionIngestionFailureRepository failures;
    private MockMvc mvc;
    private User admin;
    private User student;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = users.saveAndFlush(User.createAdmin("failure-admin@example.com", "hash", "관리자"));
        student = User.createOAuthUser("failure-student@example.com", "학생", User.OAuthProvider.KAKAO, "failure-student");
        student.verifyUniversityEmail("failure-student@uos.ac.kr");
        student = users.saveAndFlush(student);
        failures.saveAll(List.of(
                failure("batch-1", 0, FailureType.REJECTED, "첫날 부적합", DAY1),
                failure("batch-1", 2, FailureType.DUPLICATE, "첫날 중복", DAY1),
                failure("batch-2", 1, FailureType.INVALID_FIELD, "둘째날 입력", DAY2),
                failure("batch-3", 0, FailureType.DUPLICATE, "셋째날 중복", DAY3)));
    }

    @AfterEach
    void cleanUp() {
        failures.deleteAll();
        users.deleteAll();
    }

    @Test
    void listsNewestReceivedFirstWithReceivedAtAndRawPayload() throws Exception {
        mvc.perform(as(admin, get(PATH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.failures[0].title").value("셋째날 중복"))
                .andExpect(jsonPath("$.failures[0].receivedAt").value("2026-11-13T15:00:00Z"))
                .andExpect(jsonPath("$.failures[0].rawPayload").value("{\"title\":\"셋째날 중복\"}"))
                .andExpect(jsonPath("$.failures[0].failureType").value("DUPLICATE"))
                .andExpect(jsonPath("$.failures[0].batchId").value("batch-3"))
                .andExpect(jsonPath("$.failures[2].itemIndex").value(0))
                .andExpect(jsonPath("$.failures[3].itemIndex").value(2));
    }

    @Test
    void receivedAtRangeIsFromInclusiveToExclusive() throws Exception {
        mvc.perform(as(admin, get(PATH).param("from", DAY1.toString()).param("to", DAY2.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.failures[*].batchId").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("batch-1"))));
        mvc.perform(as(admin, get(PATH).param("from", DAY2.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(admin, get(PATH).param("to", DAY2.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(admin, get(PATH).param("from", "2026-11-12T00:00:00+09:00").param("to", "2026-11-13T00:00:00+09:00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void timeTypeAndBatchFiltersCombineAndPagingCountsBeforeSlicing() throws Exception {
        mvc.perform(as(admin, get(PATH).param("failureType", "DUPLICATE").param("from", DAY1.toString())
                        .param("to", DAY3.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.failures[0].title").value("첫날 중복"));
        mvc.perform(as(admin, get(PATH).param("batchId", " batch-1 ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(admin, get(PATH).param("size", "3").param("page", "1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.failures.length()").value(1));
    }

    @Test
    void invalidRangeOrParametersAreRejected() throws Exception {
        for (String[] params : List.of(
                new String[]{"from", DAY2.toString(), "to", DAY1.toString()},
                new String[]{"from", DAY2.toString(), "to", DAY2.toString()},
                new String[]{"from", "2026-11-12", "to", DAY3.toString()},
                new String[]{"from", "yesterday", "to", DAY3.toString()},
                new String[]{"failureType", "UNKNOWN", "to", DAY3.toString()},
                new String[]{"size", "101", "to", DAY3.toString()},
                new String[]{"page", "-1", "to", DAY3.toString()})) {
            mvc.perform(as(admin, get(PATH).param(params[0], params[1]).param(params[2], params[3])))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void onlyCurrentActiveAdminsCanRead() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(as(student, get(PATH))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.failures").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "SUSPENDED"})
    void staleAdminTokenIsRejected(String changed) throws Exception {
        String token = jwt.generateAccessToken(admin);
        if ("STUDENT".equals(changed)) ReflectionTestUtils.setField(admin, "role", User.Role.STUDENT);
        else ReflectionTestUtils.setField(admin, "status", User.Status.SUSPENDED);
        users.saveAndFlush(admin);
        mvc.perform(withToken(token, get(PATH))).andExpect(status().isForbidden());
    }

    private CompetitionIngestionFailure failure(String batchId, int index, FailureType type, String title, Instant receivedAt) {
        return CompetitionIngestionFailure.create(batchId, index, type, title + " 사유", title,
                "https://example.com/" + batchId + "/" + index, "{\"title\":\"" + title + "\"}", receivedAt);
    }

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
