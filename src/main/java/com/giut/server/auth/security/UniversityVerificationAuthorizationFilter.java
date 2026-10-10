package com.giut.server.auth.security;

import com.giut.server.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Limits authenticated, unverified non-admin users to contest browsing and university verification. */
@RequiredArgsConstructor
public class UniversityVerificationAuthorizationFilter extends OncePerRequestFilter {

    private static final String UNIVERSITY_EMAIL_PATH = "/api/members/university-email";
    private static final String UNIVERSITY_VERIFICATION_REQUIRED =
            "학교 이메일 인증 후 이용할 수 있습니다.";

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!requiresUniversityVerification(request, authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = parseUserId(authentication.getName());
        if (userId != null && userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + UNIVERSITY_VERIFICATION_REQUIRED + "\",\"code\":403}"
        );
    }

    private boolean requiresUniversityVerification(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        if (isAdmin(authentication)) {
            return false;
        }
        String path = request.getServletPath();
        String method = request.getMethod();

        if (!path.startsWith("/api/") || HttpMethod.OPTIONS.matches(method) || path.startsWith("/api/oauth/")) {
            return false;
        }
        if (HttpMethod.GET.matches(method) && isPublicCompetitionRead(path)) {
            return false;
        }
        if (HttpMethod.POST.matches(method)
                && ((UNIVERSITY_EMAIL_PATH + "/send").equals(path)
                || (UNIVERSITY_EMAIL_PATH + "/verify").equals(path))) {
            return false;
        }
        return !(HttpMethod.GET.matches(method) && (UNIVERSITY_EMAIL_PATH + "/status").equals(path));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private boolean isPublicCompetitionRead(String path) {
        return "/api/competitions".equals(path)
                || "/api/competitions/top5".equals(path)
                || "/api/competitions/closing-soon".equals(path)
                || path.matches("/api/competitions/\\d+");
    }

    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
