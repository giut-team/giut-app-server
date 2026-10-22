package com.giut.server.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessDeniedHandlerTest {

    @Test
    void returnsJsonForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAccessDeniedHandler().handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"success\":false", "\"code\":403");
    }
}
