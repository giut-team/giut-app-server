package com.giut.server.global.alert;

import java.util.Locale;
import java.util.function.LongSupplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * Reuses Actuator's HTTP server observation, including its async completion/timeout lifecycle.
 * This measures servlet processing, not end-to-end browser or reverse-proxy latency.
 */
@Component
public class SlowApiObservationHandler implements ObservationHandler<ServerRequestObservationContext> {
    private static final Logger logger = LoggerFactory.getLogger(SlowApiObservationHandler.class);
    private static final String SAMPLE_KEY = SlowApiObservationHandler.class.getName() + ".sample";
    private final SlowApiAlertService alerts;
    private final LongSupplier nanoTime;

    @Autowired
    public SlowApiObservationHandler(SlowApiAlertService alerts) {
        this(alerts, System::nanoTime);
    }

    SlowApiObservationHandler(SlowApiAlertService alerts, LongSupplier nanoTime) {
        this.alerts = alerts;
        this.nanoTime = nanoTime;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        // Client observations made by the webhook sender must never trigger this handler.
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        try {
            HttpServletRequest request = context.getCarrier();
            if (!alerts.isActive() || request == null || "OPTIONS".equals(request.getMethod())) {
                return;
            }
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!(path.equals("/api") || path.startsWith("/api/"))
                    || path.equals("/api/admin/alerts/discord")
                    || path.startsWith("/api/admin/alerts/discord/")) {
                return;
            }
            context.put(SAMPLE_KEY, new Sample(nanoTime.getAsLong(), request.getMethod()));
        } catch (RuntimeException error) {
            logger.warn("느린 API 알림 시작 관측 실패; 원래 요청은 유지합니다.");
        }
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        Object value = context.remove(SAMPLE_KEY);
        if (!(value instanceof Sample sample)) {
            return;
        }
        try {
            HttpServletResponse response = context.getResponse();
            if (response == null || response.getStatus() == 101) {
                return;
            }
            String contentType = response.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/event-stream")) {
                return; // Long-lived streams are not ordinary API latency.
            }
            HttpServletRequest request = context.getCarrier();
            // Spring stops observations on async timeout before the final error dispatch.
            int status = request != null && request.isAsyncStarted() && !response.isCommitted()
                    ? 0 : response.getStatus();
            alerts.notifySlowRequest(sample.method(), context.getPathPattern(), status,
                    nanoTime.getAsLong() - sample.startedNanos());
        } catch (RuntimeException error) {
            // Monitoring failure must not replace the API's response or exception.
            logger.warn("느린 API 알림 관측 처리 실패; 원래 응답은 유지합니다.");
        }
    }

    private record Sample(long startedNanos, String method) {}
}
