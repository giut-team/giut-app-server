package com.giut.server.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 로그인 없이 쓸 수 있는 경로(permitAll)의 단일 정의. 그 밖의 모든 API는 로그인이 필요하다.
 * SecurityConfig의 permitAll과 인가 필터들의 예외가 모두 이 매처를 쓴다. 경로를 추가할 때는 여기만 고친다.
 *
 * <p>로그인 경로와 인프라 경로뿐이다. 이 경로들에서는 폐기·탈퇴된 토큰이 남아 있어도 막지 않고 익명으로 진행한다.
 * 로그인은 남은 토큰과 관계없이 항상 진행돼야 하기 때문이다.
 */
public final class PublicRoutes {

    /** 로그인 경로: OAuth 로그인, 관리자 로그인. */
    public static final RequestMatcher LOGIN = new OrRequestMatcher(
            path(null, "/api/oauth/**"),
            path(HttpMethod.POST, "/api/admin/auth/login")
    );

    /** permitAll 경로 전체: 로그인 경로와 인프라 경로(루트, health, swagger, api-docs). */
    public static final RequestMatcher ALL = new OrRequestMatcher(
            LOGIN,
            path(null, "/"),
            path(null, "/actuator/health"),
            path(null, "/actuator/health/**"),
            path(null, "/swagger-ui"),
            path(null, "/swagger-ui.html"),
            path(null, "/swagger-ui/**"),
            path(null, "/v3/api-docs"),
            path(null, "/v3/api-docs/**")
    );

    private PublicRoutes() {
    }

    private static RequestMatcher path(HttpMethod method, String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
    }
}
