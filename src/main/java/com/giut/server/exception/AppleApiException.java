package com.giut.server.exception;

import org.springframework.http.HttpStatus;

public class AppleApiException extends RuntimeException {

    private final HttpStatus status;

    public AppleApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
