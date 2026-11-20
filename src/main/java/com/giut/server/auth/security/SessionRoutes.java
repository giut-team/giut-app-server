package com.giut.server.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 세션을 시작하거나 끝내는 경로(OAuth 로그인, 관리자 로그인, 로그아웃)의 단일 정의.
 * 폐기·탈퇴된 토큰이 남아 있어도 막지 않고 익명으로 진행해야 하는 경로다.
 * SecurityConfig의 permitAll과 인가 필터들의 예외가 모두 이 매처를 쓴다. 경로를 추가할 때는 여기만 고친다.
 * 두 곳이 어긋나면 폐기된 쿠키가 다시 로그인이나 쿠키 정리를 막는다.
 */
public final class SessionRoutes {

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/oauth/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/admin/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout")
    );

    private SessionRoutes() {
    }
}
