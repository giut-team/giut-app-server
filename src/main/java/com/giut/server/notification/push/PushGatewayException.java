package com.giut.server.notification.push;

/** 요청 전체 실패. retryable이면 같은 메시지를 나중에 다시 보내도 된다(네트워크·시간 초과·429·5xx). */
public class PushGatewayException extends RuntimeException {
    private final boolean retryable;
    private final String code;

    public PushGatewayException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public PushGatewayException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }

    public String code() {
        return code;
    }
}
