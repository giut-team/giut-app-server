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
        if (secret.length >= 32 && key != null && MessageDigest.isEqual(secret, key.getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "ai-ingestion", null, List.of(new SimpleGrantedAuthority("ROLE_AI_INGESTION")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
