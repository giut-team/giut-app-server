package com.giut.server.competition.controller;

import com.giut.server.competition.repository.CompetitionRepository;
import com.giut.server.competition.repository.CompetitionUrlRepository;
import com.giut.server.competition.repository.CompetitionVerificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_ai_ingestion;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.ai-ingestion.secret=0123456789abcdef0123456789abcdef",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL="
})
class AiCompetitionControllerTest {
    private static final String PATH = "/api/internal/ai/competitions";
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String BODY = """
            {"title":"AI 공모전", "category":"WEB_MOBILE_IT", "targetParticipants":"대학생",
             "applicationStartAt":"2026-11-01T00:00:00Z", "applicationEndAt":"2026-12-01T00:00:00Z",
             "urls":[{"type":"RECRUITMENT","url":"https://example.com/ai-contest","primary":true}]}
            """;
    @Autowired WebApplicationContext context;
    @Autowired CompetitionRepository competitions;
    @Autowired CompetitionUrlRepository urls;
    @Autowired CompetitionVerificationLogRepository logs;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        logs.deleteAll();
        urls.deleteAll();
        competitions.deleteAll();
    }

    @Test
    void keyIsRequiredAndCannotUnlockOtherEndpoints() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-AI-API-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/competitions").header("X-AI-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        assertThat(competitions.count()).isZero();
    }

    @Test
    void verifiedAiSubmissionIsPublishedAndDuplicateCannotOverwrite() throws Exception {
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.verificationStatus").value("AUTO_VERIFIED"));
        mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
        assertThat(competitions.count()).isEqualTo(1);
        assertThat(urls.count()).isEqualTo(1);
        assertThat(logs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getVerificationMethod().name()).isEqualTo("AUTO_EXTRACTION");
            assertThat(log.getActorUser()).isNull();
        });
    }

    @Test
    void requiredFieldsDatesAndPrimaryUrlAreValidated() throws Exception {
        for (String body : new String[] {
                BODY.replace("\"대학생\"", "\"\""),
                BODY.replace("2026-12-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                BODY.replace("\"primary\":true", "\"primary\":false"),
                BODY.replace("\"applicationStartAt\":\"2026-11-01T00:00:00Z\",", "")
        }) {
            mvc.perform(post(PATH).header("X-AI-API-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(competitions.count()).isZero();
    }
}
