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

    public static <T> ApiResponse<T> created(T data, String traceId) {
        return new ApiResponse<>("CREATED", "created", traceId, data);
    }

    public static ApiResponse<Void> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, traceId, null);
    }
}
