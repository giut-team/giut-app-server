package com.giut.server.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AiIngestionAuthenticationFilterTest {
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void emptyOrShortConfigurationDoesNotAuthenticate() throws Exception {
        for (String secret : new String[] {"", "short", "                                "}) {
            SecurityContextHolder.clearContext();
            var request = new MockHttpServletRequest();
            request.addHeader("X-AI-API-Key", secret);
            new AiIngestionAuthenticationFilter(secret).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
