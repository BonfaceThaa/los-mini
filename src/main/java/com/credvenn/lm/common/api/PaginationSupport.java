package com.credvenn.lm.common.api;

import com.credvenn.lm.common.exception.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationSupport {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private PaginationSupport() {
    }

    public static Pageable pageable(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Map<String, String> supportedSorts,
            String defaultSortBy) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedSortBy = normalizeSortBy(sortBy, supportedSorts, defaultSortBy);
        Sort.Direction direction = normalizeDirection(sortDir);
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(direction, supportedSorts.get(normalizedSortBy)));
    }

    public static String normalizeSortBy(String sortBy, Map<String, String> supportedSorts, String defaultSortBy) {
        String candidate = sortBy == null || sortBy.isBlank() ? defaultSortBy : sortBy.trim();
        if (!supportedSorts.containsKey(candidate)) {
            throw new BadRequestException("Unsupported sortBy value: " + candidate);
        }
        return candidate;
    }

    public static String normalizeDirectionValue(String sortDir) {
        return normalizeDirection(sortDir).name().toLowerCase(Locale.ROOT);
    }

    public static int normalizePage(Integer page) {
        int value = page == null ? DEFAULT_PAGE : page;
        if (value < 0) {
            throw new BadRequestException("page must be zero or greater");
        }
        return value;
    }

    public static int normalizeSize(Integer size) {
        int value = size == null ? DEFAULT_SIZE : size;
        if (value <= 0 || value > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        return value;
    }

    public static <T> PagedResponse<T> paginateList(
            List<T> items,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Map<String, Function<T, ? extends Comparable<?>>> supportedSorts,
            String defaultSortBy) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedSortBy = normalizeSortBy(sortBy, supportedSortKeys(supportedSorts), defaultSortBy);
        String normalizedSortDir = normalizeDirectionValue(sortDir);
        List<T> sorted = items.stream()
                .sorted(comparator(supportedSorts.get(normalizedSortBy), normalizedSortDir))
                .toList();
        int fromIndex = Math.min(normalizedPage * normalizedSize, sorted.size());
        int toIndex = Math.min(fromIndex + normalizedSize, sorted.size());
        return PagedResponse.fromSlice(
                sorted.subList(fromIndex, toIndex),
                normalizedPage,
                normalizedSize,
                sorted.size(),
                normalizedSortBy,
                normalizedSortDir);
    }

    private static Sort.Direction normalizeDirection(String sortDir) {
        if (sortDir == null || sortDir.isBlank()) {
            return Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.fromString(sortDir);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("sortDir must be asc or desc");
        }
    }

    private static <T> Comparator<T> comparator(Function<T, ? extends Comparable<?>> keyExtractor, String sortDir) {
        Comparator<T> comparator = Comparator.comparing(
                item -> comparableValue(keyExtractor.apply(item)),
                Comparator.nullsLast(Comparator.naturalOrder()));
        return "asc".equals(sortDir) ? comparator : comparator.reversed();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable comparableValue(Comparable<?> value) {
        if (value instanceof String stringValue) {
            return stringValue.toLowerCase(Locale.ROOT);
        }
        return (Comparable) value;
    }

    private static <T> Map<String, String> supportedSortKeys(Map<String, Function<T, ? extends Comparable<?>>> supportedSorts) {
        return supportedSorts.keySet().stream().collect(java.util.stream.Collectors.toMap(Function.identity(), Function.identity()));
    }
}
