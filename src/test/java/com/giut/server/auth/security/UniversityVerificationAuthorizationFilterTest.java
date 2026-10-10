package com.giut.server.auth.security;

import com.giut.server.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniversityVerificationAuthorizationFilterTest {

    @Mock
    private UserRepository userRepository;

    private UniversityVerificationAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UniversityVerificationAuthorizationFilter(userRepository);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unverifiedUserCanReadCompetitionsWithoutDatabaseCheck() throws Exception {
        authenticateAsStudent("42");

        var listResult = invoke("GET", "/api/competitions");
        var top5Result = invoke("GET", "/api/competitions/top5");
        var closingSoonResult = invoke("GET", "/api/competitions/closing-soon");
        var detailResult = invoke("GET", "/api/competitions/12");

        assertThat(listResult.continued()).isTrue();
        assertThat(top5Result.continued()).isTrue();
        assertThat(closingSoonResult.continued()).isTrue();
        assertThat(detailResult.continued()).isTrue();
        verifyNoInteractions(userRepository);
    }

    @Test
    void unverifiedUserCannotReadNonexistentCompetitionSubroutes() throws Exception {
        authenticateAsStudent("42");
        when(userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(42L)).thenReturn(false);

        assertDenied(invoke("GET", "/api/competitions/internal-metrics"));
    }

    @Test
    void unverifiedUserCannotReadProfilesOrModifyContestScraps() throws Exception {
        authenticateAsStudent("42");
        when(userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(42L)).thenReturn(false);

        var profileResult = invoke("GET", "/api/profile");
        var scrapResult = invoke("POST", "/api/competitions/12/scrap");
        var sharedProfileResult = invoke("GET", "/api/profile/shares/share-token");
        var shareAnotherProfileResult = invoke("POST", "/api/users/13/profile-share-links");

        assertDenied(profileResult);
        assertDenied(scrapResult);
        assertDenied(sharedProfileResult);
        assertDenied(shareAnotherProfileResult);
        verify(userRepository, times(4)).existsByIdAndUniversityVerifiedAtIsNotNull(42L);
    }

    @Test
    void unverifiedUserCanSendAndVerifyEmailAndReadOnlyVerificationStatus() throws Exception {
        authenticateAsStudent("42");

        assertThat(invoke("POST", "/api/members/university-email/send").continued()).isTrue();
        assertThat(invoke("POST", "/api/members/university-email/verify").continued()).isTrue();
        assertThat(invoke("GET", "/api/members/university-email/status").continued()).isTrue();
        verifyNoInteractions(userRepository);
    }

    @Test
    void verifiedUserCanAccessOtherApis() throws Exception {
        authenticateAsStudent("42");
        when(userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(42L)).thenReturn(true);

        var result = invoke("GET", "/api/profile");

        assertThat(result.continued()).isTrue();
    }

    @Test
    void adminRoleBypassesUniversityVerificationForAnyApi() throws Exception {
        authenticateAsAdmin("1");

        assertThat(invoke("GET", "/api/profile").continued()).isTrue();
        assertThat(invoke("POST", "/api/admin/alerts/discord/test").continued()).isTrue();
        verifyNoInteractions(userRepository);

        SecurityContextHolder.clearContext();
        assertThat(invoke("GET", "/api/profile").continued()).isTrue();

        authenticateAsStudent("42");
        assertThat(invoke("GET", "/swagger-ui/index.html").continued()).isTrue();
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void unverifiedStudentStillRequiresSchoolVerificationOnOtherMethodsAndPaths() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("42", "",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );
        when(userRepository.existsByIdAndUniversityVerifiedAtIsNotNull(42L)).thenReturn(false);

        assertDenied(invoke("GET", "/api/admin/alerts/discord/test"));
        assertDenied(invoke("POST", "/api/admin/alerts/discord/test/extra"));
        verify(userRepository, times(2)).existsByIdAndUniversityVerifiedAtIsNotNull(42L);
    }

    private void authenticateAsAdmin(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        userId, "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    private void authenticateAsStudent(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        userId, "", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                )
        );
    }

    private FilterResult invoke(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> continued.set(true);

        filter.doFilter(request, response, chain);
        return new FilterResult(continued.get(), response);
    }

    private void assertDenied(FilterResult result) throws Exception {
        assertThat(result.continued()).isFalse();
        assertThat(result.response().getStatus()).isEqualTo(403);
        assertThat(result.response().getContentAsString())
                .contains("학교 이메일 인증 후 이용할 수 있습니다.", "\"code\":403");
    }

    private record FilterResult(boolean continued, MockHttpServletResponse response) {
    }
}
