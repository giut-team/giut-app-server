package com.giut.server.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Authenticates only the dedicated AI ingestion chain; missing configuration fails closed. */
public class AiIngestionAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] secret;

    public AiIngestionAuthenticationFilter(String secret) {
        this.secret = secret == null || secret.isBlank() ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("X-AI-API-Key");
        // 32바이트 미만(미설정·빈 값 포함)이면 비밀값이 사실상 없는 것으로 보고 항상 거부한다.
        // MessageDigest.isEqual은 상수 시간 비교라 타이밍 공격으로 값을 추측하기 어렵다.
        if (secret.length >= 32 && key != null && MessageDigest.isEqual(secret, key.getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "ai-ingestion", null, List.of(new SimpleGrantedAuthority("ROLE_AI_INGESTION")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
