package com.giut.server.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 로그인 경로(OAuth 로그인, 관리자 로그인)의 단일 정의.
 * SecurityConfig의 permitAll과 인가 필터들의 로그인 경로 예외가 모두 이 매처를 쓴다.
 * 로그인 경로를 추가할 때는 여기만 고친다. 두 곳이 어긋나면 폐기된 쿠키가 다시 로그인을 막는다.
 */
public final class LoginRoutes {

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/oauth/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/admin/auth/login")
    );

    private LoginRoutes() {
    }
}
