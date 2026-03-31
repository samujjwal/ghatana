/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.util;

import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for pagination validation, offset computation, and cursor encoding.
 *
 * <p>Consolidates pagination helpers previously duplicated across product services.
 * Works with the canonical {@link PageRequest} and {@link Page} platform types.</p>
 *
 * <p>Use this class to validate request parameters at HTTP handler boundaries before
 * constructing a {@link PageRequest} for repository calls:</p>
 *
 * <pre>{@code
 * PageRequest req = PaginationUtils.validateRequest(page, pageSize);
 * List<Entity> rows = repository.findAll(tenantId, req.getOffset(), req.pageSize());
 * long total = repository.count(tenantId);
 * Page<Entity> result = PaginationUtils.toPage(rows, req, total);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pagination validation, offset computation, and cursor encoding utilities
 * @doc.layer core
 * @doc.pattern Utility
 */
public final class PaginationUtils {

    /** Default page size when not specified by the caller. */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /** Maximum allowed page size to prevent excessive memory or DB pressure. */
    public static final int MAX_PAGE_SIZE = 1000;

    /** Minimum allowed page size. */
    public static final int MIN_PAGE_SIZE = 1;

    private PaginationUtils() {
        // Utility class — no instantiation
    }

    // -------------------------------------------------------------------------
    // Request validation
    // -------------------------------------------------------------------------

    /**
     * Validates raw pagination parameters from an HTTP request and returns a
     * canonical {@link PageRequest}.
     *
     * <p>Null inputs are replaced with defaults: page = 0, pageSize = 50.
     * Invalid ranges throw {@link IllegalArgumentException}.</p>
     *
     * @param page     zero-based page number, may be null
     * @param pageSize items per page, may be null
     * @return validated {@link PageRequest}
     * @throws IllegalArgumentException if page &lt; 0 or pageSize outside [1, 1000]
     */
    public static PageRequest validateRequest(Integer page, Integer pageSize) {
        int p  = Objects.requireNonNullElse(page, 0);
        int ps = Objects.requireNonNullElse(pageSize, DEFAULT_PAGE_SIZE);

        if (p < 0) {
            throw new IllegalArgumentException("Page must be >= 0, got: " + p);
        }
        if (ps < MIN_PAGE_SIZE || ps > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                "Page size must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE + ", got: " + ps);
        }

        return PageRequest.of(p, ps);
    }

    /**
     * Clamps a requested page size into the valid range without throwing.
     *
     * <p>Useful when you want to silently limit rather than reject bad input.</p>
     *
     * @param requestedSize the caller-provided page size
     * @return clamped size between {@link #MIN_PAGE_SIZE} and {@link #MAX_PAGE_SIZE}
     */
    public static int clampPageSize(int requestedSize) {
        return Math.max(MIN_PAGE_SIZE, Math.min(requestedSize, MAX_PAGE_SIZE));
    }

    // -------------------------------------------------------------------------
    // Offset / page math
    // -------------------------------------------------------------------------

    /**
     * Calculates the row offset for a given page and page size.
     *
     * @param page     zero-based page number
     * @param pageSize items per page
     * @return row offset (number of rows to skip)
     */
    public static long calculateOffset(int page, int pageSize) {
        return (long) page * pageSize;
    }

    /**
     * Calculates the total number of pages.
     *
     * @param totalElements total items across all pages
     * @param pageSize      items per page
     * @return total page count (always &ge; 1)
     */
    public static int calculateTotalPages(long totalElements, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    // -------------------------------------------------------------------------
    // Page factory
    // -------------------------------------------------------------------------

    /**
     * Wraps a repository result list into a {@link Page}.
     *
     * @param content       the items for the current page
     * @param request       the page request used for the query
     * @param totalElements total items across all pages
     * @param <T>           content type
     * @return canonical page result
     */
    public static <T> Page<T> toPage(List<T> content, PageRequest request, long totalElements) {
        return Page.of(content, request.pageSize(), request.pageNumber(), totalElements);
    }

    // -------------------------------------------------------------------------
    // Cursor encoding (for seek-based, deep pagination)
    // -------------------------------------------------------------------------

    /**
     * Encodes a cursor from the last-seen entity ID and sort value for efficient
     * deep pagination without offset degradation.
     *
     * @param lastId        the ID of the last entity seen on the current page
     * @param lastSortValue the sort value of the last entity (e.g. createdAt ISO string), may be null
     * @return URL-safe Base64-encoded cursor string (no padding)
     */
    public static String encodeCursor(String lastId, String lastSortValue) {
        Objects.requireNonNull(lastId, "lastId must not be null");
        String raw = lastSortValue != null ? lastSortValue + "|" + lastId : lastId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    /**
     * Decodes a cursor produced by {@link #encodeCursor(String, String)}.
     *
     * @param cursor the encoded cursor
     * @return decoded cursor data
     * @throws IllegalArgumentException if the cursor format is invalid
     */
    public static CursorData decodeCursor(String cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            int sep = decoded.indexOf('|');
            if (sep > 0) {
                return new CursorData(decoded.substring(sep + 1), decoded.substring(0, sep));
            }
            return new CursorData(decoded, null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor, e);
        }
    }

    /**
     * Decoded cursor data from {@link #decodeCursor(String)}.
     *
     * @param lastId        the last entity ID
     * @param lastSortValue the last sort value, may be null
     */
    public record CursorData(String lastId, String lastSortValue) {}
}
