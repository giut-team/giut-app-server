package com.giut.server.auth.controller;

import com.giut.server.auth.security.JwtProvider;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그아웃은 로그인이 필요한 API다. 쓸 수 없는 토큰이나 토큰 없는 요청은 거절하고 쿠키를 건드리지 않는다.
 * 보호 API의 403도 쿠키를 지우지 않는다. 실제 보안 필터, JWT, 격리된 H2 DB를 쓴다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_auth_logout;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false", "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "jwt.expiration-time=3600000", "jwt.refresh-expiration-time=1209600000",
        "DISCORD_ALERT_WEBHOOK_URL=", "DISCORD_SLOW_API_WEBHOOK_URL=",
        "DISCORD_PROFILE_REPORT_WEBHOOK_URL=", "DISCORD_SLOW_API_ENABLED=false",
        "DISCORD_PROFILE_REPORT_ENABLED=false",
        "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class AuthLogoutSessionTest {

    private static final String LOGOUT = "/api/auth/logout";
    private static final String ACCESS_COOKIE = "giut_access_token";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwt;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() {
        users.deleteAll();
    }

    @Test
    void revokedCookieOnProtectedRouteIsForbiddenWithoutSetCookie() throws Exception {
        // 학교 인증을 마친 사용자로 확인해, 403이 학교 인증 필터가 아니라 폐기된 토큰 때문임을 분명히 한다.
        User student = saveStudent();
        student.verifyUniversityEmail("student@uos.ac.kr");
        String revoked = revokedTokenOf(users.saveAndFlush(student));

        MvcResult result = mvc.perform(get("/api/teams/me").cookie(new Cookie(ACCESS_COOKIE, revoked)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void logoutWithRevokedCookieIsForbiddenWithoutSetCookie() throws Exception {
        User student = saveStudent();
        String revoked = revokedTokenOf(student);
        long versionBefore = users.findById(student.getId()).orElseThrow().getTokenVersion();

        MvcResult result = mvc.perform(post(LOGOUT).cookie(new Cookie(ACCESS_COOKIE, revoked)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(users.findById(student.getId()).orElseThrow().getTokenVersion()).isEqualTo(versionBefore);
    }

    @Test
    void logoutWithoutTokenIsUnauthorizedWithoutSetCookie() throws Exception {
        MvcResult result = mvc.perform(post(LOGOUT))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void logoutWithValidTokenRevokesTokensAndClearsCookies() throws Exception {
        // 학교 인증 전 사용자도 로그아웃할 수 있어야 한다.
        User student = saveStudent();
        String token = jwt.generateAccessToken(student);
        long versionBefore = student.getTokenVersion();

        MvcResult result = mvc.perform(post(LOGOUT).cookie(new Cookie(ACCESS_COOKIE, token)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertLoginCookiesCleared(result);
        assertThat(users.findById(student.getId()).orElseThrow().getTokenVersion()).isEqualTo(versionBefore + 1);
    }

    private User saveStudent() {
        return users.saveAndFlush(User.createOAuthUser(
                "student@example.com", "학생", User.OAuthProvider.KAKAO, "logout-student"));
    }

    private String revokedTokenOf(User student) {
        String token = jwt.generateAccessToken(student);
        User stored = users.findById(student.getId()).orElseThrow();
        stored.bumpTokenVersion();
        users.saveAndFlush(stored);
        return token;
    }

    private void assertLoginCookiesCleared(MvcResult result) {
        List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(headers)
                .hasSize(2)
                .anySatisfy(header -> assertThat(header).startsWith("giut_access_token=;").contains("Max-Age=0"))
                .anySatisfy(header -> assertThat(header).startsWith("giut_refresh_token=;").contains("Max-Age=0"));
    }
}
