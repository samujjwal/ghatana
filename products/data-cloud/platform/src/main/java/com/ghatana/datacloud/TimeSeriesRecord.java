package com.ghatana.datacloud;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Time-series record - timestamped data points with aggregation support.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents measurements and metrics collected over time. Optimized for
 * time-range queries, aggregations (sum, avg, min, max), and downsampling.
 *
 * <p>
 * <b>Features</b><br>
 * <ul>
 * <li><b>Timestamped</b> - Primary timestamp for ordering and queries</li>
 * <li><b>Aggregatable</b> - Supports rollups and downsampling</li>
 * <li><b>Tagged</b> - Dimension tags for filtering and grouping</li>
 * <li><b>Numeric Focus</b> - Optimized for numeric values</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * TimeSeriesRecord metric = TimeSeriesRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("system-metrics")
 *     .metricName("cpu_usage")
 *     .timestamp(Instant.now())
 *     .value(72.5)
 *     .tags(Map.of(
 *         "host", "server-1",
 *         "region", "us-east",
 *         "env", "production"
 *     ))
 *     .build();
 *
 * // Query with aggregation
 * AggregationResult result = storagePlugin.aggregate(
 *     tenantId, "system-metrics",
 *     AggregationQuery.builder()
 *         .metric("cpu_usage")
 *         .aggregation(Aggregation.AVG)
 *         .groupBy("host")
 *         .timeRange(lastHour)
 *         .interval(Duration.ofMinutes(5))
 *         .build()
 * );
 * }</pre>
 *
 * <p>
 * <b>Database Table</b><br>
 * <pre>
 * CREATE TABLE timeseries (
 *     id UUID PRIMARY KEY,
 *     tenant_id VARCHAR(255) NOT NULL,
 *     collection_name VARCHAR(255) NOT NULL,
 *     record_type VARCHAR(50) NOT NULL,
 *     metric_name VARCHAR(255) NOT NULL,
 *     timestamp TIMESTAMP NOT NULL,
 *     value DOUBLE PRECISION,
 *     tags JSONB,
 *     data JSONB,
 *     metadata JSONB,
 *     created_at TIMESTAMP,
 *     created_by VARCHAR(255)
 * );
 * -- Use TimescaleDB hypertable or partitioning for performance
 * </pre>
 *
 * @see Record
 * @see RecordType#TIMESERIES
 * @doc.type class
 * @doc.purpose Time-series record for metrics and measurements
 * @doc.layer core
 * @doc.pattern Time-Series Data Model
 */
@Entity
@Table(name = "timeseries", indexes = {
    @Index(name = "idx_timeseries_tenant", columnList = "tenant_id"),
    @Index(name = "idx_timeseries_metric", columnList = "tenant_id, collection_name, metric_name"),
    @Index(name = "idx_timeseries_time", columnList = "tenant_id, collection_name, metric_name, timestamp DESC")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesRecord extends DataRecord {

    /**
     * Name of the metric being measured.
     * <p>
     * Examples: cpu_usage, memory_bytes, request_count, temperature
     */
    @Column(name = "metric_name", nullable = false, length = 255)
    private String metricName;

    /**
     * Timestamp of the measurement.
     * <p>
     * Primary ordering and query field for time-series data.
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Numeric value of the measurement.
     * <p>
     * For multi-value metrics, use the data JSONB field.
     */
    @Column(name = "value")
    private Double value;

    /**
     * Dimension tags for filtering and grouping.
     * <p>
     * Examples: host, region, env, service, version
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();

    @Override
    public RecordType getRecordType() {
        return RecordType.TIMESERIES;
    }

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (recordType == null) {
            recordType = RecordType.TIMESERIES;
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (tags == null) {
            tags = new HashMap<>();
        }
    }

    /**
     * Add a tag to this metric.
     *
     * @param key tag name
     * @param value tag value
     * @return this record for chaining
     */
    public TimeSeriesRecord tag(String key, String value) {
        if (tags == null) {
            tags = new HashMap<>();
        }
        tags.put(key, value);
        return this;
    }

    /**
     * Get a tag value.
     *
     * @param key tag name
     * @return tag value or null
     */
    public String getTag(String key) {
        return tags != null ? tags.get(key) : null;
    }

    /**
     * Check if this metric has a specific tag.
     *
     * @param key tag name
     * @return true if tag exists
     */
    public boolean hasTag(String key) {
        return tags != null && tags.containsKey(key);
    }

    @Override
    public String toString() {
        return "TimeSeriesRecord{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", metricName='" + metricName + '\''
                + ", timestamp=" + timestamp
                + ", value=" + value
                + ", tags=" + tags
                + '}';
    }
}
