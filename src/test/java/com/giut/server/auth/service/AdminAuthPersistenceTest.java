package com.giut.server.auth.service;

import com.giut.server.auth.dto.request.SignUpRequest;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.exception.ForbiddenException;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses real security filters, JWT, service commits and an isolated H2 database, not a shared DB. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:giut_admin_auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class AdminAuthPersistenceTest {

    private static final String LOGIN = "/api/admin/auth/login";
    private static final String PASSWORD = "Test-password-123!";
    private static final String NEW_EMAIL = "new-admin@example.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwt;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() {
        users.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/auth/signup", "/api/admin/auth/sign-up"})
    void anonymousSignupIsRejectedEvenWhenNoAdministratorExists(String path) throws Exception {
        assertThat(users.count()).isZero();
        mvc.perform(signup(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        assertThat(users.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("signupTransports")
    void activeAdminWithoutUniversityVerificationCanCreateAnotherAdmin(String path, boolean cookie) throws Exception {
        User admin = saveAdmin();
        assertThat(admin.getUniversityVerifiedAt()).isNull();
        mvc.perform(withToken(signup(path), jwt.generateAccessToken(admin), cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        User created = users.findByEmail(NEW_EMAIL).orElseThrow();
        assertThat(created.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(created.getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(created.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
        assertThat(users.count()).isEqualTo(2);
        mvc.perform(login(NEW_EMAIL, PASSWORD)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("studentRequests")
    void studentsCannotCreateAdminAccounts(String path, boolean verified, boolean cookie) throws Exception {
        User student = User.createOAuthUser("student@example.com", "학생", User.OAuthProvider.KAKAO, "admin-auth-student");
        if (verified) {
            student.verifyUniversityEmail("student@uos.ac.kr");
        }
        student = users.saveAndFlush(student);
        mvc.perform(withToken(signup(path), jwt.generateAccessToken(student), cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        assertThat(users.existsByEmail(NEW_EMAIL)).isFalse();
        assertThat(users.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("revokedAdminRequests")
    void oldAdminJwtCannotBypassCurrentDatabaseRoleOrStatus(
            String path, User.Role role, User.Status accountStatus) throws Exception {
        User admin = saveAdmin();
        String oldToken = jwt.generateAccessToken(admin);
        ReflectionTestUtils.setField(admin, "role", role);
        ReflectionTestUtils.setField(admin, "status", accountStatus);
        users.saveAndFlush(admin);

        mvc.perform(withToken(signup(path), oldToken, false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        assertThat(users.existsByEmail(NEW_EMAIL)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/auth/signup", "/api/admin/auth/sign-up"})
    void jwtForRemovedAdministratorCannotCreateAccounts(String path) throws Exception {
        User admin = saveAdmin();
        String token = jwt.generateAccessToken(admin);
        users.deleteById(admin.getId());
        mvc.perform(withToken(signup(path), token, true))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isZero();
    }

    @Test
    void directServiceCallAlsoRequiresAnActiveAdministrator() throws Exception {
        SignUpRequest request = JSON.readValue(signupBody(), SignUpRequest.class);
        assertThatThrownBy(() -> service.signUp(null, request)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.signUp(Long.MAX_VALUE, request)).isInstanceOf(ForbiddenException.class);
        User student = users.saveAndFlush(User.createOAuthUser(
                "direct@example.com", "학생", User.OAuthProvider.APPLE, "admin-auth-direct"));
        assertThatThrownBy(() -> service.signUp(student.getId(), request)).isInstanceOf(ForbiddenException.class);
        assertThat(users.existsByEmail(NEW_EMAIL)).isFalse();
    }

    @Test
    void activeAdministratorCanLoginAnonymouslyAndReceivesValidTokens() throws Exception {
        User admin = saveAdmin();
        String body = mvc.perform(login(admin.getEmail(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JSON.readTree(body).get("accessToken").asText();
        String refreshToken = JSON.readTree(body).get("refreshToken").asText();
        assertThat(jwt.validateToken(accessToken)).isTrue();
        assertThat(jwt.getTokenType(accessToken)).isEqualTo("access");
        assertThat(jwt.getUserIdAsLong(accessToken)).isEqualTo(admin.getId());
        assertThat(jwt.validateToken(refreshToken)).isTrue();
        assertThat(jwt.getTokenType(refreshToken)).isEqualTo("refresh");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "DELETED"})
    void inactiveAdministratorCannotLoginEvenWithCorrectPassword(String accountStatus) throws Exception {
        User admin = saveAdmin();
        ReflectionTestUtils.setField(admin, "status", User.Status.valueOf(accountStatus));
        users.saveAndFlush(admin);
        assertLoginRejected(admin.getEmail(), PASSWORD);
    }

    @Test
    void studentMissingAccountAndWrongPasswordUseTheSameLoginFailure() throws Exception {
        User admin = saveAdmin();
        User student = User.createAdmin("non-admin@example.com", encoder.encode(PASSWORD), "일반 사용자");
        ReflectionTestUtils.setField(student, "role", User.Role.STUDENT);
        users.saveAndFlush(student);
        assertLoginRejected(student.getEmail(), PASSWORD);
        assertLoginRejected("missing@example.com", PASSWORD);
        assertLoginRejected(admin.getEmail(), "wrong-password");
        ReflectionTestUtils.setField(admin, "passwordHash", null);
        users.saveAndFlush(admin);
        assertLoginRejected(admin.getEmail(), PASSWORD);
    }

    @Test
    void signupValidationAndDuplicateEmailStillRejectWithoutPartialWrites() throws Exception {
        User admin = saveAdmin();
        String token = jwt.generateAccessToken(admin);
        String path = "/api/admin/auth/signup";
        mvc.perform(withToken(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", NEW_EMAIL, "password", PASSWORD,
                                "confirmPassword", "different-password", "nickname", "새 관리자"))), token, false))
                .andExpect(status().isBadRequest());
        mvc.perform(withToken(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"), token, false))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(1);
        mvc.perform(withToken(signup(path), token, false)).andExpect(status().isOk());
        mvc.perform(withToken(signup(path), token, false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        assertThat(users.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/auth/signup", "/api/admin/auth/sign-up"})
    void invalidOrRefreshTokenDoesNotAuthorizeSignup(String path) throws Exception {
        User admin = saveAdmin();
        mvc.perform(withToken(signup(path), "invalid-token", false)).andExpect(status().isUnauthorized());
        mvc.perform(withToken(signup(path), jwt.generateRefreshToken(admin), false))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isEqualTo(1);
    }

    private User saveAdmin() {
        return users.saveAndFlush(User.createAdmin("admin@example.com", encoder.encode(PASSWORD), "기존 관리자"));
    }

    private MockHttpServletRequestBuilder signup(String path) throws Exception {
        return post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(signupBody());
    }

    private String signupBody() throws Exception {
        return JSON.writeValueAsString(Map.of("email", NEW_EMAIL, "password", PASSWORD,
                "confirmPassword", PASSWORD, "nickname", "새 관리자"));
    }

    private MockHttpServletRequestBuilder login(String email, String password) throws Exception {
        return post(LOGIN).servletPath(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password)));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token, boolean cookie) {
        return cookie ? request.cookie(new Cookie("giut_access_token", token))
                : request.header("Authorization", "Bearer " + token);
    }

    private void assertLoginRejected(String email, String password) throws Exception {
        mvc.perform(login(email, password)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    static Stream<Arguments> signupTransports() {
        return Stream.of("/api/admin/auth/signup", "/api/admin/auth/sign-up")
                .flatMap(path -> Stream.of(false, true).map(cookie -> Arguments.of(path, cookie)));
    }

    static Stream<Arguments> studentRequests() {
        return signupTransports().flatMap(arguments -> Stream.of(false, true)
                .map(verified -> Arguments.of(arguments.get()[0], verified, arguments.get()[1])));
    }

    static Stream<Arguments> revokedAdminRequests() {
        return Stream.of("/api/admin/auth/signup", "/api/admin/auth/sign-up").flatMap(path -> Stream.of(
                Arguments.of(path, User.Role.STUDENT, User.Status.ACTIVE),
                Arguments.of(path, User.Role.ADMIN, User.Status.SUSPENDED),
                Arguments.of(path, User.Role.ADMIN, User.Status.DELETED)));
    }
}
