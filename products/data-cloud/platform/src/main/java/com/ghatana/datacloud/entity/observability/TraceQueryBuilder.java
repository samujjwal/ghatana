package com.ghatana.datacloud.entity.observability;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Fluent builder for constructing trace queries with multiple filtering criteria.
 *
 * <p><b>Purpose</b><br>
 * Provides DSL for building complex trace queries with time ranges, attribute
 * matching, span count filters, error detection, and duration constraints.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TraceQueryBuilder query = TraceQueryBuilder.create()
 *     .withOperationName("user.login")
 *     .withTimeRange(startTime, endTime)
 *     .withSpanCountRange(5, 100)
 *     .withErrorsOnly(true)
 *     .withDurationRange(100, 5000);
 *
 * // Build returns a concrete query object
 * AbstractTraceQuery builtQuery = query.build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Builder for trace query DSL
 * @doc.layer product
 * @doc.pattern Builder
 */
public class TraceQueryBuilder {
    private String operationNameFilter;
    private Instant timeRangeStart;
    private Instant timeRangeEnd;
    private Integer spanCountMin;
    private Integer spanCountMax;
    private Long durationMinMs;
    private Long durationMaxMs;
    private Boolean errorsOnly;
    private final Map<String, AttributeFilter> attributeFilters = new HashMap<>();

    private TraceQueryBuilder() {
    }

    /**
     * Create new trace query builder with empty criteria.
     *
     * @return builder instance
     */
    public static TraceQueryBuilder create() {
        return new TraceQueryBuilder();
    }

    /**
     * Filter traces by operation name (exact match).
     *
     * @param operationName operation to match (non-blank)
     * @return this builder for chaining
     */
    public TraceQueryBuilder withOperationName(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName must be non-blank");
        }
        this.operationNameFilter = operationName;
        return this;
    }

    /**
     * Filter traces by time range.
     *
     * @param startTime earliest span time (inclusive)
     * @param endTime latest span time (inclusive)
     * @return this builder for chaining
     */
    public TraceQueryBuilder withTimeRange(Instant startTime, Instant endTime) {
        Objects.requireNonNull(startTime, "startTime required");
        Objects.requireNonNull(endTime, "endTime required");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must be ≥ startTime");
        }
        this.timeRangeStart = startTime;
        this.timeRangeEnd = endTime;
        return this;
    }

    /**
     * Filter traces by span count range.
     *
     * @param minSpans minimum span count (inclusive)
     * @param maxSpans maximum span count (inclusive)
     * @return this builder for chaining
     */
    public TraceQueryBuilder withSpanCountRange(int minSpans, int maxSpans) {
        if (minSpans < 0) throw new IllegalArgumentException("minSpans must be ≥ 0");
        if (maxSpans < minSpans) throw new IllegalArgumentException("maxSpans must be ≥ minSpans");
        this.spanCountMin = minSpans;
        this.spanCountMax = maxSpans;
        return this;
    }

    /**
     * Filter traces by duration range in milliseconds.
     *
     * @param minMs minimum duration (inclusive)
     * @param maxMs maximum duration (inclusive)
     * @return this builder for chaining
     */
    public TraceQueryBuilder withDurationRange(long minMs, long maxMs) {
        if (minMs < 0) throw new IllegalArgumentException("minMs must be ≥ 0");
        if (maxMs < minMs) throw new IllegalArgumentException("maxMs must be ≥ minMs");
        this.durationMinMs = minMs;
        this.durationMaxMs = maxMs;
        return this;
    }

    /**
     * Filter to include only traces with ERROR spans.
     *
     * @param errorsOnly true to return only error traces
     * @return this builder for chaining
     */
    public TraceQueryBuilder withErrorsOnly(boolean errorsOnly) {
        this.errorsOnly = errorsOnly;
        return this;
    }

    /**
     * Add attribute filter (exact match, contains, or regex).
     *
     * @param attributeName span attribute name
     * @param filter matching criteria
     * @return this builder for chaining
     */
    public TraceQueryBuilder withAttributeFilter(String attributeName, AttributeFilter filter) {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("attributeName must be non-blank");
        }
        Objects.requireNonNull(filter, "filter required");
        attributeFilters.put(attributeName, filter);
        return this;
    }

    /**
     * Add attribute exact match filter.
     *
     * @param attributeName attribute to match
     * @param value exact value to match
     * @return this builder for chaining
     */
    public TraceQueryBuilder withAttributeExact(String attributeName, String value) {
        return withAttributeFilter(attributeName, new ExactAttributeFilter(value));
    }

    /**
     * Add attribute contains filter.
     *
     * @param attributeName attribute to match
     * @param substring substring to search for
     * @return this builder for chaining
     */
    public TraceQueryBuilder withAttributeContains(String attributeName, String substring) {
        return withAttributeFilter(attributeName, new ContainsAttributeFilter(substring));
    }

    /**
     * Add attribute regex filter.
     *
     * @param attributeName attribute to match
     * @param regex regex pattern
     * @return this builder for chaining
     */
    public TraceQueryBuilder withAttributeRegex(String attributeName, String regex) {
        return withAttributeFilter(attributeName, new RegexAttributeFilter(regex));
    }

    /**
     * Build immutable query from configured criteria.
     *
     * @return query ready for execution
     */
    public AbstractTraceQuery build() {
        return new AbstractTraceQuery(
                operationNameFilter,
                timeRangeStart,
                timeRangeEnd,
                spanCountMin,
                spanCountMax,
                durationMinMs,
                durationMaxMs,
                errorsOnly,
                new HashMap<>(attributeFilters)
        );
    }

    /**
     * Base class for attribute matching filters.
     *
     * <p>Implementations define how to match span attributes.
     */
    public static abstract class AttributeFilter {
        /**
         * Check if attribute value matches filter criteria.
         *
         * @param actualValue attribute value from span
         * @return true if matches
         */
        public abstract boolean matches(String actualValue);

        /**
         * Get filter description for logging.
         *
         * @return human-readable filter description
         */
        public abstract String getDescription();
    }

    /**
     * Exact match attribute filter.
     */
    public static final class ExactAttributeFilter extends AttributeFilter {
        private final String expectedValue;

        public ExactAttributeFilter(String expectedValue) {
            this.expectedValue = Objects.requireNonNull(expectedValue, "expectedValue required");
        }

        @Override
        public boolean matches(String actualValue) {
            return expectedValue.equals(actualValue);
        }

        @Override
        public String getDescription() {
            return "= \"" + expectedValue + "\"";
        }
    }

    /**
     * Contains (substring) attribute filter.
     */
    public static final class ContainsAttributeFilter extends AttributeFilter {
        private final String substring;

        public ContainsAttributeFilter(String substring) {
            if (substring == null || substring.isBlank()) {
                throw new IllegalArgumentException("substring must be non-blank");
            }
            this.substring = substring;
        }

        @Override
        public boolean matches(String actualValue) {
            return actualValue != null && actualValue.contains(substring);
        }

        @Override
        public String getDescription() {
            return "CONTAINS \"" + substring + "\"";
        }
    }

    /**
     * Regex pattern attribute filter.
     */
    public static final class RegexAttributeFilter extends AttributeFilter {
        private final Pattern pattern;
        private final String patternString;

        public RegexAttributeFilter(String regex) {
            if (regex == null || regex.isBlank()) {
                throw new IllegalArgumentException("regex must be non-blank");
            }
            try {
                this.pattern = Pattern.compile(regex);
                this.patternString = regex;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid regex: " + e.getMessage(), e);
            }
        }

        @Override
        public boolean matches(String actualValue) {
            return actualValue != null && pattern.matcher(actualValue).find();
        }

        @Override
        public String getDescription() {
            return "MATCHES /" + patternString + "/";
        }
    }

    /**
     * Concrete trace query implementation with immutable criteria.
     */
    public static final class AbstractTraceQuery implements TraceQuery {
        private final String operationNameFilter;
        private final Instant timeRangeStart;
        private final Instant timeRangeEnd;
        private final Integer spanCountMin;
        private final Integer spanCountMax;
        private final Long durationMinMs;
        private final Long durationMaxMs;
        private final Boolean errorsOnly;
        private final Map<String, AttributeFilter> attributeFilters;

        AbstractTraceQuery(
                String operationNameFilter,
                Instant timeRangeStart,
                Instant timeRangeEnd,
                Integer spanCountMin,
                Integer spanCountMax,
                Long durationMinMs,
                Long durationMaxMs,
                Boolean errorsOnly,
                Map<String, AttributeFilter> attributeFilters) {
            this.operationNameFilter = operationNameFilter;
            this.timeRangeStart = timeRangeStart;
            this.timeRangeEnd = timeRangeEnd;
            this.spanCountMin = spanCountMin;
            this.spanCountMax = spanCountMax;
            this.durationMinMs = durationMinMs;
            this.durationMaxMs = durationMaxMs;
            this.errorsOnly = errorsOnly;
            this.attributeFilters = Map.copyOf(attributeFilters);
        }

        public String getOperationNameFilter() {
            return operationNameFilter;
        }

        public Instant getTimeRangeStart() {
            return timeRangeStart;
        }

        public Instant getTimeRangeEnd() {
            return timeRangeEnd;
        }

        public Integer getSpanCountMin() {
            return spanCountMin;
        }

        public Integer getSpanCountMax() {
            return spanCountMax;
        }

        public Long getDurationMinMs() {
            return durationMinMs;
        }

        public Long getDurationMaxMs() {
            return durationMaxMs;
        }

        public Boolean getErrorsOnly() {
            return errorsOnly;
        }

        public Map<String, AttributeFilter> getAttributeFilters() {
            return attributeFilters;
        }

        @Override
        public io.activej.promise.Promise<QueryResult> execute(String tenantId, int pageSize, int pageNumber) {
            throw new UnsupportedOperationException("AbstractTraceQuery cannot be executed directly");
        }

        @Override
        public String getDescription() {
            StringBuilder sb = new StringBuilder("TraceQuery{");
            if (operationNameFilter != null) sb.append("operation=").append(operationNameFilter).append(", ");
            if (timeRangeStart != null) sb.append("timeRange=[").append(timeRangeStart).append(",").append(timeRangeEnd).append("], ");
            if (spanCountMin != null) sb.append("spanCount=[").append(spanCountMin).append(",").append(spanCountMax).append("], ");
            if (durationMinMs != null) sb.append("duration=[").append(durationMinMs).append(",").append(durationMaxMs).append("]ms, ");
            if (errorsOnly != null) sb.append("errorsOnly=").append(errorsOnly).append(", ");
            if (!attributeFilters.isEmpty()) {
                sb.append("attributes={");
                attributeFilters.forEach((name, filter) -> sb.append(name).append(":").append(filter.getDescription()).append(", "));
                sb.append("}, ");
            }
            if (sb.charAt(sb.length() - 2) == ',') {
                sb.setLength(sb.length() - 2);
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        public boolean hasFilters() {
            return operationNameFilter != null
                    || timeRangeStart != null
                    || spanCountMin != null
                    || durationMinMs != null
                    || errorsOnly != null
                    || !attributeFilters.isEmpty();
        }
    }
}
