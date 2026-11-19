package com.giut.server.auth.security;

import com.giut.server.auth.service.OAuthCookieService;
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

/**
 * A valid JWT does not grant access to a withdrawn, suspended or missing account.
 * 로그인·OAuth 요청에서는 그런 토큰을 거부하지 않고 무시한다. 다른 기기에서 전체 로그아웃해 폐기된 쿠키가
 * 남아 있어도(HttpOnly라 프런트가 지울 수 없다) 다시 로그인할 수 있어야 하기 때문이다.
 */
@RequiredArgsConstructor
public class ActiveUserAuthorizationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final JwtProvider jwtProvider;
    private final OAuthCookieService oAuthCookieService;

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
        String bearerToken = resolveBearerToken(request);
        String token = bearerToken != null ? bearerToken : resolveCookieToken(request);
        // 로그아웃 시 User.tokenVersion이 올라가므로, 그 이전에 발급된 토큰은 값이 달라 거부된다.
        boolean tokenVersionMatches = user != null && token != null
                && user.getTokenVersion() == jwtProvider.getTokenVersion(token);
        if (user == null || user.getStatus() != User.Status.ACTIVE || !tokenVersionMatches) {
            if (LoginRoutes.MATCHER.matches(request)) {
                // 로그인 경로는 SecurityConfig에서 같은 매처로 permitAll이라 인증 없이 진행해도 된다.
                // 성공하면 새 쿠키가 덮어쓴다.
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            if (bearerToken == null && token != null) {
                // 쿠키로 온 토큰만 지운다. 쓸 수 없는 쿠키를 계속 보내 403만 받는 상태를 끝낸다.
                oAuthCookieService.clearLoginCookies(response);
            }
            accessDeniedHandler.handle(request, response, new AccessDeniedException("비활성 계정입니다."));
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String resolveCookieToken(HttpServletRequest request) {
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
