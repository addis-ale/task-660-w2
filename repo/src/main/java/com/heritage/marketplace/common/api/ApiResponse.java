package com.heritage.marketplace.common.api;

public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final ApiMeta meta;

    private ApiResponse(boolean success, T data, ApiError error, ApiMeta meta) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, ApiMeta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }

    public ApiMeta getMeta() {
        return meta;
    }
}
