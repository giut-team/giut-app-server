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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveUserAuthorizationFilterTest {
    @Mock UserRepository users;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void anonymousRequestsDoNotQueryAccounts() throws Exception {
        assertThat(invoke().continued()).isTrue();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(invoke().continued()).isTrue();
        verifyNoInteractions(users);
    }

    @Test
    void activeAccountContinuesAndInactiveOrMissingAccountIsForbidden() throws Exception {
        authenticate("42", "ROLE_STUDENT");
        when(users.existsByIdAndStatus(42L, User.Status.ACTIVE)).thenReturn(true);
        assertThat(invoke().continued()).isTrue();
        when(users.existsByIdAndStatus(42L, User.Status.ACTIVE)).thenReturn(false);
        var denied = invoke();
        assertThat(denied.continued()).isFalse();
        assertThat(denied.response().getStatus()).isEqualTo(403);
        assertThat(denied.response().getContentAsString()).contains("\"code\":403");
    }

    @Test
    void adminRoleAndPublicRouteDoNotBypassAccountStatus() throws Exception {
        authenticate("42", "ROLE_ADMIN");
        assertThat(invoke().continued()).isFalse();
        verify(users).existsByIdAndStatus(42L, User.Status.ACTIVE);
    }

    @Test
    void malformedPrincipalIsDeniedWithoutQuery() throws Exception {
        authenticate("not-an-id", "ROLE_STUDENT");
        assertThat(invoke().response().getStatus()).isEqualTo(403);
        verifyNoInteractions(users);
    }

    private void authenticate(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                id, "", List.of(new SimpleGrantedAuthority(role))));
    }

    private Result invoke() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/profile/shares/public-token");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        new ActiveUserAuthorizationFilter(users, new JwtAccessDeniedHandler())
                .doFilter(request, response, (req, res) -> continued.set(true));
        return new Result(continued.get(), response);
    }
    private record Result(boolean continued, MockHttpServletResponse response) {}
}
