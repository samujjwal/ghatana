package com.ghatana.datacloud.entity.storage;

import java.time.Instant;
import java.util.*;

/**
 * Backend-agnostic query specification (AST).
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines a platform-independent query representation that StorageConnectors
 * translate to backend-specific queries (SQL WHERE clauses, Elasticsearch DSL,
 * PromQL, Iceberg queries, etc.). Prevents query logic from hardcoding specific
 * backend syntax.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Immutable value object in domain/storage layer representing query intent.
 * Used by: - QueryExecutionService (application layer) - Creates QuerySpec from
 * user input - StorageConnector (port) - Translates to native queries -
 * Infrastructure adapters - Transform for specific backend - NLQ service -
 * Generates QuerySpec from natural language
 *
 * <p>
 * <b>Query Components</b><br>
 * - Filters: Logical predicates on entity fields (e.g., "age > 18 AND status =
 * 'active'") - Sort: Ordering by one or more fields with direction -
 * Pagination: Limit/offset for result windows - Projections: Select specific
 * fields (reduces data transfer) - Time Window: Optional time range for
 * time-series queries - Aggregations: Group-by, count, sum, avg (for analytics
 * backends)
 *
 * <p>
 * <b>Filter Expression Syntax</b><br>
 * Supports simple predicates: - Equality: "field = value" - Comparison: "field
 * > value", "field >= value", "field < value", "field <= value" - Range: "field
 * BETWEEN value1 AND value2" - String matching: "field LIKE 'pattern%'" - Null
 * check: "field IS NULL", "field IS NOT NULL" - Logical: "expr1 AND expr2",
 * "expr1 OR expr2", "NOT expr"
 *
 * <p>
 * <b>Example Queries</b><br>
 * <pre>{@code
 * // Simple filter
 * QuerySpec query1 = QuerySpec.builder()
 *     .filter("status = 'active'")
 *     .sort("createdAt", SortDirection.DESC)
 *     .limit(20)
 *     .build();
 *
 * // Complex filter with AND/OR
 * QuerySpec query2 = QuerySpec.builder()
 *     .filter("(department = 'engineering' OR department = 'product') AND salary > 100000")
 *     .sort("name", SortDirection.ASC)
 *     .offset(20)
 *     .limit(20)
 *     .build();
 *
 * // Projection
 * QuerySpec query3 = QuerySpec.builder()
 *     .filter("active = true")
 *     .projection(List.of("id", "name", "email"))
 *     .build();
 *
 * // Time-series query
 * QuerySpec query4 = QuerySpec.builder()
 *     .filter("eventType = 'purchase'")
 *     .timeWindow(
 *         Instant.parse("2024-01-01T00:00:00Z"),
 *         Instant.parse("2024-01-31T23:59:59Z")
 *     )
 *     .limit(1000)
 *     .build();
 * }</pre>
 *
 * @see StorageConnector
 * @see StorageConnector.QueryResult
 * @doc.type class
 * @doc.purpose Backend-agnostic query specification (AST)
 * @doc.layer product
 * @doc.pattern Value Object, Query Builder Pattern
 */
public final class QuerySpec {

    private final String filter;
    private final List<SortField> sortFields;
    private final int limit;
    private final int offset;
    private final List<String> projections;
    private final Instant timeWindowStart;
    private final Instant timeWindowEnd;
    private final Map<String, String> metadata;

    /**
     * Private constructor - use Builder.
     */
    private QuerySpec(
            String filter,
            List<SortField> sortFields,
            int limit,
            int offset,
            List<String> projections,
            Instant timeWindowStart,
            Instant timeWindowEnd,
            Map<String, String> metadata
    ) {
        this.filter = filter;
        this.sortFields = Collections.unmodifiableList(sortFields != null ? sortFields : List.of());
        this.limit = limit;
        this.offset = offset;
        this.projections = Collections.unmodifiableList(projections != null ? projections : List.of());
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.metadata = Collections.unmodifiableMap(metadata != null ? metadata : Map.of());
    }

    /**
     * Create builder for fluent QuerySpec construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get filter expression (backend-agnostic).
     */
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    /**
     * Get sort fields (ordered by priority).
     */
    public List<SortField> getSortFields() {
        return sortFields;
    }

    /**
     * Get result limit (0 = unlimited, but adapter may enforce max).
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Get result offset for pagination.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Get projected fields (empty = all fields).
     */
    public List<String> getProjections() {
        return projections;
    }

    /**
     * Get time window start (if present).
     */
    public Optional<Instant> getTimeWindowStart() {
        return Optional.ofNullable(timeWindowStart);
    }

    /**
     * Get time window end (if present).
     */
    public Optional<Instant> getTimeWindowEnd() {
        return Optional.ofNullable(timeWindowEnd);
    }

    /**
     * Get additional metadata (e.g., "returnCount", "explainPlan").
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Check if query has time window (for time-series optimization).
     */
    public boolean hasTimeWindow() {
        return timeWindowStart != null && timeWindowEnd != null;
    }

    /**
     * Check if query has projections (for column selection optimization).
     */
    public boolean hasProjections() {
        return !projections.isEmpty();
    }

    /**
     * Check if query has filters.
     *
     * @return true if filter expression is present
     */
    public boolean hasFilters() {
        return filter != null && !filter.trim().isEmpty();
    }

    /**
     * Get filters as list (compatibility method).
     *
     * <p>
     * Returns singleton list with filter expression if present, empty list
     * otherwise. Used by routing service for filter count estimation.
     *
     * @return list containing filter expression (or empty if no filter)
     */
    public List<String> getFilters() {
        return filter != null && !filter.trim().isEmpty()
                ? List.of(filter)
                : List.of();
    }

    /**
     * Get sorts as list (compatibility method).
     *
     * <p>
     * Alias for getSortFields() for routing service compatibility.
     *
     * @return list of sort fields
     */
    public List<SortField> getSorts() {
        return sortFields;
    }

    /**
     * Check if query has pagination.
     *
     * @return true if limit > 0 or offset > 0
     */
    public boolean hasPagination() {
        return limit > 0 || offset > 0;
    }

    /**
     * Get collection ID (compatibility method).
     *
     * <p>
     * Returns collection ID from metadata if present. Used by routing service
     * to determine target collection.
     *
     * @return collection ID from metadata, or null if not set
     */
    public String getCollectionId() {
        return metadata.get("collectionId");
    }

    /**
     * Check if query is paginated (limit < unlimited).
     */
    public boolean isPaginated() {
        return limit > 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("QuerySpec{");
        if (filter != null) {
            sb.append("filter='").append(filter).append("'");
        }
        if (!sortFields.isEmpty()) {
            sb.append(", sort=").append(sortFields);
        }
        if (limit > 0) {
            sb.append(", limit=").append(limit);
        }
        if (offset > 0) {
            sb.append(", offset=").append(offset);
        }
        if (!projections.isEmpty()) {
            sb.append(", projections=").append(projections);
        }
        if (timeWindowStart != null) {
            sb.append(", timeWindow=[").append(timeWindowStart).append("..").append(timeWindowEnd).append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builder for QuerySpec with fluent API.
     */
    public static class Builder {

        private String filter;
        private List<SortField> sortFields;
        private int limit = 0; // 0 = unlimited
        private int offset = 0;
        private List<String> projections;
        private Instant timeWindowStart;
        private Instant timeWindowEnd;
        private Map<String, String> metadata;

        public Builder filter(String filter) {
            if (filter != null && !filter.isBlank()) {
                this.filter = filter;
            }
            return this;
        }

        public Builder sort(String fieldName, SortDirection direction) {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("Sort field name cannot be null or blank");
            }
            if (this.sortFields == null) {
                this.sortFields = new ArrayList<>();
            }
            this.sortFields.add(new SortField(fieldName, direction));
            return this;
        }

        public Builder sort(List<SortField> fields) {
            if (fields != null && !fields.isEmpty()) {
                if (this.sortFields == null) {
                    this.sortFields = new ArrayList<>();
                }
                this.sortFields.addAll(fields);
            }
            return this;
        }

        public Builder limit(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("Limit cannot be negative");
            }
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("Offset cannot be negative");
            }
            this.offset = offset;
            return this;
        }

        public Builder projection(List<String> fields) {
            if (fields != null && !fields.isEmpty()) {
                this.projections = new ArrayList<>(fields);
            }
            return this;
        }

        public Builder projection(String... fields) {
            if (fields != null && fields.length > 0) {
                this.projections = Arrays.asList(fields);
            }
            return this;
        }

        public Builder timeWindow(Instant start, Instant end) {
            if (start == null || end == null) {
                throw new IllegalArgumentException("Time window start and end cannot be null");
            }
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Time window start cannot be after end");
            }
            this.timeWindowStart = start;
            this.timeWindowEnd = end;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new LinkedHashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Set collection ID in metadata (compatibility method).
         *
         * @param collectionId collection identifier
         * @return this builder
         */
        public Builder collectionId(String collectionId) {
            if (collectionId != null && !collectionId.isBlank()) {
                return metadata("collectionId", collectionId);
            }
            return this;
        }

        /**
         * Build immutable QuerySpec.
         */
        public QuerySpec build() {
            return new QuerySpec(
                    filter,
                    sortFields,
                    limit,
                    offset,
                    projections,
                    timeWindowStart,
                    timeWindowEnd,
                    metadata
            );
        }
    }

    /**
     * Sort direction enumeration.
     */
    public enum SortDirection {
        /**
         * Ascending order (A-Z, 0-9, oldest-newest).
         */
        ASC("asc"),
        /**
         * Descending order (Z-A, 9-0, newest-oldest).
         */
        DESC("desc");

        private final String symbol;

        SortDirection(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        /**
         * Parse sort direction from string (case-insensitive).
         */
        public static SortDirection fromString(String s) {
            if (s == null || s.isBlank()) {
                throw new IllegalArgumentException("Sort direction cannot be null or blank");
            }
            return switch (s.toUpperCase()) {
                case "ASC", "ASCENDING" ->
                    ASC;
                case "DESC", "DESCENDING" ->
                    DESC;
                default ->
                    throw new IllegalArgumentException("Unknown sort direction: " + s);
            };
        }
    }

    /**
     * Single sort field with direction.
     *
     * @param fieldName Field to sort by
     * @param direction Sort order
     */
    public record SortField(String fieldName, SortDirection direction) {
    public SortField

    {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Sort direction cannot be null");
        }
    }

    @Override
    public String toString() {
        return fieldName + " " + direction.getSymbol();
    }
}
}
