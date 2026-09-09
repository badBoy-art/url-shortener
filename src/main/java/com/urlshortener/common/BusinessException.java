package com.urlshortener.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    public BusinessException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(HttpStatus status, String message) {
        this(status, status.value(), message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException gone(String message) {
        return new BusinessException(HttpStatus.GONE, message);
    }

    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static BusinessException internal(String message) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
