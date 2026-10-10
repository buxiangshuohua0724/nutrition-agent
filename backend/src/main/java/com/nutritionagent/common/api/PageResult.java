package com.nutritionagent.common.api;

/**
 * 分页响应结构（{@code docs/api-contract.md} 第一章）。
 *
 * @param <T> 列表元素类型
 */
public record PageResult<T>(
        java.util.List<T> list,
        int page,
        int pageSize,
        long total,
        int totalPages
) {

    public static <T> PageResult<T> of(java.util.List<T> list, int page, int pageSize, long total) {
        int totalPages = pageSize <= 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(list, page, pageSize, total, totalPages);
    }

    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(java.util.List.of(), page, pageSize, 0, 0);
    }
}
