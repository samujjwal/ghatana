package com.ghatana.eventcore.domain;

/**
 * Simple pagination cursor for read operations.
 * Renamed from Page to PageCursor to avoid conflict with com.ghatana.core.common.pagination.Page.
 */
public record PageCursor(int size, String token) {
    public PageCursor {
        if (size <= 0) {
            size = 100;
        }
        token = token == null ? "" : token;
    }
}
