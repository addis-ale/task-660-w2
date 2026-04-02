package com.heritage.marketplace.common.api;

public record ApiMeta(Integer page, Integer pageSize, Long totalItems, Integer totalPages) {

    public static ApiMeta of(int page, int pageSize, long totalItems, int totalPages) {
        return new ApiMeta(page, pageSize, totalItems, totalPages);
    }
}
