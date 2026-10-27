package com.giut.server.profile.controller;

import com.giut.server.auth.security.JwtAccessDeniedHandler;
import com.giut.server.auth.security.JwtAuthenticationEntryPoint;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.global.alert.DiscordAlertService;
import com.giut.server.global.config.SecurityConfig;
import com.giut.server.global.exception.GlobalExceptionHandler;
import com.giut.server.profile.dto.request.UpdateDepartmentRequest;
import com.giut.server.profile.dto.response.DepartmentResponse;
import com.giut.server.profile.entity.UserProfile;
import com.giut.server.profile.service.AdminUserProfileService;
import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserProfileControllerTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtProvider jwtProvider;
    private AdminUserProfileService service;

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
        service = context.getBean(AdminUserProfileService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void anonymousCannotChangeDepartment() throws Exception {
        mockMvc.perform(patch("/api/admin/user-profiles/12/department")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"department\":\"컴퓨터과학부\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void universityVerifiedStudentCannotChangeDepartment() throws Exception {
        String token = token(User.Role.STUDENT);
        when(context.getBean(UserRepository.class).existsByIdAndUniversityVerifiedAtIsNotNull(10L))
                .thenReturn(true);

        mockMvc.perform(patch("/api/admin/user-profiles/12/department")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"department\":\"컴퓨터과학부\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminCanChangeDepartmentUsingKoreanName() throws Exception {
        UpdateDepartmentRequest request = new UpdateDepartmentRequest(UserProfile.DepartmentType.COMPUTER_SCIENCE);
        when(service.updateDepartment(10L, 12L, request))
                .thenReturn(DepartmentResponse.from(request.department()));

        mockMvc.perform(patch("/api/admin/user-profiles/12/department")
                        .header("Authorization", "Bearer " + token(User.Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"department\":\"컴퓨터과학부\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMPUTER_SCIENCE"))
                .andExpect(jsonPath("$.name").value("컴퓨터과학부"));
        verify(service).updateDepartment(10L, 12L, request);
    }

    @Test
    void missingOrUnsupportedDepartmentIsRejectedBeforeServiceCall() throws Exception {
        String token = token(User.Role.ADMIN);
        for (String body : new String[]{"{}", "{\"department\":\"없는학과\"}"}) {
            mockMvc.perform(patch("/api/admin/user-profiles/12/department")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
    }

    private String token(User.Role role) {
        User user = User.createAdmin("test@example.com", "password-hash", "테스트");
        ReflectionTestUtils.setField(user, "id", 10L);
        ReflectionTestUtils.setField(user, "role", role);
        return jwtProvider.generateAccessToken(user);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
            AdminUserProfileController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean
        JwtProvider jwtProvider() { return new JwtProvider(); }

        @Bean
        UserRepository userRepository() {
            UserRepository repository = mock(UserRepository.class);
            when(repository.existsByIdAndStatus(org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.eq(User.Status.ACTIVE))).thenReturn(true);
            return repository;
        }

        @Bean
        AdminUserProfileService adminUserProfileService() { return mock(AdminUserProfileService.class); }

        @Bean
        DiscordAlertService discordAlertService() { return mock(DiscordAlertService.class); }
    }
}
