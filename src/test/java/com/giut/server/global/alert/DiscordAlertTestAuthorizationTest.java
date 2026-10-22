package com.giut.server.global.alert;

import com.giut.server.auth.security.JwtAccessDeniedHandler;
import com.giut.server.auth.security.JwtAuthenticationEntryPoint;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.config.SecurityConfig;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Base64;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiscordAlertTestAuthorizationTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private DiscordAlertService discordAlertService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testSecurity", Map.of(
                "jwt.secret", Base64.getEncoder().encodeToString(new byte[32]),
                "jwt.expiration-time", "3600000",
                "jwt.refresh-expiration-time", "1209600000",
                "APP_ENVIRONMENT", "dev"
        )));
        context.register(TestConfig.class);
        context.refresh();
        jwtProvider = context.getBean(JwtProvider.class);
        discordAlertService = context.getBean(DiscordAlertService.class);
        userRepository = context.getBean(UserRepository.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void anonymousRequestCannotSendTestAlert() throws Exception {
        mockMvc.perform(post("/api/admin/alerts/discord/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(discordAlertService);
    }

    @Test
    void ordinaryUserCannotSendTestAlertEvenIfUniversityVerified() throws Exception {
        User student = user(User.Role.STUDENT);
        student.verifyUniversityEmail("student@uos.ac.kr");
        when(userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(10L)).thenReturn(true);
        String token = jwtProvider.generateAccessToken(student);

        mockMvc.perform(post("/api/admin/alerts/discord/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(discordAlertService);
    }

    @Test
    void unverifiedAdminCanSendTestAlert() throws Exception {
        String token = jwtProvider.generateAccessToken(user(User.Role.ADMIN));
        when(discordAlertService.sendTestAlert()).thenReturn(DiscordAlertService.TestAlertResult.SENT);

        mockMvc.perform(post("/api/admin/alerts/discord/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(discordAlertService).sendTestAlert();
    }

    private User user(User.Role role) {
        User user = role == User.Role.ADMIN
                ? User.createAdmin("admin@example.com", "password-hash", "관리자")
                : User.createOAuthUser("student@example.com", "학생", User.OAuthProvider.KAKAO, "provider-id");
        ReflectionTestUtils.setField(user, "id", 10L);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            DiscordAlertTestController.class
    })
    static class TestConfig {

        @Bean
        JwtProvider jwtProvider() {
            return new JwtProvider();
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        DiscordAlertService discordAlertService() {
            return mock(DiscordAlertService.class);
        }
    }
}
