package com.giut.server.global.exception;

import com.giut.server.auth.exception.AppleApiException;
import com.giut.server.auth.exception.AuthenticationFailedException;
import com.giut.server.auth.exception.InvalidTokenException;
import com.giut.server.auth.exception.KakaoApiException;
import com.giut.server.global.alert.DiscordAlertService;
import com.giut.server.global.dto.ResultDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final DiscordAlertService discordAlertService;

    @Autowired
    public GlobalExceptionHandler(DiscordAlertService discordAlertService) {
        this.discordAlertService = discordAlertService;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ResultDto> handleResourceNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidTokenException.class, AuthenticationFailedException.class})
    public ResponseEntity<ResultDto> handleAuthentication(RuntimeException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(KakaoApiException.class)
    public ResponseEntity<ResultDto> handleKakaoApiException(KakaoApiException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(AppleApiException.class)
    public ResponseEntity<ResultDto> handleAppleApiException(AppleApiException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResultDto> handleConflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ResultDto> handleForbidden(ForbiddenException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<ResultDto> handleUnsupportedMediaType(UnsupportedMediaTypeException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultDto> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResultDto> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        logger.error("Internal state failure", e);
        discordAlertService.notifyServerError(request, e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultDto> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResultDto> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResultDto> handleMissingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "필수 요청 파라미터가 없습니다: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResultDto> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다: " + e.getName());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, BindException.class})
    public ResponseEntity<ResultDto> handleMalformedRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResultDto> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResultDto> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ResultDto> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return error(HttpStatus.NOT_ACCEPTABLE, "요청한 응답 형식을 지원하지 않습니다.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResultDto> handleNoResource(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultDto> handleAllExceptions(Exception e, HttpServletRequest request) {
        logger.error("Unexpected server error", e);
        discordAlertService.notifyServerError(request, e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ResultDto> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ResultDto.builder()
                .success(false)
                .message(message)
                .code(status.value())
                .build());
    }
}
