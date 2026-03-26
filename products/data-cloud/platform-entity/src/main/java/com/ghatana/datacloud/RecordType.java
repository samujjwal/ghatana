package com.ghatana.datacloud;

/**
 * Defines the behavior characteristics of records in Data-Cloud.
 *
 * <p>
 * <b>Purpose</b><br>
 * RecordType determines how a record can be manipulated and what operations are
 * supported. This enables a unified storage layer that handles different data
 * patterns through a single interface.
 *
 * <p>
 * <b>Record Types</b><br>
 * <ul>
 * <li><b>ENTITY</b> - Mutable records with CRUD operations (customers,
 * products)</li>
 * <li><b>EVENT</b> - Immutable, append-only records with ordering
 * (order-placed, payment-received)</li>
 * <li><b>TIMESERIES</b> - Timestamped data points with aggregation (metrics,
 * sensor data)</li>
 * <li><b>GRAPH</b> - Nodes with relationships and traversal (social network,
 * knowledge graph)</li>
 * <li><b>DOCUMENT</b> - Schema-free records with flexible structure
 * (configurations, preferences)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Check record type capabilities
 * RecordType type = RecordType.EVENT;
 * if (type.isAppendOnly()) {
 *     // Use append operation
 * } else {
 *     // Use upsert operation
 * }
 *
 * // Get supported operations
 * if (type.supportsStreaming()) {
 *     // Enable tailing/subscription
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Define record behavior characteristics
 * @doc.layer core
 * @doc.pattern Strategy, Type Object
 */
public enum RecordType {

    /**
     * Mutable entity - supports full CRUD operations.
     * <p>
     * Use for business objects that change over time.
     * <p>
     * Examples: Customer, Product, Order, User
     */
    ENTITY(
            true, // mutable
            false, // not append-only
            false, // not ordered
            false, // not timestamped
            false, // not aggregatable
            "Mutable entity - full CRUD operations"
    ),
    /**
     * Immutable event - append-only, ordered within partition.
     * <p>
     * Use for facts that happened and cannot be changed.
     * <p>
     * Examples: OrderPlaced, PaymentReceived, UserLoggedIn
     */
    EVENT(
            false, // immutable
            true, // append-only
            true, // ordered by offset
            true, // has occurrence time
            false, // not aggregatable by default
            "Immutable event - append-only, ordered"
    ),
    /**
     * Time-series data - timestamped, aggregatable.
     * <p>
     * Use for metrics and measurements over time.
     * <p>
     * Examples: CPU usage, temperature readings, stock prices
     */
    TIMESERIES(
            false, // immutable
            true, // append-only
            true, // ordered by timestamp
            true, // timestamped
            true, // aggregatable
            "Time-series - timestamped, aggregatable"
    ),
    /**
     * Graph node - supports relationships and traversal.
     * <p>
     * Use for connected data with relationships.
     * <p>
     * Examples: Person (knows), Document (references), Asset (depends-on)
     */
    GRAPH(
            true, // mutable (relationships can change)
            false, // not append-only
            false, // not ordered
            false, // not timestamped
            false, // not aggregatable
            "Graph node - relationships, traversal"
    ),
    /**
     * Schema-free document - flexible structure.
     * <p>
     * Use for semi-structured data without fixed schema.
     * <p>
     * Examples: Configuration, UserPreferences, DynamicForm
     */
    DOCUMENT(
            true, // mutable
            false, // not append-only
            false, // not ordered
            false, // not timestamped
            false, // not aggregatable
            "Schema-free document - flexible structure"
    );

    private final boolean mutable;
    private final boolean appendOnly;
    private final boolean ordered;
    private final boolean timestamped;
    private final boolean aggregatable;
    private final String description;

    RecordType(
            boolean mutable,
            boolean appendOnly,
            boolean ordered,
            boolean timestamped,
            boolean aggregatable,
            String description) {
        this.mutable = mutable;
        this.appendOnly = appendOnly;
        this.ordered = ordered;
        this.timestamped = timestamped;
        this.aggregatable = aggregatable;
        this.description = description;
    }

    /**
     * Whether records of this type can be updated after creation.
     *
     * @return true if records support update operations
     */
    public boolean isMutable() {
        return mutable;
    }

    /**
     * Whether records of this type are append-only (no updates/deletes).
     *
     * @return true if records can only be appended
     */
    public boolean isAppendOnly() {
        return appendOnly;
    }

    /**
     * Whether records of this type maintain ordering (by offset or timestamp).
     *
     * @return true if records have a defined order
     */
    public boolean isOrdered() {
        return ordered;
    }

    /**
     * Whether records of this type have a primary timestamp.
     *
     * @return true if records are timestamped
     */
    public boolean isTimestamped() {
        return timestamped;
    }

    /**
     * Whether records of this type support aggregation operations.
     *
     * @return true if records can be aggregated
     */
    public boolean isAggregatable() {
        return aggregatable;
    }

    /**
     * Human-readable description of this record type.
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    // ==================== Capability Checks ====================
    /**
     * Whether this type supports CRUD operations.
     *
     * @return true if create/read/update/delete supported
     */
    public boolean supportsCRUD() {
        return mutable;
    }

    /**
     * Whether this type supports streaming/tailing operations.
     *
     * @return true if real-time subscription supported
     */
    public boolean supportsStreaming() {
        return appendOnly;
    }

    /**
     * Whether this type supports time-range queries.
     *
     * @return true if time-based queries supported
     */
    public boolean supportsTimeRangeQuery() {
        return timestamped;
    }

    /**
     * Whether this type supports offset-based reads.
     *
     * @return true if offset-based range reads supported
     */
    public boolean supportsOffsetRead() {
        return ordered && appendOnly;
    }

    /**
     * Whether soft-delete is applicable to this type.
     *
     * @return true if soft-delete makes sense
     */
    public boolean supportsSoftDelete() {
        return mutable;
    }

    /**
     * Whether optimistic locking (versioning) is applicable.
     *
     * @return true if versioning makes sense
     */
    public boolean supportsVersioning() {
        return mutable;
    }
}
