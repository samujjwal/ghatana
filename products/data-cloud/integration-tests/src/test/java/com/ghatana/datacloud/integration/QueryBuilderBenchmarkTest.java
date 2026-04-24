/**
 * @doc.type test
 * @doc.purpose Benchmark tests for RecordQuery builder covering pagination, sort,
 *              group-by, aggregation, join simulation, export, and anomaly patterns
 * @doc.layer products
 * @doc.pattern Benchmark
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.RecordQuery;
import com.ghatana.datacloud.RecordQuery.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Benchmark tests for the {@link RecordQuery} builder and validation layer.
 *
 * <p>These tests verify correctness of all query patterns required by the data-cloud
 * platform (pagination, sort, group-by, aggregation, export, anomaly detection)
 * and measure that builder construction stays under acceptable wall-clock thresholds
 * for high-volume call sites.
 */
@DisplayName("RecordQuery builder – benchmarks and contract tests")
@Tag("performance")
class QueryBuilderBenchmarkTest {

    private static final String COLLECTION = "dc_events";
    private static final String TENANT = "tenant-bench-01";

    // ── Pagination ────────────────────────────────────────────────────────

    @Test
    @DisplayName("page() computes correct limit/offset for any page number")
    void paginationLimitOffsetCalculation() {
        int pageSize = 50;
        for (int page = 0; page < 10; page++) {
            RecordQuery q = RecordQuery.builder()
                .collectionName(COLLECTION)
                .tenantId(TENANT)
                .build()
                .page(page, pageSize);

            assertThat(q.getLimit()).isEqualTo(pageSize);
            assertThat(q.getOffset()).isEqualTo(page * pageSize);
        }
    }

    @Test
    @DisplayName("offset() pagination sets correct skip value")
    void offsetPaginationIsCorrect() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .limit(100)
            .offset(500);

        assertThat(q.getLimit()).isEqualTo(100);
        assertThat(q.getOffset()).isEqualTo(500);
    }

    @Test
    @DisplayName("continuationToken can be set for keyset pagination")
    void keysetPaginationTokenIsPreserved() {
        String token = "eyJsYXN0SWQiOiI5OTkifQ==";
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .continuationToken(token)
            .limit(100)
            .build();

        assertThat(q.getContinuationToken()).isEqualTo(token);
    }

    @Test
    @DisplayName("building 10,000 paginated queries completes within 500 ms")
    void paginationBuildThroughput() {
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            RecordQuery.builder()
                .collectionName(COLLECTION)
                .tenantId(TENANT)
                .build()
                .page(i % 100, 50);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMs)
            .as("10k paginated query builds must complete within 500 ms, took %d ms", elapsedMs)
            .isLessThan(500L);
    }

    // ── Sort ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("multi-column sort preserves insertion order")
    void multiColumnSortOrder() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .orderByDesc("createdAt")
            .orderByAsc("tenantId")
            .orderBy("score", SortDirection.DESC);

        List<SortSpec> sorts = q.getSorts();
        assertThat(sorts).hasSize(3);
        assertThat(sorts.get(0).getField()).isEqualTo("createdAt");
        assertThat(sorts.get(0).getDirection()).isEqualTo(SortDirection.DESC);
        assertThat(sorts.get(1).getField()).isEqualTo("tenantId");
        assertThat(sorts.get(1).getDirection()).isEqualTo(SortDirection.ASC);
        assertThat(sorts.get(2).getField()).isEqualTo("score");
    }

    @Test
    @DisplayName("null handling in sort defaults to NULLS_LAST")
    void sortNullHandlingDefault() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .orderByAsc("priority");

        assertThat(q.getSorts().getFirst().getNullHandling()).isEqualTo(NullHandling.NULLS_LAST);
    }

    // ── Filtering ────────────────────────────────────────────────────────

    @Test
    @DisplayName("where() with multiple conditions accumulates filters in order")
    void filterAccumulationOrder() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .where("status", Operator.EQUALS, "ACTIVE")
            .where("age", Operator.GREATER_THAN, 18)
            .where("region", Operator.IN, null); // value ignored for IN (uses values list)

        assertThat(q.getFilters()).hasSize(3);
        assertThat(q.getFilters().get(0).getField()).isEqualTo("status");
        assertThat(q.getFilters().get(1).getField()).isEqualTo("age");
    }

    @Test
    @DisplayName("whereIn() stores the multi-value list correctly")
    void whereInStoresValueList() {
        List<Object> ids = List.of("id-1", "id-2", "id-3");
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .whereIn("entityId", ids);

        assertThat(q.getFilters()).hasSize(1);
        assertThat(q.getFilters().getFirst().getOperator()).isEqualTo(Operator.IN);
        assertThat(q.getFilters().getFirst().getValues()).containsExactlyElementsOf(ids);
    }

    @Test
    @DisplayName("whereNotIn() stores the exclusion list correctly")
    void whereNotInStoresExclusionList() {
        List<Object> excluded = List.of("DELETED", "ARCHIVED");
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .whereNotIn("status", excluded);

        assertThat(q.getFilters().getFirst().getOperator()).isEqualTo(Operator.NOT_IN);
        assertThat(q.getFilters().getFirst().getValues()).containsExactlyElementsOf(excluded);
    }

    @Test
    @DisplayName("building 10,000 filtered queries completes within 500 ms")
    void filterBuildThroughput() {
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            RecordQuery.builder()
                .collectionName(COLLECTION)
                .tenantId(TENANT)
                .build()
                .where("status", Operator.EQUALS, "ACTIVE")
                .where("score", Operator.GREATER_THAN_OR_EQUALS, i)
                .orderByDesc("createdAt")
                .limit(50)
                .offset(i * 50);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMs)
            .as("10k filtered query builds must complete within 500 ms, took %d ms", elapsedMs)
            .isLessThan(500L);
    }

    // ── Group-by / Aggregation ────────────────────────────────────────────

    @Test
    @DisplayName("groupBy() with AVG aggregation is encoded correctly")
    void groupByWithAvgAggregation() {
        RecordQuery q = RecordQuery.builder()
            .collectionName("cpu_metrics")
            .tenantId(TENANT)
            .build()
            .groupBy("host", "region")
            .aggregate(AggregationType.AVG, "value")
            .bucket(TimeBucket.HOUR);

        assertThat(q.getGroupByFields()).containsExactly("host", "region");
        assertThat(q.getAggregations()).hasSize(1);
        assertThat(q.getAggregations().getFirst().getType()).isEqualTo(AggregationType.AVG);
        assertThat(q.getAggregations().getFirst().getField()).isEqualTo("value");
        assertThat(q.getTimeBucket()).isEqualTo(TimeBucket.HOUR);
    }

    @Test
    @DisplayName("multiple aggregations can be chained on a single query")
    void multipleAggregations() {
        RecordQuery q = RecordQuery.builder()
            .collectionName("orders")
            .tenantId(TENANT)
            .build()
            .groupBy("status")
            .aggregate(AggregationType.COUNT, "id", "order_count")
            .aggregate(AggregationType.SUM, "total", "revenue")
            .aggregate(AggregationType.AVG, "total", "avg_order_value")
            .aggregate(AggregationType.MAX, "total", "max_order");

        assertThat(q.getAggregations()).hasSize(4);
        assertThat(q.getAggregations().stream().map(AggregationSpec::getAlias))
            .containsExactly("order_count", "revenue", "avg_order_value", "max_order");
    }

    @Test
    @DisplayName("all AggregationType enum values are distinct")
    void aggregationTypeEnumValuesAreDistinct() {
        AggregationType[] types = AggregationType.values();
        long distinct = java.util.Arrays.stream(types).distinct().count();
        assertThat(distinct).isEqualTo(types.length);
    }

    @Test
    @DisplayName("building 5,000 aggregation queries completes within 500 ms")
    void aggregationBuildThroughput() {
        long start = System.nanoTime();
        for (int i = 0; i < 5_000; i++) {
            RecordQuery.builder()
                .collectionName("metrics")
                .tenantId(TENANT)
                .build()
                .groupBy("host", "region")
                .aggregate(AggregationType.AVG, "cpu")
                .aggregate(AggregationType.MAX, "mem")
                .bucket(TimeBucket.MINUTE)
                .limit(1000);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMs)
            .as("5k aggregation query builds must complete within 500 ms, took %d ms", elapsedMs)
            .isLessThan(500L);
    }

    // ── Time range (export / analytics) ──────────────────────────────────

    @Test
    @DisplayName("inTimeRange() stores start/end correctly and passes validation")
    void timeRangeIsStoredCorrectly() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end   = Instant.parse("2026-01-31T23:59:59Z");

        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .inTimeRange(start, end)
            .onTimeField("eventTime");

        q.validate();

        assertThat(q.getStartTime()).isEqualTo(start);
        assertThat(q.getEndTime()).isEqualTo(end);
        assertThat(q.getTimeField()).isEqualTo("eventTime");
    }

    @Test
    @DisplayName("after() and before() build a valid time-bounded range")
    void afterAndBeforeHelpers() {
        Instant now = Instant.now();
        Instant dayAgo = now.minus(Duration.ofDays(1));

        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .after(dayAgo)
            .before(now);

        q.validate();
        assertThat(q.getStartTime()).isEqualTo(dayAgo);
        assertThat(q.getEndTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("all TimeBucket enum values are present")
    void timeBucketEnumIsComplete() {
        TimeBucket[] buckets = TimeBucket.values();
        assertThat(buckets).hasSizeGreaterThanOrEqualTo(10);
        assertThat(buckets).contains(
            TimeBucket.SECOND, TimeBucket.MINUTE, TimeBucket.HOUR,
            TimeBucket.DAY, TimeBucket.WEEK, TimeBucket.MONTH, TimeBucket.YEAR
        );
    }

    // ── Projection (export use-case) ───────────────────────────────────────

    @Test
    @DisplayName("select() stores exactly the specified projection fields")
    void projectionFieldsAreStored() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .select("id", "name", "email", "createdAt");

        assertThat(q.getProjections())
            .containsExactly("id", "name", "email", "createdAt");
    }

    // ── Full-text search ──────────────────────────────────────────────────

    @Test
    @DisplayName("search() + searchIn() store text and field list correctly")
    void fullTextSearchFields() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build()
            .search("anomaly detection")
            .searchIn("description", "tags", "summary");

        assertThat(q.getSearchText()).isEqualTo("anomaly detection");
        assertThat(q.getSearchFields()).containsExactly("description", "tags", "summary");
    }

    // ── Anomaly detection query pattern ───────────────────────────────────

    @Test
    @DisplayName("anomaly scan query: time-range + percentile aggregation + sort is buildable")
    void anomalyScanQueryPattern() {
        Instant windowStart = Instant.now().minus(Duration.ofHours(1));
        Instant windowEnd   = Instant.now();

        RecordQuery q = RecordQuery.builder()
            .collectionName("anomaly_signals")
            .tenantId(TENANT)
            .build()
            .inTimeRange(windowStart, windowEnd)
            .where("severity", Operator.GREATER_THAN_OR_EQUALS, "MEDIUM")
            .groupBy("entity_type", "source")
            .aggregate(AggregationType.COUNT, "id", "signal_count")
            .aggregate(AggregationType.PERCENTILE, "score", "p99_score")
            .orderByDesc("signal_count")
            .limit(200);

        q.validate();

        assertThat(q.getAggregations()).hasSize(2);
        assertThat(q.getFilters()).hasSize(1);
        assertThat(q.getGroupByFields()).containsExactly("entity_type", "source");
        assertThat(q.getLimit()).isEqualTo(200);
    }

    // ── Entity scan ────────────────────────────────────────────────────────

    @Test
    @DisplayName("entity scan with DISTINCT_COUNT and STDDEV aggregations is buildable")
    void entityScanAggregationQuery() {
        RecordQuery q = RecordQuery.builder()
            .collectionName("entities")
            .tenantId(TENANT)
            .build()
            .groupBy("category")
            .aggregate(AggregationType.DISTINCT_COUNT, "owner_id", "unique_owners")
            .aggregate(AggregationType.STDDEV, "risk_score", "risk_stddev")
            .aggregate(AggregationType.VARIANCE, "risk_score", "risk_variance")
            .orderByDesc("unique_owners")
            .limit(50);

        q.validate();

        assertThat(q.getAggregations()).hasSize(3);
        assertThat(q.getAggregations().stream().map(AggregationSpec::getType))
            .containsExactly(
                AggregationType.DISTINCT_COUNT,
                AggregationType.STDDEV,
                AggregationType.VARIANCE
            );
    }

    // ── Validation contract ───────────────────────────────────────────────

    @Test
    @DisplayName("validate() throws for missing collection name")
    void validateThrowsForBlankCollectionName() {
        RecordQuery q = RecordQuery.builder().tenantId(TENANT).build();

        assertThatThrownBy(q::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Collection name is required");
    }

    @Test
    @DisplayName("validate() throws for negative limit")
    void validateThrowsForNegativeLimit() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .limit(-1)
            .build();

        assertThatThrownBy(q::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit cannot be negative");
    }

    @Test
    @DisplayName("validate() throws when startTime is after endTime")
    void validateThrowsWhenStartAfterEnd() {
        Instant now = Instant.now();
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .startTime(now.plusSeconds(10))
            .endTime(now)
            .build();

        assertThatThrownBy(q::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Start time must be before end time");
    }

    @Test
    @DisplayName("validate() throws when startOffset is greater than endOffset")
    void validateThrowsWhenStartOffsetGreaterThanEndOffset() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .startOffset(100L)
            .endOffset(50L)
            .build();

        assertThatThrownBy(q::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Start offset must be less than end offset");
    }

    // ── Large query construction ───────────────────────────────────────────

    @Test
    @DisplayName("building a query with 50 filter conditions completes without error")
    void largeFilterListIsHandledCorrectly() {
        RecordQuery q = RecordQuery.builder()
            .collectionName(COLLECTION)
            .tenantId(TENANT)
            .build();

        for (int i = 0; i < 50; i++) {
            q.where("field_" + i, Operator.EQUALS, "value_" + i);
        }

        q.validate();
        assertThat(q.getFilters()).hasSize(50);
    }

    @Test
    @DisplayName("building 20,000 mixed queries (filter+sort+agg) completes within 1 second")
    void mixedQueryBuildThroughput() {
        long start = System.nanoTime();
        for (int i = 0; i < 20_000; i++) {
            RecordQuery.builder()
                .collectionName(COLLECTION)
                .tenantId(TENANT)
                .build()
                .where("status", Operator.EQUALS, "ACTIVE")
                .where("score", Operator.GREATER_THAN, i % 100)
                .orderByDesc("createdAt")
                .groupBy("category")
                .aggregate(AggregationType.COUNT, "id")
                .limit(25)
                .page(i % 10, 25);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMs)
            .as("20k mixed query builds must complete within 1000 ms, took %d ms", elapsedMs)
            .isLessThan(1_000L);
    }
}
