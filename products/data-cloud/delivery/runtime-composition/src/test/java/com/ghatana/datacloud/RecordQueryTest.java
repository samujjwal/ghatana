package com.ghatana.datacloud;

import com.ghatana.datacloud.entity.storage.FilterCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RecordQuery} fluent builder and validation.
 */
@DisplayName("RecordQuery")
class RecordQueryTest {

    // ── where / filter ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("where()")
    class Where {

        @Test
        void addsFilterCondition() { 
            RecordQuery q = new RecordQuery() 
                    .where("status", FilterCriteria.Operator.EQ, "active"); 

            assertThat(q.getFilters()).hasSize(1); 
            RecordQuery.FilterCondition f = q.getFilters().get(0); 
            assertThat(f.getField()).isEqualTo("status");
            assertThat(f.getOperator()).isEqualTo(FilterCriteria.Operator.EQ); 
            assertThat(f.getValue()).isEqualTo("active");
        }

        @Test
        void supportsChaining() { 
            RecordQuery q = new RecordQuery() 
                    .where("age", FilterCriteria.Operator.GT, 18) 
                    .where("active", FilterCriteria.Operator.EQ, true); 

            assertThat(q.getFilters()).hasSize(2); 
        }
    }

    @Nested
    @DisplayName("whereIn() / whereNotIn()")
    class WhereInOut {

        @Test
        void whereInSetsInOperatorAndValues() { 
            List<Object> ids = List.of("a", "b", "c"); 
            RecordQuery q = new RecordQuery().whereIn("id", ids); 

            assertThat(q.getFilters()).hasSize(1); 
            RecordQuery.FilterCondition f = q.getFilters().get(0); 
            assertThat(f.getOperator()).isEqualTo(FilterCriteria.Operator.IN); 
            assertThat(f.getValues()).isEqualTo(ids); 
        }

        @Test
        void whereNotInSetsNotInOperatorAndValues() { 
            List<Object> excluded = List.of("x", "y"); 
            RecordQuery q = new RecordQuery().whereNotIn("category", excluded); 

            assertThat(q.getFilters()).hasSize(1); 
            RecordQuery.FilterCondition f = q.getFilters().get(0); 
            assertThat(f.getOperator()).isEqualTo(FilterCriteria.Operator.NOT_IN); 
            assertThat(f.getValues()).isEqualTo(excluded); 
        }
    }

    // ── ordering ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("orderBy() / orderByAsc() / orderByDesc()")
    class Ordering {

        @Test
        void orderByAddsSortSpec() { 
            RecordQuery q = new RecordQuery() 
                    .orderBy("createdAt", RecordQuery.SortDirection.DESC); 

            assertThat(q.getSorts()).hasSize(1); 
            RecordQuery.SortSpec sort = q.getSorts().get(0); 
            assertThat(sort.getField()).isEqualTo("createdAt");
            assertThat(sort.getDirection()).isEqualTo(RecordQuery.SortDirection.DESC); 
        }

        @Test
        void orderByAscUsesAscDirection() { 
            RecordQuery q = new RecordQuery().orderByAsc("name");

            assertThat(q.getSorts()).hasSize(1); 
            assertThat(q.getSorts().get(0).getDirection()).isEqualTo(RecordQuery.SortDirection.ASC); 
        }

        @Test
        void orderByDescUsesDescDirection() { 
            RecordQuery q = new RecordQuery().orderByDesc("name");

            assertThat(q.getSorts()).hasSize(1); 
            assertThat(q.getSorts().get(0).getDirection()).isEqualTo(RecordQuery.SortDirection.DESC); 
        }

        @Test
        void multipleSortSpecsAllowed() { 
            RecordQuery q = new RecordQuery() 
                    .orderByDesc("score")
                    .orderByAsc("name");

            assertThat(q.getSorts()).hasSize(2); 
        }
    }

    // ── projection / pagination ───────────────────────────────────────────────

    @Nested
    @DisplayName("select() / limit() / offset() / page()")
    class ProjectionPagination {

        @Test
        void selectSetsProjectionFields() { 
            RecordQuery q = new RecordQuery().select("id", "name", "email"); 

            assertThat(q.getProjections()).containsExactly("id", "name", "email"); 
        }

        @Test
        void limitSetsMaxRecords() { 
            RecordQuery q = new RecordQuery().limit(50); 

            assertThat(q.getLimit()).isEqualTo(50); 
        }

        @Test
        void offsetSetsSkipCount() { 
            RecordQuery q = new RecordQuery().offset(20); 

            assertThat(q.getOffset()).isEqualTo(20); 
        }

        @Test
        void pageComputesLimitAndOffset() { 
            RecordQuery q = new RecordQuery().page(3, 25); 

            assertThat(q.getLimit()).isEqualTo(25); 
            assertThat(q.getOffset()).isEqualTo(75); // 3 * 25 
        }

        @Test
        void page0IsFirstPage() { 
            RecordQuery q = new RecordQuery().page(0, 10); 

            assertThat(q.getOffset()).isEqualTo(0); 
            assertThat(q.getLimit()).isEqualTo(10); 
        }
    }

    // ── time range ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inTimeRange() / after() / before() / onTimeField()")
    class TimeRange {

        @Test
        void inTimeRangeSetsStartAndEnd() { 
            Instant start = Instant.parse("2025-01-01T00:00:00Z");
            Instant end   = Instant.parse("2025-12-31T23:59:59Z");
            RecordQuery q = new RecordQuery().inTimeRange(start, end); 

            assertThat(q.getStartTime()).isEqualTo(start); 
            assertThat(q.getEndTime()).isEqualTo(end); 
        }

        @Test
        void afterSetsStartTime() { 
            Instant t = Instant.parse("2025-06-01T00:00:00Z");
            RecordQuery q = new RecordQuery().after(t); 

            assertThat(q.getStartTime()).isEqualTo(t); 
            assertThat(q.getEndTime()).isNull(); 
        }

        @Test
        void beforeSetsEndTime() { 
            Instant t = Instant.parse("2025-06-01T00:00:00Z");
            RecordQuery q = new RecordQuery().before(t); 

            assertThat(q.getEndTime()).isEqualTo(t); 
            assertThat(q.getStartTime()).isNull(); 
        }

        @Test
        void onTimeFieldSetsTimeField() { 
            RecordQuery q = new RecordQuery().onTimeField("eventTimestamp");

            assertThat(q.getTimeField()).isEqualTo("eventTimestamp");
        }
    }

    // ── stream / offset ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("inStream() / inPartitions() / fromOffset() / toOffset()")
    class StreamOffset {

        @Test
        void inStreamSetsStreamName() { 
            RecordQuery q = new RecordQuery().inStream("orders");

            assertThat(q.getStreamName()).isEqualTo("orders");
        }

        @Test
        void inPartitionsSetsPartitionIds() { 
            RecordQuery q = new RecordQuery().inPartitions(0, 1, 2); 

            assertThat(q.getPartitionIds()).containsExactly(0, 1, 2); 
        }

        @Test
        void fromOffsetSetsStartOffset() { 
            RecordQuery q = new RecordQuery().fromOffset(1000L); 

            assertThat(q.getStartOffset()).isEqualTo(1000L); 
        }

        @Test
        void toOffsetSetsEndOffset() { 
            RecordQuery q = new RecordQuery().toOffset(2000L); 

            assertThat(q.getEndOffset()).isEqualTo(2000L); 
        }
    }

    // ── aggregation ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("groupBy() / aggregate() / bucket()")
    class Aggregation {

        @Test
        void groupBySetsGroupByFields() { 
            RecordQuery q = new RecordQuery().groupBy("country", "city"); 

            assertThat(q.getGroupByFields()).containsExactly("country", "city"); 
        }

        @Test
        void aggregateWithAutoAlias() { 
            RecordQuery q = new RecordQuery() 
                    .aggregate(RecordQuery.AggregationType.COUNT, "id"); 

            assertThat(q.getAggregations()).hasSize(1); 
            RecordQuery.AggregationSpec agg = q.getAggregations().get(0); 
            assertThat(agg.getType()).isEqualTo(RecordQuery.AggregationType.COUNT); 
            assertThat(agg.getField()).isEqualTo("id");
            assertThat(agg.getAlias()).isEqualTo("count_id");
        }

        @Test
        void aggregateWithCustomAlias() { 
            RecordQuery q = new RecordQuery() 
                    .aggregate(RecordQuery.AggregationType.SUM, "amount", "total"); 

            assertThat(q.getAggregations()).hasSize(1); 
            assertThat(q.getAggregations().get(0).getAlias()).isEqualTo("total");
        }

        @Test
        void bucketSetsTimeBucket() { 
            RecordQuery q = new RecordQuery().bucket(RecordQuery.TimeBucket.HOUR); 

            assertThat(q.getTimeBucket()).isEqualTo(RecordQuery.TimeBucket.HOUR); 
        }
    }

    // ── full-text search ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("search() / searchIn()")
    class FullTextSearch {

        @Test
        void searchSetsSearchText() { 
            RecordQuery q = new RecordQuery().search("java async");

            assertThat(q.getSearchText()).isEqualTo("java async");
        }

        @Test
        void searchInSetsSearchFields() { 
            RecordQuery q = new RecordQuery().searchIn("title", "body"); 

            assertThat(q.getSearchFields()).containsExactly("title", "body"); 
        }
    }

    // ── validate() ──────────────────────────────────────────────────────────── 

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        void throwsWhenCollectionNameIsNull() { 
            RecordQuery q = new RecordQuery(); 
            assertThatIllegalArgumentException().isThrownBy(q::validate) 
                    .withMessageContaining("Collection name is required");
        }

        @Test
        void throwsWhenCollectionNameIsBlank() { 
            RecordQuery q = new RecordQuery(); 
            q.setCollectionName("   ");
            assertThatIllegalArgumentException().isThrownBy(q::validate); 
        }

        @Test
        void throwsWhenLimitIsNegative() { 
            RecordQuery q = new RecordQuery(); 
            q.setCollectionName("events");
            q.setLimit(-1); 
            assertThatIllegalArgumentException().isThrownBy(q::validate) 
                    .withMessageContaining("Limit cannot be negative");
        }

        @Test
        void throwsWhenOffsetIsNegative() { 
            RecordQuery q = new RecordQuery(); 
            q.setCollectionName("events");
            q.setOffset(-1); 
            assertThatIllegalArgumentException().isThrownBy(q::validate); 
        }

        @Test
        void throwsWhenStartTimeIsAfterEndTime() { 
            RecordQuery q = new RecordQuery(); 
            q.setCollectionName("events");
            q.setStartTime(Instant.parse("2025-12-01T00:00:00Z"));
            q.setEndTime(Instant.parse("2025-01-01T00:00:00Z"));
            assertThatIllegalArgumentException().isThrownBy(q::validate) 
                    .withMessageContaining("Start time must be before end time");
        }

        @Test
        void throwsWhenStartOffsetIsGreaterThanEndOffset() { 
            RecordQuery q = new RecordQuery(); 
            q.setCollectionName("events");
            q.setStartOffset(500L); 
            q.setEndOffset(100L); 
            assertThatIllegalArgumentException().isThrownBy(q::validate) 
                    .withMessageContaining("Start offset must be less than end offset");
        }

        @Test
        void passesWithValidQuery() { 
            RecordQuery q = new RecordQuery(); 
            q.setCollectionName("orders");
            assertThatNoException().isThrownBy(q::validate); 
        }
    }

    // ── enum completeness ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Operator enum has all expected values")
    void operatorEnumValues() { 
        assertThat(FilterCriteria.Operator.values()).hasSizeGreaterThan(0); 
        assertThat(FilterCriteria.Operator.EQ).isNotNull(); 
        assertThat(FilterCriteria.Operator.REGEX).isNotNull(); 
    }

    @Test
    @DisplayName("AggregationType enum has all expected values")
    void aggregationTypeValues() { 
        assertThat(RecordQuery.AggregationType.values()) 
                .contains( 
                        RecordQuery.AggregationType.COUNT,
                        RecordQuery.AggregationType.SUM,
                        RecordQuery.AggregationType.AVG,
                        RecordQuery.AggregationType.MIN,
                        RecordQuery.AggregationType.MAX,
                        RecordQuery.AggregationType.DISTINCT_COUNT);
    }

    @Test
    @DisplayName("TimeBucket enum has all expected values")
    void timeBucketValues() { 
        assertThat(RecordQuery.TimeBucket.values()) 
                .contains( 
                        RecordQuery.TimeBucket.MINUTE,
                        RecordQuery.TimeBucket.HOUR,
                        RecordQuery.TimeBucket.DAY,
                        RecordQuery.TimeBucket.MONTH,
                        RecordQuery.TimeBucket.YEAR);
    }
}
