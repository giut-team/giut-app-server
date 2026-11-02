package com.giut.server.auth.security;

import com.giut.server.user.entity.User;
import com.giut.server.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        var cookieRequest = new MockHttpServletRequest("GET", "/api/profile/shares/public-token");
        cookieRequest.setCookies(new jakarta.servlet.http.Cookie("giut_access_token", TOKEN));
        assertThat(invoke(cookieRequest).continued()).isTrue();

        var noTokenRequest = new MockHttpServletRequest("GET", "/api/profile/shares/public-token");
        assertThat(invoke(noTokenRequest).response().getStatus()).isEqualTo(403);
    }

    @Test
    void adminRoleAndPublicRouteDoNotBypassAccountStatus() throws Exception {
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

    private User activeUser() {
        return User.createOAuthUser("student@example.com", "student", User.OAuthProvider.KAKAO, "kakao-42");
    }

    private void authenticate(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                id, "", List.of(new SimpleGrantedAuthority(role))));
    }

    private Result invoke() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/profile/shares/public-token");
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
