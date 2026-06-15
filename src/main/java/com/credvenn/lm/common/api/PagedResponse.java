package com.credvenn.lm.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sortBy,
        String sortDir) {

    public static <T> PagedResponse<T> fromPage(Page<T> page, String sortBy, String sortDir) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                sortBy,
                sortDir);
    }

    public static <T> PagedResponse<T> fromSlice(
            List<T> items,
            int page,
            int size,
            long totalElements,
            String sortBy,
            String sortDir) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / (double) size);
        return new PagedResponse<>(
                items,
                page,
                size,
                totalElements,
                totalPages,
                page <= 0,
                totalPages == 0 || page >= totalPages - 1,
                sortBy,
                sortDir);
    }
}
