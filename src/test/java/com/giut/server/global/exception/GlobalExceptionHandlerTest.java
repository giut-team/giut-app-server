package com.giut.server.global.exception;

import com.giut.server.auth.exception.AuthenticationFailedException;

import com.giut.server.global.dto.ResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void usesDomainStatusCodesAndConsistentBody() {
        assertError(handler.handleAuthentication(new AuthenticationFailedException("인증 실패")), 401);
        assertError(handler.handleForbidden(new ForbiddenException("권한 없음")), 403);
        assertError(handler.handleResourceNotFound(new ResourceNotFoundException("없음")), 404);
        assertError(handler.handleConflict(new ConflictException("중복")), 409);
    }

    @Test
    void doesNotExposeInternalStateDetails() {
        ResponseEntity<ResultDto> response = handler.handleIllegalState(new IllegalStateException("비밀 설정값"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).doesNotContain("비밀 설정값");
        assertError(response, 500);
    }

    private void assertError(ResponseEntity<ResultDto> response, int code) {
        assertThat(response.getStatusCode().value()).isEqualTo(code);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(code);
    }
}
