package com.cn.zym.note.web;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.common.ApiErrorBodies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandlers {

    @ExceptionHandler(ApiBusinessException.class)
    ResponseEntity<ApiErrorBodies.Problem> business(ApiBusinessException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(new ApiErrorBodies.Problem(e.getCode(), e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorBodies.Problem> validation(MethodArgumentNotValidException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "validation_failed";
        return ResponseEntity.badRequest().body(new ApiErrorBodies.Problem("VALIDATION_ERROR", msg, null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorBodies.Problem> fallback(Exception e) {
        log.error("Unhandled", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorBodies.Problem("INTERNAL", "服务器内部错误", null));
    }
}
