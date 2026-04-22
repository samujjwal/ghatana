package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RecordQuery} fluent builder and validation.
 */
@DisplayName("RecordQuery [GH-90000]")
class RecordQueryTest {

    // ── where / filter ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("where() [GH-90000]")
    class Where {

        @Test
        void addsFilterCondition() { // GH-90000
            RecordQuery q = new RecordQuery() // GH-90000
                    .where("status", RecordQuery.Operator.EQUALS, "active"); // GH-90000

            assertThat(q.getFilters()).hasSize(1); // GH-90000
            RecordQuery.FilterCondition f = q.getFilters().get(0); // GH-90000
            assertThat(f.getField()).isEqualTo("status [GH-90000]");
            assertThat(f.getOperator()).isEqualTo(RecordQuery.Operator.EQUALS); // GH-90000
            assertThat(f.getValue()).isEqualTo("active [GH-90000]");
        }

        @Test
        void supportsChaining() { // GH-90000
            RecordQuery q = new RecordQuery() // GH-90000
                    .where("age", RecordQuery.Operator.GREATER_THAN, 18) // GH-90000
                    .where("active", RecordQuery.Operator.EQUALS, true); // GH-90000

            assertThat(q.getFilters()).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("whereIn() / whereNotIn() [GH-90000]")
    class WhereInOut {

        @Test
        void whereInSetsInOperatorAndValues() { // GH-90000
            List<Object> ids = List.of("a", "b", "c"); // GH-90000
            RecordQuery q = new RecordQuery().whereIn("id", ids); // GH-90000

            assertThat(q.getFilters()).hasSize(1); // GH-90000
            RecordQuery.FilterCondition f = q.getFilters().get(0); // GH-90000
            assertThat(f.getOperator()).isEqualTo(RecordQuery.Operator.IN); // GH-90000
            assertThat(f.getValues()).isEqualTo(ids); // GH-90000
        }

        @Test
        void whereNotInSetsNotInOperatorAndValues() { // GH-90000
            List<Object> excluded = List.of("x", "y"); // GH-90000
            RecordQuery q = new RecordQuery().whereNotIn("category", excluded); // GH-90000

            assertThat(q.getFilters()).hasSize(1); // GH-90000
            RecordQuery.FilterCondition f = q.getFilters().get(0); // GH-90000
            assertThat(f.getOperator()).isEqualTo(RecordQuery.Operator.NOT_IN); // GH-90000
            assertThat(f.getValues()).isEqualTo(excluded); // GH-90000
        }
    }

    // ── ordering ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("orderBy() / orderByAsc() / orderByDesc() [GH-90000]")
    class Ordering {

        @Test
        void orderByAddsSortSpec() { // GH-90000
            RecordQuery q = new RecordQuery() // GH-90000
                    .orderBy("createdAt", RecordQuery.SortDirection.DESC); // GH-90000

            assertThat(q.getSorts()).hasSize(1); // GH-90000
            RecordQuery.SortSpec sort = q.getSorts().get(0); // GH-90000
            assertThat(sort.getField()).isEqualTo("createdAt [GH-90000]");
            assertThat(sort.getDirection()).isEqualTo(RecordQuery.SortDirection.DESC); // GH-90000
        }

        @Test
        void orderByAscUsesAscDirection() { // GH-90000
            RecordQuery q = new RecordQuery().orderByAsc("name [GH-90000]");

            assertThat(q.getSorts()).hasSize(1); // GH-90000
            assertThat(q.getSorts().get(0).getDirection()).isEqualTo(RecordQuery.SortDirection.ASC); // GH-90000
        }

        @Test
        void orderByDescUsesDescDirection() { // GH-90000
            RecordQuery q = new RecordQuery().orderByDesc("name [GH-90000]");

            assertThat(q.getSorts()).hasSize(1); // GH-90000
            assertThat(q.getSorts().get(0).getDirection()).isEqualTo(RecordQuery.SortDirection.DESC); // GH-90000
        }

        @Test
        void multipleSortSpecsAllowed() { // GH-90000
            RecordQuery q = new RecordQuery() // GH-90000
                    .orderByDesc("score [GH-90000]")
                    .orderByAsc("name [GH-90000]");

            assertThat(q.getSorts()).hasSize(2); // GH-90000
        }
    }

    // ── projection / pagination ───────────────────────────────────────────────

    @Nested
    @DisplayName("select() / limit() / offset() / page() [GH-90000]")
    class ProjectionPagination {

        @Test
        void selectSetsProjectionFields() { // GH-90000
            RecordQuery q = new RecordQuery().select("id", "name", "email"); // GH-90000

            assertThat(q.getProjections()).containsExactly("id", "name", "email"); // GH-90000
        }

        @Test
        void limitSetsMaxRecords() { // GH-90000
            RecordQuery q = new RecordQuery().limit(50); // GH-90000

            assertThat(q.getLimit()).isEqualTo(50); // GH-90000
        }

        @Test
        void offsetSetsSkipCount() { // GH-90000
            RecordQuery q = new RecordQuery().offset(20); // GH-90000

            assertThat(q.getOffset()).isEqualTo(20); // GH-90000
        }

        @Test
        void pageComputesLimitAndOffset() { // GH-90000
            RecordQuery q = new RecordQuery().page(3, 25); // GH-90000

            assertThat(q.getLimit()).isEqualTo(25); // GH-90000
            assertThat(q.getOffset()).isEqualTo(75); // 3 * 25 // GH-90000
        }

        @Test
        void page0IsFirstPage() { // GH-90000
            RecordQuery q = new RecordQuery().page(0, 10); // GH-90000

            assertThat(q.getOffset()).isEqualTo(0); // GH-90000
            assertThat(q.getLimit()).isEqualTo(10); // GH-90000
        }
    }

    // ── time range ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inTimeRange() / after() / before() / onTimeField() [GH-90000]")
    class TimeRange {

        @Test
        void inTimeRangeSetsStartAndEnd() { // GH-90000
            Instant start = Instant.parse("2025-01-01T00:00:00Z [GH-90000]");
            Instant end   = Instant.parse("2025-12-31T23:59:59Z [GH-90000]");
            RecordQuery q = new RecordQuery().inTimeRange(start, end); // GH-90000

            assertThat(q.getStartTime()).isEqualTo(start); // GH-90000
            assertThat(q.getEndTime()).isEqualTo(end); // GH-90000
        }

        @Test
        void afterSetsStartTime() { // GH-90000
            Instant t = Instant.parse("2025-06-01T00:00:00Z [GH-90000]");
            RecordQuery q = new RecordQuery().after(t); // GH-90000

            assertThat(q.getStartTime()).isEqualTo(t); // GH-90000
            assertThat(q.getEndTime()).isNull(); // GH-90000
        }

        @Test
        void beforeSetsEndTime() { // GH-90000
            Instant t = Instant.parse("2025-06-01T00:00:00Z [GH-90000]");
            RecordQuery q = new RecordQuery().before(t); // GH-90000

            assertThat(q.getEndTime()).isEqualTo(t); // GH-90000
            assertThat(q.getStartTime()).isNull(); // GH-90000
        }

        @Test
        void onTimeFieldSetsTimeField() { // GH-90000
            RecordQuery q = new RecordQuery().onTimeField("eventTimestamp [GH-90000]");

            assertThat(q.getTimeField()).isEqualTo("eventTimestamp [GH-90000]");
        }
    }

    // ── stream / offset ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("inStream() / inPartitions() / fromOffset() / toOffset() [GH-90000]")
    class StreamOffset {

        @Test
        void inStreamSetsStreamName() { // GH-90000
            RecordQuery q = new RecordQuery().inStream("orders [GH-90000]");

            assertThat(q.getStreamName()).isEqualTo("orders [GH-90000]");
        }

        @Test
        void inPartitionsSetsPartitionIds() { // GH-90000
            RecordQuery q = new RecordQuery().inPartitions(0, 1, 2); // GH-90000

            assertThat(q.getPartitionIds()).containsExactly(0, 1, 2); // GH-90000
        }

        @Test
        void fromOffsetSetsStartOffset() { // GH-90000
            RecordQuery q = new RecordQuery().fromOffset(1000L); // GH-90000

            assertThat(q.getStartOffset()).isEqualTo(1000L); // GH-90000
        }

        @Test
        void toOffsetSetsEndOffset() { // GH-90000
            RecordQuery q = new RecordQuery().toOffset(2000L); // GH-90000

            assertThat(q.getEndOffset()).isEqualTo(2000L); // GH-90000
        }
    }

    // ── aggregation ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("groupBy() / aggregate() / bucket() [GH-90000]")
    class Aggregation {

        @Test
        void groupBySetsGroupByFields() { // GH-90000
            RecordQuery q = new RecordQuery().groupBy("country", "city"); // GH-90000

            assertThat(q.getGroupByFields()).containsExactly("country", "city"); // GH-90000
        }

        @Test
        void aggregateWithAutoAlias() { // GH-90000
            RecordQuery q = new RecordQuery() // GH-90000
                    .aggregate(RecordQuery.AggregationType.COUNT, "id"); // GH-90000

            assertThat(q.getAggregations()).hasSize(1); // GH-90000
            RecordQuery.AggregationSpec agg = q.getAggregations().get(0); // GH-90000
            assertThat(agg.getType()).isEqualTo(RecordQuery.AggregationType.COUNT); // GH-90000
            assertThat(agg.getField()).isEqualTo("id [GH-90000]");
            assertThat(agg.getAlias()).isEqualTo("count_id [GH-90000]");
        }

        @Test
        void aggregateWithCustomAlias() { // GH-90000
            RecordQuery q = new RecordQuery() // GH-90000
                    .aggregate(RecordQuery.AggregationType.SUM, "amount", "total"); // GH-90000

            assertThat(q.getAggregations()).hasSize(1); // GH-90000
            assertThat(q.getAggregations().get(0).getAlias()).isEqualTo("total [GH-90000]");
        }

        @Test
        void bucketSetsTimeBucket() { // GH-90000
            RecordQuery q = new RecordQuery().bucket(RecordQuery.TimeBucket.HOUR); // GH-90000

            assertThat(q.getTimeBucket()).isEqualTo(RecordQuery.TimeBucket.HOUR); // GH-90000
        }
    }

    // ── full-text search ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("search() / searchIn() [GH-90000]")
    class FullTextSearch {

        @Test
        void searchSetsSearchText() { // GH-90000
            RecordQuery q = new RecordQuery().search("java async [GH-90000]");

            assertThat(q.getSearchText()).isEqualTo("java async [GH-90000]");
        }

        @Test
        void searchInSetsSearchFields() { // GH-90000
            RecordQuery q = new RecordQuery().searchIn("title", "body"); // GH-90000

            assertThat(q.getSearchFields()).containsExactly("title", "body"); // GH-90000
        }
    }

    // ── validate() ──────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("validate() [GH-90000]")
    class Validate {

        @Test
        void throwsWhenCollectionNameIsNull() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            assertThatIllegalArgumentException().isThrownBy(q::validate) // GH-90000
                    .withMessageContaining("Collection name is required [GH-90000]");
        }

        @Test
        void throwsWhenCollectionNameIsBlank() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            q.setCollectionName("    [GH-90000]");
            assertThatIllegalArgumentException().isThrownBy(q::validate); // GH-90000
        }

        @Test
        void throwsWhenLimitIsNegative() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            q.setCollectionName("events [GH-90000]");
            q.setLimit(-1); // GH-90000
            assertThatIllegalArgumentException().isThrownBy(q::validate) // GH-90000
                    .withMessageContaining("Limit cannot be negative [GH-90000]");
        }

        @Test
        void throwsWhenOffsetIsNegative() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            q.setCollectionName("events [GH-90000]");
            q.setOffset(-1); // GH-90000
            assertThatIllegalArgumentException().isThrownBy(q::validate); // GH-90000
        }

        @Test
        void throwsWhenStartTimeIsAfterEndTime() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            q.setCollectionName("events [GH-90000]");
            q.setStartTime(Instant.parse("2025-12-01T00:00:00Z [GH-90000]"));
            q.setEndTime(Instant.parse("2025-01-01T00:00:00Z [GH-90000]"));
            assertThatIllegalArgumentException().isThrownBy(q::validate) // GH-90000
                    .withMessageContaining("Start time must be before end time [GH-90000]");
        }

        @Test
        void throwsWhenStartOffsetIsGreaterThanEndOffset() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            q.setCollectionName("events [GH-90000]");
            q.setStartOffset(500L); // GH-90000
            q.setEndOffset(100L); // GH-90000
            assertThatIllegalArgumentException().isThrownBy(q::validate) // GH-90000
                    .withMessageContaining("Start offset must be less than end offset [GH-90000]");
        }

        @Test
        void passesWithValidQuery() { // GH-90000
            RecordQuery q = new RecordQuery(); // GH-90000
            q.setCollectionName("orders [GH-90000]");
            assertThatNoException().isThrownBy(q::validate); // GH-90000
        }
    }

    // ── enum completeness ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Operator enum has all expected values [GH-90000]")
    void operatorEnumValues() { // GH-90000
        assertThat(RecordQuery.Operator.values()).hasSize(17); // GH-90000
        assertThat(RecordQuery.Operator.EQUALS).isNotNull(); // GH-90000
        assertThat(RecordQuery.Operator.REGEX).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("AggregationType enum has all expected values [GH-90000]")
    void aggregationTypeValues() { // GH-90000
        assertThat(RecordQuery.AggregationType.values()) // GH-90000
                .contains( // GH-90000
                        RecordQuery.AggregationType.COUNT,
                        RecordQuery.AggregationType.SUM,
                        RecordQuery.AggregationType.AVG,
                        RecordQuery.AggregationType.MIN,
                        RecordQuery.AggregationType.MAX,
                        RecordQuery.AggregationType.DISTINCT_COUNT);
    }

    @Test
    @DisplayName("TimeBucket enum has all expected values [GH-90000]")
    void timeBucketValues() { // GH-90000
        assertThat(RecordQuery.TimeBucket.values()) // GH-90000
                .contains( // GH-90000
                        RecordQuery.TimeBucket.MINUTE,
                        RecordQuery.TimeBucket.HOUR,
                        RecordQuery.TimeBucket.DAY,
                        RecordQuery.TimeBucket.MONTH,
                        RecordQuery.TimeBucket.YEAR);
    }
}
