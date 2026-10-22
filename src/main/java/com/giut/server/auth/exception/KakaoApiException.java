package com.giut.server.auth.exception;

import org.springframework.http.HttpStatus;

public class KakaoApiException extends RuntimeException {
        private final HttpStatus status;

        public KakaoApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
}
