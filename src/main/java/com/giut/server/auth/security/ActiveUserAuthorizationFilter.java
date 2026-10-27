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
        if (userId == null || !userRepository.existsByIdAndStatus(userId, User.Status.ACTIVE)) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("비활성 계정입니다."));
            return;
        }
        chain.doFilter(request, response);
    }

    private Long parseUserId(String name) {
        try {
            return Long.valueOf(name);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
