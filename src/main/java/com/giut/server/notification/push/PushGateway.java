package com.giut.server.notification.push;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 외부 푸시 서비스 경계. 발송 작업자는 이 인터페이스만 알고, Expo 대신 FCM 직접 발송으로 바꿔도 작업자·DB는 그대로다.
 * 구현은 DB 트랜잭션 밖에서만 호출된다.
 */
public interface PushGateway {

    /**
     * 메시지들을 한 요청으로 보낸다.
     *
     * @return 입력과 같은 순서·같은 개수의 결과
     * @throws PushGatewayException 요청 전체가 실패했을 때(네트워크·429·5xx는 retryable)
     */
    List<PushTicket> send(List<PushMessage> messages);

    /**
     * 발송 결과(영수증)를 조회한다. 아직 영수증이 없는 id는 결과 Map에 없다.
     *
     * @throws PushGatewayException 요청 전체가 실패했을 때
     */
    Map<String, PushReceipt> receipts(Collection<String> ticketIds);

    record PushMessage(String to, String title, String body, Map<String, Object> data, Integer badge) {
    }

    /** ok면 id(영수증 id)가 있고, 아니면 error(Expo details.error, 예: DeviceNotRegistered)와 message가 있다. */
    record PushTicket(boolean ok, String id, String error, String message) {
        public static PushTicket ok(String id) {
            return new PushTicket(true, id, null, null);
        }

        public static PushTicket error(String error, String message) {
            return new PushTicket(false, null, error, message);
        }
    }

    record PushReceipt(boolean ok, String error, String message) {
        public static PushReceipt accepted() {
            return new PushReceipt(true, null, null);
        }

        public static PushReceipt error(String error, String message) {
            return new PushReceipt(false, error, message);
        }
    }

    /** Expo가 돌려주는 오류 코드 중 작업자가 구분해서 처리하는 값. */
    final class Errors {
        public static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";
        public static final String MESSAGE_RATE_EXCEEDED = "MessageRateExceeded";
        public static final String MESSAGE_TOO_BIG = "MessageTooBig";
        public static final String INVALID_CREDENTIALS = "InvalidCredentials";
        public static final String MISMATCH_SENDER_ID = "MismatchSenderId";

        private Errors() {
        }

        /** 기기 문제가 아니라 서버·Expo 프로젝트 자격 증명 설정 문제다. 운영자가 고쳐야 한다. */
        public static boolean isConfiguration(String error) {
            return INVALID_CREDENTIALS.equals(error) || MISMATCH_SENDER_ID.equals(error);
        }
    }
}
