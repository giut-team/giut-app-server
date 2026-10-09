package com.giut.server.competition.controller;

import com.giut.server.auth.security.JwtAccessDeniedHandler;
import com.giut.server.auth.security.JwtAuthenticationEntryPoint;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.config.SecurityConfig;
import com.giut.server.competition.dto.response.SeedSampleCompetitionsResponse;
import com.giut.server.competition.service.CompetitionSeedService;
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

class AdminCompetitionSeedControllerTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private CompetitionSeedService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testJwt", Map.of(
                "jwt.secret", Base64.getEncoder().encodeToString(new byte[32]),
                "jwt.expiration-time", "3600000",
                "jwt.refresh-expiration-time", "1209600000"
        )));
        context.register(TestConfig.class);
        context.refresh();
        jwtProvider = context.getBean(JwtProvider.class);
        service = context.getBean(CompetitionSeedService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void anonymousRequestIsUnauthorizedAndDoesNotSeed() throws Exception {
        mockMvc.perform(post("/api/admin/competitions/samples"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(service);
    }

    @Test
    void studentAccessTokenIsForbiddenAndDoesNotSeed() throws Exception {
        String token = jwtProvider.generateAccessToken(user(User.Role.STUDENT));

        mockMvc.perform(post("/api/admin/competitions/samples")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(service);
    }

    @Test
    void adminAccessTokenCanSeedWithoutARequestBody() throws Exception {
        when(context.getBean(UserRepository.class).existsByIdAndUniversityVerifiedAtIsNotNull(10L)).thenReturn(true);
        String token = jwtProvider.generateAccessToken(user(User.Role.ADMIN));
        when(service.seedSamples(10L)).thenReturn(new SeedSampleCompetitionsResponse(10, 0, java.util.List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)));

        mockMvc.perform(post("/api/admin/competitions/samples")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(10))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.competitionIds.length()").value(10));

        verify(service).seedSamples(10L);
    }

    @Test
    void adminRefreshTokenCannotSeed() throws Exception {
        String token = jwtProvider.generateRefreshToken(user(User.Role.ADMIN));

        mockMvc.perform(post("/api/admin/competitions/samples")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private User user(User.Role role) {
        User user = User.createAdmin("admin@example.com", "password-hash", "관리자");
        ReflectionTestUtils.setField(user, "id", 10L);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            AdminCompetitionSeedController.class
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
        CompetitionSeedService defaultProfileRoleService() {
            return mock(CompetitionSeedService.class);
        }
    }
}
