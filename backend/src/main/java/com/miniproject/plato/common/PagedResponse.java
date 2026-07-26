package com.miniproject.plato.common;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated response wrapper — used whenever an endpoint returns a list
 * split across multiple pages.
 *
 * <p>Accepts a Spring {@link Page} object directly and extracts the
 * metadata the frontend needs to render pagination controls.
 *
 * <p>Usage in a controller:
 * <pre>
 *   Page&lt;OrderResponse&gt; page = orderService.getOrders(restaurantId, pageable);
 *   return ResponseEntity.ok(ApiResponse.ok("Orders retrieved", new PagedResponse&lt;&gt;(page)));
 * </pre>
 *
 * <p>The resulting JSON contains:
 * <pre>
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 157,
 *   "totalPages": 8,
 *   "last": false
 * }
 * </pre>
 */
@Getter
public class PagedResponse<T> {

    private final List<T> content;       // items on this page
    private final int page;              // current page number (0-indexed)
    private final int size;              // items per page
    private final long totalElements;    // total items across ALL pages
    private final int totalPages;        // total number of pages
    private final boolean last;          // true if this is the last page

    /**
     * Build from a Spring Data {@link Page}.
     * The type {@code T} should be a DTO, not a raw entity.
     */
    public PagedResponse(Page<T> pageResult) {
        this.content = pageResult.getContent();
        this.page = pageResult.getNumber();
        this.size = pageResult.getSize();
        this.totalElements = pageResult.getTotalElements();
        this.totalPages = pageResult.getTotalPages();
        this.last = pageResult.isLast();
    }
}
