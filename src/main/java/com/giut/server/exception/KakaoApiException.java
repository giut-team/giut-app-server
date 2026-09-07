package com.giut.server.exception;

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
