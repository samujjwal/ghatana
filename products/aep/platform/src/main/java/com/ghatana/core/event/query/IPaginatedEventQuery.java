package com.ghatana.core.event.query;

import com.ghatana.platform.domain.domain.event.Event;

/**
 * Extends IEventQuery to support cursor-based pagination for event queries.
 * This interface provides methods for paginating through large result sets
 * efficiently using cursors.
 *
 * @doc.type interface
 * @doc.purpose Extends IEventQuery with cursor-based pagination for efficient traversal of large event result sets
 * @doc.layer core
 * @doc.pattern Port
 */
public interface IPaginatedEventQuery extends IEventQuery {
    
    /**
     * Returns the maximum number of events to return per page.
     * 
     * @return the page size, or 0 for no limit
     */
    int getPageSize();
    
    /**
     * Returns the cursor for the current page, or null for the first page.
     * 
     * @return the current cursor, or null for the first page
     */
    Cursor getCursor();
    
    /**
     * Returns whether to include the cursor for the next page in the result.
     * 
     * @return true to include the next page cursor, false otherwise
     */
    boolean includeNextCursor();
    
    /**
     * Returns whether to include the cursor for the previous page in the result.
     * 
     * @return true to include the previous page cursor, false otherwise
     */
    boolean includePreviousCursor();
    
    /**
     * Creates a new query with the specified cursor.
     * 
     * @param cursor the cursor to use for pagination
     * @return a new IPaginatedEventQuery with the specified cursor
     */
    IPaginatedEventQuery withCursor(Cursor cursor);
    
    /**
     * Creates a new query with the specified page size.
     * 
     * @param pageSize the maximum number of events to return per page
     * @return a new IPaginatedEventQuery with the specified page size
     */
    IPaginatedEventQuery withPageSize(int pageSize);
    
    /**
     * Creates a new query with the specified cursor inclusion flags.
     * 
     * @param includeNext whether to include the next page cursor
     * @param includePrevious whether to include the previous page cursor
     * @return a new IPaginatedEventQuery with the specified cursor inclusion flags
     */
    IPaginatedEventQuery withCursorInclusion(boolean includeNext, boolean includePrevious);
    
    /**
     * Creates a default implementation of IPaginatedEventQuery.
     * 
     * @param baseQuery the base query to paginate
     * @param pageSize the maximum number of events to return per page
     * @param cursor the cursor for the current page, or null for the first page
     * @return a new IPaginatedEventQuery instance
     */
    static IPaginatedEventQuery of(IEventQuery baseQuery, int pageSize, Cursor cursor) {
        return new DefaultPaginatedEventQuery(baseQuery, pageSize, cursor, true, false);
    }
    
    /**
     * Creates a default implementation of IPaginatedEventQuery with cursor inclusion flags.
     * 
     * @param baseQuery the base query to paginate
     * @param pageSize the maximum number of events to return per page
     * @param cursor the cursor for the current page, or null for the first page
     * @param includeNext whether to include the next page cursor
     * @param includePrevious whether to include the previous page cursor
     * @return a new IPaginatedEventQuery instance
     */
    static IPaginatedEventQuery of(IEventQuery baseQuery, int pageSize, Cursor cursor, 
                                 boolean includeNext, boolean includePrevious) {
        return new DefaultPaginatedEventQuery(baseQuery, pageSize, cursor, includeNext, includePrevious);
    }

    boolean matches(Event event);

    /**
     * Default implementation of IPaginatedEventQuery.
     */
    class DefaultPaginatedEventQuery implements IPaginatedEventQuery {
        private final IEventQuery baseQuery;
        private final int pageSize;
        private final Cursor cursor;
        private final boolean includeNext;
        private final boolean includePrevious;
        
        public DefaultPaginatedEventQuery(IEventQuery baseQuery, int pageSize, Cursor cursor,
                                        boolean includeNext, boolean includePrevious) {
            this.baseQuery = baseQuery != null ? baseQuery : IEventQuery.all();
            this.pageSize = Math.max(1, pageSize);
            this.cursor = cursor != null ? cursor : Cursor.initial();
            this.includeNext = includeNext;
            this.includePrevious = includePrevious;
        }
        
        @Override
        public boolean matches(Event event) {
            return baseQuery.matches(event);
        }
        
        @Override
        public long getPollInterval() {
            return baseQuery.getPollInterval();
        }
        
        @Override
        public int getPageSize() {
            return pageSize;
        }
        
        @Override
        public Cursor getCursor() {
            return cursor;
        }
        
        @Override
        public boolean includeNextCursor() {
            return includeNext;
        }
        
        @Override
        public boolean includePreviousCursor() {
            return includePrevious;
        }
        
        @Override
        public IPaginatedEventQuery withCursor(Cursor cursor) {
            return new DefaultPaginatedEventQuery(baseQuery, pageSize, cursor, includeNext, includePrevious);
        }
        
        @Override
        public IPaginatedEventQuery withPageSize(int pageSize) {
            return new DefaultPaginatedEventQuery(baseQuery, pageSize, cursor, includeNext, includePrevious);
        }
        
        @Override
        public IPaginatedEventQuery withCursorInclusion(boolean includeNext, boolean includePrevious) {
            return new DefaultPaginatedEventQuery(baseQuery, pageSize, cursor, includeNext, includePrevious);
        }
        
        @Override
        public String toString() {
            return String.format(
                "PaginatedQuery{baseQuery=%s, pageSize=%d, cursor=%s, includeNext=%b, includePrevious=%b}",
                baseQuery, pageSize, cursor, includeNext, includePrevious
            );
        }
    }
}
