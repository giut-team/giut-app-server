package com.giut.server.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * Expo 푸시 발송 작업자 설정. 기본값은 단일 서버·소규모 발송을 가정한 보수적인 값이며 부하를 측정한 값이 아니다.
 *
 * @param workerEnabled      false면 이 서버는 알림·발송 대기만 저장하고 실제 발송·영수증 확인을 하지 않는다.
 * @param expoSendUrl        Expo 발송 API.
 * @param expoReceiptsUrl    Expo 영수증 조회 API.
 * @param expoAccessToken    Expo 대시보드에서 "Enhanced security for push"를 켰을 때만 필요한 access token.
 * @param androidChannelId   Android 알림 채널. 비우면 보내지 않는다. 기기에 없는 채널을 지정하면 알림이 표시되지 않는다.
 * @param pollInterval       발송 대기 확인 주기.
 * @param batchSize          한 번에 선점·발송할 수(Expo 요청 1건의 상한 100).
 * @param requestTimeout     Expo 요청 한 번의 제한 시간.
 * @param leaseDuration      SENDING 임대 시간. requestTimeout + 저장 여유보다 길어야 한다.
 * @param maxAttempts        발송 시도 상한(일시 오류 재시도·임대 만료 회수 포함).
 * @param retryBaseDelay     일시 오류 재시도 첫 대기(이후 2배씩).
 * @param retryMaxDelay      재시도 대기 상한.
 * @param maxPendingAge      이 시간이 지나도록 못 보낸 푸시는 늦은 알림이 되므로 보내지 않고 실패로 끝낸다(알림함에는 남는다).
 * @param receiptDelay       발송 후 영수증을 확인할 때까지 기다리는 시간(Expo 권장 약 15분).
 * @param receiptRetention   Expo가 영수증을 보관하는 기간. 지나도록 영수증이 없으면 UNCONFIRMED로 끝낸다.
 * @param receiptBatchSize   영수증 조회 1회 id 수(Expo 상한 1000).
 * @param deliveryRetention  끝난 발송 기록 보관 기간.
 * @param deviceStaleAfter   이 기간 동안 앱이 토큰을 다시 등록하지 않은 기기는 비활성화한다(FCM은 270일 미사용 토큰을 만료로 본다).
 * @param maintenanceBatchSize 회수·정리 1회 처리 행 수 상한.
 */
@ConfigurationProperties("app.push")
public record PushProperties(
        @DefaultValue("true") boolean workerEnabled,
        @DefaultValue("https://exp.host/--/api/v2/push/send") URI expoSendUrl,
        @DefaultValue("https://exp.host/--/api/v2/push/getReceipts") URI expoReceiptsUrl,
        @DefaultValue("") String expoAccessToken,
        @DefaultValue("") String androidChannelId,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10s") Duration requestTimeout,
        @DefaultValue("60s") Duration leaseDuration,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration retryBaseDelay,
        @DefaultValue("10m") Duration retryMaxDelay,
        @DefaultValue("6h") Duration maxPendingAge,
        @DefaultValue("15m") Duration receiptDelay,
        @DefaultValue("24h") Duration receiptRetention,
        @DefaultValue("300") int receiptBatchSize,
        @DefaultValue("30d") Duration deliveryRetention,
        @DefaultValue("270d") Duration deviceStaleAfter,
        @DefaultValue("100") int maintenanceBatchSize
) {
    public static final int EXPO_MAX_MESSAGES_PER_REQUEST = 100;
    public static final int EXPO_MAX_RECEIPT_IDS_PER_REQUEST = 1000;

    public PushProperties {
        require(expoSendUrl != null && expoReceiptsUrl != null, "expo urls are required");
        require(batchSize >= 1 && batchSize <= EXPO_MAX_MESSAGES_PER_REQUEST, "batch-size must be 1..100");
        require(positive(pollInterval) && positive(requestTimeout), "poll-interval and request-timeout must be positive");
        require(leaseDuration != null && leaseDuration.compareTo(requestTimeout.plusSeconds(30)) >= 0,
                "lease-duration must be at least request-timeout + 30s");
        require(maxAttempts >= 1 && maxAttempts <= 10, "max-attempts must be 1..10");
        require(positive(retryBaseDelay) && positive(retryMaxDelay) && retryMaxDelay.compareTo(retryBaseDelay) >= 0,
                "retry delays must be positive and max >= base");
        require(maxPendingAge != null && maxPendingAge.compareTo(leaseDuration) > 0, "max-pending-age must exceed lease-duration");
        require(positive(receiptDelay) && receiptRetention != null && receiptRetention.compareTo(receiptDelay) > 0,
                "receipt-retention must exceed receipt-delay");
        require(receiptBatchSize >= 1 && receiptBatchSize <= EXPO_MAX_RECEIPT_IDS_PER_REQUEST, "receipt-batch-size must be 1..1000");
        require(positive(deliveryRetention) && positive(deviceStaleAfter), "retention values must be positive");
        require(maintenanceBatchSize >= 1 && maintenanceBatchSize <= 1000, "maintenance-batch-size must be 1..1000");
        expoAccessToken = expoAccessToken == null ? "" : expoAccessToken.trim();
        androidChannelId = androidChannelId == null ? "" : androidChannelId.trim();
    }

    /** n번째 시도(1부터)가 일시 실패했을 때 다음 시도까지의 대기: base * 2^(n-1), 상한 retryMaxDelay. */
    public Duration retryDelay(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        Duration delay = retryBaseDelay.multipliedBy(1L << exponent);
        return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("app.push: " + message);
        }
    }
}
