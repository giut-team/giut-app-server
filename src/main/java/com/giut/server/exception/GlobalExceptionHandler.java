package com.giut.server.exception;


import com.giut.server.dto.ResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 걍 일반적인 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultDto> handleAllExceptions(Exception e) {
        logger.error("An unexpected error occurred", e);

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("Internal server error")
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(resultDto);
    }

    // NotFound 예외
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ResultDto> handleResourceNotFound(ResourceNotFoundException e) {
        logger.error("Resource Not Found : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("Resource not Found : " + e.getMessage())
                .code(HttpStatus.NOT_FOUND.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(resultDto);
    }

    // 유효하지 않은 토큰 예외
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ResultDto> handleInvalidToken(InvalidTokenException e) {
        logger.error("Invalid Token : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("Invalid Token : " + e.getMessage())
                .code(HttpStatus.UNAUTHORIZED.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(resultDto);
    }

    // AuthenticationFailed
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ResultDto> handleAuthenticationFailed(AuthenticationFailedException e) {
        logger.error("AuthenticationFailed : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("AuthenticationFailed : " + e.getMessage())
                .code(HttpStatus.UNAUTHORIZED.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(resultDto);
    }

    @ExceptionHandler(KakaoApiException.class)
    public ResponseEntity<ResultDto> handleKakaoApiException(KakaoApiException e) {
        logger.error("KakaoApiException : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("KakaoApiException : " + e.getMessage())
                .code(e.getStatus().value())
                .build();

        return ResponseEntity
                .status(e.getStatus())
                .body(resultDto);
    }

    // Conflict
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResultDto> handleConflict(ConflictException e) {
        logger.error("ConflictException : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("ConflictException : " + e.getMessage())
                .code(HttpStatus.CONFLICT.value())
                .build();

        return new ResponseEntity<>(resultDto, HttpStatus.CONFLICT);
    }

    // Forbidden
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ResultDto> handleForbidden(ForbiddenException e) {
        logger.error("ForbiddenException : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("ForbiddenException : " + e.getMessage())
                .code(HttpStatus.FORBIDDEN.value())
                .build();

        return new ResponseEntity<>(resultDto, HttpStatus.FORBIDDEN);
    }

    // UnsupportedMediaType
    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<ResultDto> handleUnsupportedMediaType(UnsupportedMediaTypeException e) {
        logger.error("UnsupportedMediaTypeException : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("UnsupportedMediaTypeException : " + e.getMessage())
                .code(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .build();

        return new ResponseEntity<>(resultDto, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultDto> handleIllegalArgument(IllegalArgumentException e) {
        logger.error("IllegalArgumentException : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("IllegalArgumentException : " + e.getMessage())
                .code(HttpStatus.BAD_REQUEST.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(resultDto);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResultDto> handleIllegalState(IllegalStateException e) {
        logger.error("IllegalStateException : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message("IllegalStateException : " + e.getMessage())
                .code(HttpStatus.BAD_REQUEST.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(resultDto);
    }

    // 요청 형식 오류
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ResultDto> handleBadRequest(Exception e) {
        logger.error(e.getClass().getSimpleName() + " : {}", e.getMessage());

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message(e.getClass().getSimpleName() + " : " + e.getMessage())
                .code(HttpStatus.BAD_REQUEST.value())
                .build();

        return new ResponseEntity<>(resultDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultDto> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        ResultDto resultDto = ResultDto.builder()
                .success(false)
                .message(message)
                .code(HttpStatus.BAD_REQUEST.value())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(resultDto);
    }
}
