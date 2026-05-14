package com.wecombft.shared.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wecombft.shared.error.ErrorCode;
import com.wecombft.shared.trace.TraceIds;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
            .body(ApiResponse.error(exception.code(), exception.getMessage(), TraceIds.currentOrCreate()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingRequestHeaderException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        ErrorCode errorCode = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(errorCode.httpStatus())
            .body(ApiResponse.error(errorCode.code(), exception.getMessage(), TraceIds.currentOrCreate()));
    }
}
