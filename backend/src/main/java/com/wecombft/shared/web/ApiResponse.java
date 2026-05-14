package com.wecombft.shared.web;

public record ApiResponse<T>(
    String code,
    String message,
    String traceId,
    T data
) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "ok", traceId, data);
    }
}
