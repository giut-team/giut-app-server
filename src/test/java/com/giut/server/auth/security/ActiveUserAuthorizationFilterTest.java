package com.giut.server.auth.security;

import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveUserAuthorizationFilterTest {
    private static final String TOKEN = "access-token";

    @Mock UserRepository users;
    @Mock JwtProvider jwtProvider;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void anonymousRequestsDoNotQueryAccounts() throws Exception {
        assertThat(invoke().continued()).isTrue();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(invoke().continued()).isTrue();
        verifyNoInteractions(users, jwtProvider);
    }

    @Test
    void activeAccountContinuesAndInactiveOrMissingAccountIsForbidden() throws Exception {
        authenticate("42", "ROLE_STUDENT");
        User user = activeUser();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);
        assertThat(invoke().continued()).isTrue();

        user.withdraw();
        var denied = invoke();
        assertThat(denied.continued()).isFalse();
        assertThat(denied.response().getStatus()).isEqualTo(403);
        assertThat(denied.response().getContentAsString()).contains("\"code\":403");

        when(users.findById(42L)).thenReturn(Optional.empty());
        assertThat(invoke().response().getStatus()).isEqualTo(403);
    }

    @Test
    void tokenVersionMismatchIsForbidden() throws Exception {
        authenticate("42", "ROLE_STUDENT");
        User user = activeUser();
        user.bumpTokenVersion();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);
        var denied = invoke();
        assertThat(denied.continued()).isFalse();
        assertThat(denied.response().getStatus()).isEqualTo(403);
    }

    @Test
    void cookieTokenIsCheckedAndMissingTokenIsForbidden() throws Exception {
        authenticate("42", "ROLE_STUDENT");
        when(users.findById(42L)).thenReturn(Optional.of(activeUser()));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);
        assertThat(invoke(cookieRequest("GET", "/api/profile")).continued()).isTrue();

        var noTokenRequest = new MockHttpServletRequest("GET", "/api/profile");
        noTokenRequest.setServletPath("/api/profile");
        assertThat(invoke(noTokenRequest).response().getStatus()).isEqualTo(403);
    }

    @Test
    void adminRoleDoesNotBypassAccountStatus() throws Exception {
        authenticate("42", "ROLE_ADMIN");
        assertThat(invoke().continued()).isFalse();
        verify(users).findById(42L);
    }

    @Test
    void malformedPrincipalIsDeniedWithoutQuery() throws Exception {
        authenticate("not-an-id", "ROLE_STUDENT");
        assertThat(invoke().response().getStatus()).isEqualTo(403);
        verifyNoInteractions(users, jwtProvider);
    }

    @Test
    void revokedTokenDoesNotBlockPublicRoutesAndContinuesAnonymously() throws Exception {
        User user = activeUser();
        user.bumpTokenVersion();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);

        for (var route : List.of(
                new String[]{"GET", "/api/oauth/kakao/authorization"},
                new String[]{"GET", "/api/oauth/kakao/callback"},
                new String[]{"GET", "/api/oauth/apple/authorization"},
                new String[]{"POST", "/api/oauth/apple/callback"},
                new String[]{"POST", "/api/admin/auth/login"},
                new String[]{"GET", "/actuator/health"},
                new String[]{"GET", "/"})) {
            authenticate("42", "ROLE_STUDENT");
            var result = invoke(cookieRequest(route[0], route[1]));
            assertThat(result.continued()).as(route[1]).isTrue();
            assertThat(result.response().getStatus()).as(route[1]).isEqualTo(200);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).as(route[1]).isNull();
        }
    }

    @Test
    void withdrawnOrMissingAccountTokenDoesNotBlockLogin() throws Exception {
        // 로그인은 남은 토큰과 관계없이 항상 진행된다.
        User user = activeUser();
        user.withdraw();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);
        authenticate("42", "ROLE_STUDENT");
        assertThat(invoke(cookieRequest("GET", "/api/oauth/kakao/callback")).continued()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        when(users.findById(42L)).thenReturn(Optional.empty());
        authenticate("42", "ROLE_ADMIN");
        assertThat(invoke(cookieRequest("POST", "/api/admin/auth/login")).continued()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logoutAndSharedProfileRequireAValidLogin() throws Exception {
        // 로그아웃과 공유 카드는 로그인이 필요한 API다. 쓸 수 없는 토큰이면 다른 보호 API처럼 403이다.
        User user = activeUser();
        user.withdraw();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);
        for (var route : List.of(
                new String[]{"POST", "/api/auth/logout"},
                new String[]{"GET", "/api/profile/shares/public-token"})) {
            authenticate("42", "ROLE_STUDENT");
            var result = invoke(cookieRequest(route[0], route[1]));
            assertThat(result.continued()).as(route[1]).isFalse();
            assertThat(result.response().getStatus()).as(route[1]).isEqualTo(403);
            assertThat(result.response().getHeaders(HttpHeaders.SET_COOKIE)).as(route[1]).isEmpty();
        }
    }

    @Test
    void loginRouteExceptionIsExact() throws Exception {
        User user = activeUser();
        user.bumpTokenVersion();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);

        for (var route : List.of(
                new String[]{"GET", "/api/admin/auth/login"},
                new String[]{"POST", "/api/admin/auth/login/extra"},
                new String[]{"POST", "/api/admin/auth/signup"},
                new String[]{"GET", "/api/oauthx/kakao"},
                new String[]{"GET", "/api/auth/logout"},
                new String[]{"POST", "/api/auth/logout/extra"},
                new String[]{"GET", "/api/profile/shares/public-token/target"},
                new String[]{"POST", "/api/profile/shares/public-token"},
                new String[]{"GET", "/api/profile/shares"})) {
            authenticate("42", "ROLE_STUDENT");
            var result = invoke(cookieRequest(route[0], route[1]));
            assertThat(result.continued()).as(route[1]).isFalse();
            assertThat(result.response().getStatus()).as(route[1]).isEqualTo(403);
        }
    }

    @Test
    void revokedTokenOnProtectedRouteIsForbiddenWithoutClearingCookies() throws Exception {
        // 늦게 도착한 403이 방금 로그인해 받은 같은 이름의 쿠키를 지우지 않도록, 403 응답에는 Set-Cookie가 없어야 한다.
        User user = activeUser();
        user.bumpTokenVersion();
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwtProvider.getTokenVersion(TOKEN)).thenReturn(0L);
        var bearerRequest = new MockHttpServletRequest("GET", "/api/profile");
        bearerRequest.setServletPath("/api/profile");
        bearerRequest.addHeader("Authorization", "Bearer " + TOKEN);

        for (var request : List.of(cookieRequest("GET", "/api/profile"), bearerRequest)) {
            authenticate("42", "ROLE_STUDENT");
            var result = invoke(request);
            assertThat(result.continued()).isFalse();
            assertThat(result.response().getStatus()).isEqualTo(403);
            assertThat(result.response().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        }
    }

    private MockHttpServletRequest cookieRequest(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setCookies(new jakarta.servlet.http.Cookie("giut_access_token", TOKEN));
        return request;
    }

    private User activeUser() {
        return User.createOAuthUser("student@example.com", "student", User.OAuthProvider.KAKAO, "kakao-42");
    }

    private void authenticate(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                id, "", List.of(new SimpleGrantedAuthority(role))));
    }

    private Result invoke() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/profile");
        request.setServletPath("/api/profile");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return invoke(request);
    }

    private Result invoke(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        new ActiveUserAuthorizationFilter(users, new JwtAccessDeniedHandler(), jwtProvider)
                .doFilter(request, response, (req, res) -> continued.set(true));
        return new Result(continued.get(), response);
    }
    private record Result(boolean continued, MockHttpServletResponse response) {}
}
