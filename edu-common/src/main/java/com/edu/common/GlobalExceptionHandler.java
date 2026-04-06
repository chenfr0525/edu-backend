package com.edu.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("Runtime exception at [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<String> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied at [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(403, "Access denied: " + e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidationException(Exception e, HttpServletRequest request) {
        log.warn("Validation error at [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.error(400, "Validation error");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected exception at [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
