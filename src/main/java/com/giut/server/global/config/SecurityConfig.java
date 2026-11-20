package com.giut.server.global.config;

import com.giut.server.auth.security.JwtAuthenticationFilter;
import com.giut.server.auth.security.ActiveUserAuthorizationFilter;
import com.giut.server.auth.security.JwtAuthenticationEntryPoint;
import com.giut.server.auth.security.JwtAccessDeniedHandler;
import com.giut.server.auth.security.JwtProvider;
import com.giut.server.auth.security.PublicRoutes;
import com.giut.server.auth.security.UniversityVerificationAuthorizationFilter;
import com.giut.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import com.giut.server.auth.security.AiIngestionAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private final UserRepository userRepository;

    @Bean
    @Order(1)
    public SecurityFilterChain aiIngestionSecurity(HttpSecurity http,
            @Value("${app.ai-ingestion.secret:}") String secret) throws Exception {
        return http.securityMatcher("/api/integrations/competitions", "/api/integrations/competitions/batch")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(new AiIngestionAuthenticationFilter(secret), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // permitAll 경로는 PublicRoutes에만 정의한다. 인가 필터들도 같은 매처를 쓴다.
                        .requestMatchers(PublicRoutes.ALL).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ActiveUserAuthorizationFilter(userRepository, jwtAccessDeniedHandler, jwtProvider), JwtAuthenticationFilter.class)
                .addFilterAfter(new UniversityVerificationAuthorizationFilter(userRepository), ActiveUserAuthorizationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
