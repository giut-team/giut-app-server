package com.giut.server.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 로그인 없이 쓸 수 있는 경로(permitAll)의 단일 정의.
 * SecurityConfig의 permitAll과 인가 필터들의 예외가 모두 이 매처를 쓴다. 경로를 추가할 때는 여기만 고친다.
 *
 * <p>이 경로들에서는 폐기·탈퇴된 토큰이 남아 있어도 막지 않고 익명으로 진행한다. 토큰 없이도 되는 요청이라
 * 쓸 수 없는 토큰을 무시해도 권한이 넓어지지 않는다. 두 곳이 어긋나면 폐기된 쿠키가 다시 로그인, 쿠키 정리,
 * 공유 프로필 조회를 막는다.
 */
public final class PublicRoutes {

    /** 세션을 시작하거나 끝내는 경로: OAuth 로그인, 관리자 로그인, 로그아웃. */
    public static final RequestMatcher SESSION = new OrRequestMatcher(
            path(null, "/api/oauth/**"),
            path(HttpMethod.POST, "/api/admin/auth/login"),
            path(HttpMethod.POST, "/api/auth/logout")
    );

    /** permitAll 경로 전체. */
    public static final RequestMatcher ALL = new OrRequestMatcher(
            SESSION,
            path(null, "/"),
            path(null, "/actuator/health"),
            path(null, "/actuator/health/**"),
            path(null, "/swagger-ui"),
            path(null, "/swagger-ui.html"),
            path(null, "/swagger-ui/**"),
            path(null, "/v3/api-docs"),
            path(null, "/v3/api-docs/**"),
            path(HttpMethod.GET, "/api/profile/shares/{token}")
    );

    private PublicRoutes() {
    }

    private static RequestMatcher path(HttpMethod method, String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
    }
}
