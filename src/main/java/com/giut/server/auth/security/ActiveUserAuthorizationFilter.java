package com.giut.server.auth.security;

import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** A valid JWT does not grant access to a withdrawn, suspended or missing account. */
@RequiredArgsConstructor
public class ActiveUserAuthorizationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        Long userId = parseUserId(authentication.getName());
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        String token = resolveToken(request);
        boolean tokenVersionMatches = user != null && token != null
                && user.getTokenVersion() == jwtProvider.getTokenVersion(token);
        if (user == null || user.getStatus() != User.Status.ACTIVE || !tokenVersionMatches) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("비활성 계정입니다."));
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if ("giut_access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Long parseUserId(String name) {
        try {
            return Long.valueOf(name);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
