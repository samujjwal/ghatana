/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.query;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.DataCloudClient.Sort;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unified advanced query service for all AEP Data-Cloud collections.
 *
 * <p>Provides rich querying capabilities over patterns, pipelines, anomalies
 * and KPI snapshots with:
 * <ul>
 *   <li><b>Composable filters</b> — combine up to 8 field-level predicates (eq/ne/gt/gte/lt/lte/like)</li>
 *   <li><b>Cursor-based pagination</b> — stable {@code nextOffset} token for deep paging</li>
 *   <li><b>Multi-sort</b> — up to 4 sort fields per collection</li>
 *   <li><b>In-memory aggregation</b> — count, distinct, group-by over a single query page</li>
 *   <li><b>Cross-collection totals</b> — tenant-scoped summary across all AEP collections</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Fetch active patterns sorted by priority, page 2
 * AepQueryService.PagedResult<PatternSummary> result =
 *     queryService.queryPatterns("tenant-1",
 *         AepQueryService.QuerySpec.builder()
 *             .filter(Filter.eq("status", "ACTIVE"))
 *             .sort(Sort.desc("priority"))
 *             .page(0, 20)
 *             .build())
 *     .getResult();
 *
 * // Count anomalies by severity
 * AepQueryService.AggregateResult agg =
 *     queryService.aggregate("tenant-1", "aep_anomalies",
 *         AepQueryService.AggregateSpec.groupBy("severity", 1000))
 *     .getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified advanced query service for all AEP Data-Cloud collections
 * @doc.layer product
 * @doc.pattern Service, Facade
 */
public final class AepQueryService {

    private static final Logger log = LoggerFactory.getLogger(AepQueryService.class);

    // AEP collection names — kept as constants so callers can reference them
    public static final String COLLECTION_PATTERNS  = "aep_patterns";
    public static final String COLLECTION_PIPELINES = "aep_pipelines";
    public static final String COLLECTION_ANOMALIES = "aep_anomalies";
    public static final String COLLECTION_KPI       = "aep_kpi_snapshots";
    public static final String COLLECTION_METRICS   = "aep_metrics";

    private static final int MAX_PAGE_SIZE = 1_000;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final DataCloudClient client;
    private final Counter queryCounter;
    private final Counter errorCounter;
    private final Timer   queryTimer;

    /**
     * Creates a query service backed by the given Data-Cloud client.
     *
     * @param client        the Data-Cloud client; must not be {@code null}
     * @param meterRegistry Micrometer registry for operational metrics
     */
    public AepQueryService(DataCloudClient client, MeterRegistry meterRegistry) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
        Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
        this.queryCounter = meterRegistry.counter("aep.query.total");
        this.errorCounter = meterRegistry.counter("aep.query.errors");
        this.queryTimer   = meterRegistry.timer("aep.query.duration");
    }

    // =========================================================================
    //  Pattern Queries
    // =========================================================================

    /**
     * Queries the {@value #COLLECTION_PATTERNS} collection with the given spec.
     *
     * @param tenantId tenant identifier
     * @param spec     query specification
     * @return paged result of {@link PatternSummary} records
     */
    public Promise<PagedResult<PatternSummary>> queryPatterns(String tenantId, QuerySpec spec) {
        return queryCollection(tenantId, COLLECTION_PATTERNS, spec, AepQueryService::toPatternSummary);
    }

    /**
     * Queries the {@value #COLLECTION_PIPELINES} collection.
     *
     * @param tenantId tenant identifier
     * @param spec     query specification
     * @return paged result of {@link PipelineSummary} records
     */
    public Promise<PagedResult<PipelineSummary>> queryPipelines(String tenantId, QuerySpec spec) {
        return queryCollection(tenantId, COLLECTION_PIPELINES, spec, AepQueryService::toPipelineSummary);
    }

    /**
     * Queries the {@value #COLLECTION_ANOMALIES} collection.
     *
     * @param tenantId tenant identifier
     * @param spec     query specification
     * @return paged result of {@link AnomalySummary} records
     */
    public Promise<PagedResult<AnomalySummary>> queryAnomalies(String tenantId, QuerySpec spec) {
        return queryCollection(tenantId, COLLECTION_ANOMALIES, spec, AepQueryService::toAnomalySummary);
    }

    /**
     * Queries the {@value #COLLECTION_KPI} collection.
     *
     * @param tenantId tenant identifier
     * @param spec     query specification
     * @return paged result of {@link KpiSummary} records
     */
    public Promise<PagedResult<KpiSummary>> queryKpis(String tenantId, QuerySpec spec) {
        return queryCollection(tenantId, COLLECTION_KPI, spec, AepQueryService::toKpiSummary);
    }

    // =========================================================================
    //  Aggregation
    // =========================================================================

    /**
     * Performs in-memory aggregation over a DataCloud collection.
     *
     * <p>Fetches up to {@link AggregateSpec#sampleLimit()} entities and then
     * applies the aggregation function client-side. For large collections use
     * {@link AggregateSpec#sampleLimit()} to cap the sample.
     *
     * @param tenantId   tenant identifier
     * @param collection DataCloud collection name
     * @param spec       aggregation specification
     * @return aggregate result
     */
    public Promise<AggregateResult> aggregate(String tenantId, String collection,
                                              AggregateSpec spec) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(spec, "spec");

        Query q = Query.builder()
                .filters(spec.filters())
                .limit(Math.min(spec.sampleLimit(), MAX_PAGE_SIZE))
                .build();

        return client.query(tenantId, collection, q).map(entities -> {
            queryCounter.increment();
            return applyAggregation(entities, spec);
        }).whenException(e -> errorCounter.increment());
    }

    /**
     * Returns a cross-collection tenant summary: count of items in each AEP collection.
     *
     * @param tenantId tenant identifier
     * @return promise of tenant summary
     */
    public Promise<TenantSummary> tenantSummary(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        Query countQuery = Query.builder()
                .filter(Filter.eq("tenantId", tenantId))
                .limit(MAX_PAGE_SIZE)
                .build();

        Promise<Integer> patterns  = client.query(tenantId, COLLECTION_PATTERNS,  countQuery).map(List::size);
        Promise<Integer> pipelines = client.query(tenantId, COLLECTION_PIPELINES, countQuery).map(List::size);
        Promise<Integer> anomalies = client.query(tenantId, COLLECTION_ANOMALIES, countQuery).map(List::size);
        Promise<Integer> kpis      = client.query(tenantId, COLLECTION_KPI,       countQuery).map(List::size);

        return Promises.toList(List.of(patterns, pipelines, anomalies, kpis))
                .map(counts -> new TenantSummary(
                        tenantId,
                        counts.get(0),
                        counts.get(1),
                        counts.get(2),
                        counts.get(3),
                        Instant.now()
                ));
    }

    // =========================================================================
    //  Internal — Generic Query
    // =========================================================================

    private <T> Promise<PagedResult<T>> queryCollection(String tenantId, String collection,
                                                        QuerySpec spec,
                                                        Function<Entity, T> mapper) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(spec, "spec");

        int limit  = Math.min(spec.limit() > 0 ? spec.limit() : DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        int offset = Math.max(spec.offset(), 0);

        // Build filters — always include tenantId isolation
        List<Filter> filters = new ArrayList<>(spec.filters());
        boolean hasTenantFilter = filters.stream().anyMatch(f -> "tenantId".equals(f.field()));
        if (!hasTenantFilter) {
            filters.add(Filter.eq("tenantId", tenantId));
        }

        Query q = Query.builder()
                .filters(filters)
                .sorts(spec.sorts())
                .offset(offset)
                .limit(limit + 1)  // fetch one extra to detect hasMore
                .build();

        Instant start = Instant.now();
        return client.query(tenantId, collection, q)
                .map(entities -> {
                    queryCounter.increment();
                    queryTimer.record(Duration.between(start, Instant.now()));

                    boolean hasMore = entities.size() > limit;
                    List<Entity> page = hasMore ? entities.subList(0, limit) : entities;

                    List<T> items = page.stream().map(mapper).collect(Collectors.toList());
                    int nextOffset = hasMore ? offset + limit : -1;
                    return new PagedResult<>(items, items.size(), offset, limit, hasMore, nextOffset);
                })
                .whenException(e -> {
                    errorCounter.increment();
                    log.error("Query failed tenant={} collection={}: {}", tenantId, collection, e.getMessage(), e);
                });
    }

    // =========================================================================
    //  Internal — Aggregation Engine
    // =========================================================================

    private static AggregateResult applyAggregation(List<Entity> entities, AggregateSpec spec) {
        int totalCount = entities.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);

        // COUNT distinct
        if (spec.groupByField() != null) {
            Map<String, Long> groups = entities.stream()
                    .collect(Collectors.groupingBy(
                            e -> String.valueOf(e.data().getOrDefault(spec.groupByField(), "null")),
                            Collectors.counting()
                    ));
            result.put("groupBy", groups);
        }

        // SUM / AVG
        if (spec.sumField() != null) {
            double sum = entities.stream()
                    .mapToDouble(e -> toDouble(e.data().get(spec.sumField())))
                    .sum();
            result.put("sum", sum);
            result.put("avg", totalCount > 0 ? sum / totalCount : 0.0);
        }

        // MIN / MAX
        if (spec.minMaxField() != null) {
            OptionalDouble min = entities.stream()
                    .mapToDouble(e -> toDouble(e.data().get(spec.minMaxField())))
                    .min();
            OptionalDouble max = entities.stream()
                    .mapToDouble(e -> toDouble(e.data().get(spec.minMaxField())))
                    .max();
            result.put("min", min.orElse(Double.NaN));
            result.put("max", max.orElse(Double.NaN));
        }

        // DISTINCT values
        if (spec.distinctField() != null) {
            Set<Object> distinct = entities.stream()
                    .map(e -> e.data().get(spec.distinctField()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            result.put("distinct", distinct);
            result.put("distinctCount", distinct.size());
        }

        return new AggregateResult(totalCount, Map.copyOf(result), Instant.now());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    // =========================================================================
    //  Internal — Entity → Summary Mappers
    // =========================================================================

    private static PatternSummary toPatternSummary(Entity e) {
        Map<String, Object> d = e.data();
        return new PatternSummary(
                e.id(),
                str(d, "tenantId"),
                str(d, "name"),
                str(d, "status"),
                numInt(d, "priority"),
                str(d, "description"),
                parseInstant(d, "createdAt"),
                parseInstant(d, "updatedAt")
        );
    }

    private static PipelineSummary toPipelineSummary(Entity e) {
        Map<String, Object> d = e.data();
        return new PipelineSummary(
                e.id(),
                str(d, "tenantId"),
                str(d, "name"),
                str(d, "status"),
                numInt(d, "stageCount"),
                parseInstant(d, "createdAt"),
                parseInstant(d, "updatedAt")
        );
    }

    private static AnomalySummary toAnomalySummary(Entity e) {
        Map<String, Object> d = e.data();
        return new AnomalySummary(
                e.id(),
                str(d, "tenantId"),
                str(d, "kpiName"),
                str(d, "severity"),
                numDouble(d, "zScore"),
                parseInstant(d, "detectedAt"),
                str(d, "status")
        );
    }

    private static KpiSummary toKpiSummary(Entity e) {
        Map<String, Object> d = e.data();
        return new KpiSummary(
                e.id(),
                str(d, "tenantId"),
                str(d, "kpiName"),
                numDouble(d, "value"),
                str(d, "unit"),
                parseInstant(d, "recordedAt")
        );
    }

    private static String str(Map<String, Object> d, String key) {
        Object v = d.get(key);
        return v == null ? null : v.toString();
    }

    private static int numInt(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {} }
        return 0;
    }

    private static double numDouble(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {} }
        return 0.0;
    }

    private static Instant parseInstant(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        try { return Instant.parse(v.toString()); } catch (Exception ignored) { return null; }
    }

    // =========================================================================
    //  Public API Types
    // =========================================================================

    /**
     * Query specification for paginated DataCloud queries.
     *
     * <p>Build via {@link QuerySpec#builder()}:
     * <pre>{@code
     * QuerySpec.builder()
     *     .filter(Filter.eq("status", "ACTIVE"))
     *     .sort(Sort.desc("createdAt"))
     *     .page(0, 20)
     *     .build();
     * }</pre>
     *
     * @param filters list of field-level filters
     * @param sorts   sort fields (applied in order)
     * @param offset  zero-based page start
     * @param limit   maximum items to return (capped at 1000)
     */
    public record QuerySpec(
            List<Filter> filters,
            List<Sort>   sorts,
            int          offset,
            int          limit) {

        public QuerySpec {
            filters = filters != null ? List.copyOf(filters) : List.of();
            sorts   = sorts   != null ? List.copyOf(sorts)   : List.of();
            if (offset < 0) offset = 0;
            if (limit  < 1) limit  = DEFAULT_PAGE_SIZE;
        }

        public static Builder builder() { return new Builder(); }

        public static QuerySpec all()   { return new QuerySpec(List.of(), List.of(), 0, MAX_PAGE_SIZE); }

        public static final class Builder {
            private final List<Filter> filters = new ArrayList<>();
            private final List<Sort>   sorts   = new ArrayList<>();
            private int offset = 0;
            private int limit  = DEFAULT_PAGE_SIZE;

            public Builder filter(Filter f)  { filters.add(f); return this; }
            public Builder sort(Sort s)      { sorts.add(s);   return this; }
            public Builder offset(int n)     { this.offset = n; return this; }
            public Builder limit(int n)      { this.limit  = n; return this; }
            /** Sets offset and limit from a page index (0-based) and page size. */
            public Builder page(int pageIndex, int pageSize) {
                this.offset = pageIndex * pageSize;
                this.limit  = pageSize;
                return this;
            }
            public QuerySpec build() { return new QuerySpec(filters, sorts, offset, limit); }
        }
    }

    /**
     * Aggregation specification.
     *
     * @param filters      pre-aggregation filters
     * @param groupByField field to group by (optional)
     * @param sumField     numeric field to sum + average (optional)
     * @param minMaxField  numeric field to compute min/max (optional)
     * @param distinctField field to collect distinct values for (optional)
     * @param sampleLimit  max entities to load before aggregating (default 1000)
     */
    public record AggregateSpec(
            List<Filter> filters,
            String groupByField,
            String sumField,
            String minMaxField,
            String distinctField,
            int sampleLimit) {

        public AggregateSpec {
            filters = filters != null ? List.copyOf(filters) : List.of();
            if (sampleLimit < 1) sampleLimit = 1_000;
        }

        /** Simple group-by with a given sample size. */
        public static AggregateSpec groupBy(String field, int limit) {
            return new AggregateSpec(List.of(), field, null, null, null, limit);
        }

        /** Distinct + count for a field. */
        public static AggregateSpec distinct(String field) {
            return new AggregateSpec(List.of(), null, null, null, field, 1_000);
        }

        /** Sum + average of a numeric field. */
        public static AggregateSpec sum(String field) {
            return new AggregateSpec(List.of(), null, field, null, null, 1_000);
        }
    }

    /**
     * Paginated query result.
     *
     * @param items      items on this page
     * @param count      number of items returned (≤ limit)
     * @param offset     offset used for this page
     * @param limit      page size requested
     * @param hasMore    true when more items exist beyond this page
     * @param nextOffset offset to pass for the next page, or -1 if no more pages
     * @param <T>        summary type
     */
    public record PagedResult<T>(
            List<T> items,
            int     count,
            int     offset,
            int     limit,
            boolean hasMore,
            int     nextOffset) {}

    /**
     * Result of an aggregation operation.
     *
     * @param totalCount  entities sampled
     * @param aggregates  named result map (groupBy, sum, avg, min, max, distinct, ...)
     * @param computedAt  when the aggregation was performed
     */
    public record AggregateResult(
            int                  totalCount,
            Map<String, Object>  aggregates,
            Instant              computedAt) {}

    /**
     * Cross-collection tenant summary.
     *
     * @param tenantId       tenant identifier
     * @param patternCount   patterns in the last sample page
     * @param pipelineCount  pipelines in the last sample page
     * @param anomalyCount   anomalies in the last sample page
     * @param kpiCount       KPI snapshots in the last sample page
     * @param computedAt     timestamp; compare to detect staleness
     */
    public record TenantSummary(
            String  tenantId,
            int     patternCount,
            int     pipelineCount,
            int     anomalyCount,
            int     kpiCount,
            Instant computedAt) {}

    // ────────── Domain Summary Records ──────────

    /** Compact pattern summary for query results. */
    public record PatternSummary(
            String  id,
            String  tenantId,
            String  name,
            String  status,
            int     priority,
            String  description,
            Instant createdAt,
            Instant updatedAt) {}

    /** Compact pipeline summary for query results. */
    public record PipelineSummary(
            String  id,
            String  tenantId,
            String  name,
            String  status,
            int     stageCount,
            Instant createdAt,
            Instant updatedAt) {}

    /** Compact anomaly summary for query results. */
    public record AnomalySummary(
            String  id,
            String  tenantId,
            String  kpiName,
            String  severity,
            double  zScore,
            Instant detectedAt,
            String  status) {}

    /** Compact KPI summary for query results. */
    public record KpiSummary(
            String  id,
            String  tenantId,
            String  kpiName,
            double  value,
            String  unit,
            Instant recordedAt) {}
}
