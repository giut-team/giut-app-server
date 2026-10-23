package com.giut.server.global.alert;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlowApiObservationHandlerTest {
    private final SlowApiAlertService alerts = mock(SlowApiAlertService.class);
    private final AtomicLong nanos = new AtomicLong(-10_000_000_000L);
    private final SlowApiObservationHandler handler = new SlowApiObservationHandler(alerts, nanos::get);

    @BeforeEach
    void active() {
        when(alerts.isActive()).thenReturn(true);
    }

    @Test
    void measuresEntireObservationAndOnlyPassesTemplateNotRawSecrets() {
        var request = new MockHttpServletRequest("POST", "/giut/api/profile/shares/SECRET-TOKEN");
        request.setContextPath("/giut");
        request.setQueryString("email=private@example.invalid&token=SECRET");
        request.addHeader("Authorization", "Bearer SECRET");
        var response = new MockHttpServletResponse();
        response.setStatus(201);
        var context = new ServerRequestObservationContext(request, response);
        handler.onStart(context);
        context.setPathPattern("/api/profile/shares/{token}");
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(2345));
        handler.onStop(context);
        handler.onStop(context);

        verify(alerts, times(1)).notifySlowRequest("POST", "/api/profile/shares/{token}", 201,
                TimeUnit.MILLISECONDS.toNanos(2345));
    }

    @Test
    void ignoresNonApiPreflightAndAlertTestPaths() {
        for (String path : new String[]{"/actuator/health", "/v3/api-docs", "/swagger-ui/index.html",
                "/ws/chat", "/apix/secret", "/api/admin/alerts/discord/test"}) {
            var context = context("GET", path);
            handler.onStart(context);
            nanos.addAndGet(TimeUnit.SECONDS.toNanos(10));
            handler.onStop(context);
        }
        var preflight = context("OPTIONS", "/api/profile");
        handler.onStart(preflight);
        handler.onStop(preflight);
        verify(alerts, never()).notifySlowRequest(any(), any(), anyInt(), anyLong());
    }

    @Test
    void disabledAndMissingStartDoNotSend() {
        when(alerts.isActive()).thenReturn(false);
        var context = context("GET", "/api/profile");
        handler.onStart(context);
        handler.onStop(context);
        handler.onStop(context("GET", "/api/profile"));
        verify(alerts, never()).notifySlowRequest(any(), any(), anyInt(), anyLong());
    }

    @Test
    void missingMappingNeverFallsBackToRequestUri() {
        var context = context("GET", "/api/profile/PRIVATE-ID");
        handler.onStart(context);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(3));
        handler.onStop(context);
        verify(alerts).notifySlowRequest("GET", null, 200, TimeUnit.SECONDS.toNanos(3));
    }

    @Test
    void excludesLongLivedSseAndWebSocketUpgrade() {
        var sse = context("GET", "/api/events");
        handler.onStart(sse);
        sse.getResponse().setContentType("text/event-stream;charset=UTF-8");
        nanos.addAndGet(TimeUnit.MINUTES.toNanos(1));
        handler.onStop(sse);
        var ws = context("GET", "/api/socket");
        handler.onStart(ws);
        ws.getResponse().setStatus(101);
        handler.onStop(ws);
        verify(alerts, never()).notifySlowRequest(any(), any(), anyInt(), anyLong());
    }

    @Test
    void asyncTimeoutDoesNotInventFinalHttp200() {
        var request = new MockHttpServletRequest("GET", "/api/async");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        var context = new ServerRequestObservationContext(request, response);
        context.setPathPattern("/api/async");
        handler.onStart(context);
        request.startAsync(request, response);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(3));
        handler.onStop(context);
        verify(alerts).notifySlowRequest("GET", "/api/async", 0, TimeUnit.SECONDS.toNanos(3));
    }

    @Test
    void notificationFailureDoesNotEscapeObservationStop() {
        var context = context("GET", "/api/profile");
        handler.onStart(context);
        doThrow(new IllegalStateException("private-detail")).when(alerts)
                .notifySlowRequest(any(), any(), anyInt(), anyLong());
        assertThatCode(() -> handler.onStop(context)).doesNotThrowAnyException();
    }

    @Test
    void supportsOnlyServletServerObservations() {
        assertThat(handler.supportsContext(new Observation.Context())).isFalse();
        assertThat(handler.supportsContext(context("GET", "/api/profile"))).isTrue();
    }

    private ServerRequestObservationContext context(String method, String path) {
        return new ServerRequestObservationContext(new MockHttpServletRequest(method, path),
                new MockHttpServletResponse());
    }
}
