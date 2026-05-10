package com.ghatana.datacloud;

import com.ghatana.datacloud.entity.storage.FilterCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecordQuery")
class RecordQueryTest {

    @Test
    @DisplayName("enum families expose expected members")
    void enumFamiliesExposeExpectedMembers() {
        assertThat(RecordQuery.LogicalOperator.values()).containsExactly(RecordQuery.LogicalOperator.AND, RecordQuery.LogicalOperator.OR);
        assertThat(RecordQuery.SortDirection.values()).containsExactly(RecordQuery.SortDirection.ASC, RecordQuery.SortDirection.DESC);
        assertThat(RecordQuery.NullHandling.values()).containsExactly(RecordQuery.NullHandling.NULLS_FIRST, RecordQuery.NullHandling.NULLS_LAST);
        assertThat(RecordQuery.AggregationType.values()).contains(RecordQuery.AggregationType.COUNT, RecordQuery.AggregationType.AVG);
        assertThat(RecordQuery.TimeBucket.values()).contains(RecordQuery.TimeBucket.MINUTE, RecordQuery.TimeBucket.DAY);
    }

    @Test
    @DisplayName("fluent API populates query state")
    void fluentApiPopulatesQueryState() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant start = now.minusSeconds(3600);

        RecordQuery query = RecordQuery.builder()
                .collectionName("events")
                .tenantId("tenant-a")
                .build()
                .where("status", FilterCriteria.Operator.EQ, "active")
                .whereIn("region", List.of((Object) "us-east", "eu-west"))
                .whereNotIn("tier", List.of((Object) "deprecated"))
                .orderByAsc("createdAt")
                .orderByDesc("priority")
                .select("id", "status")
                .limit(25)
                .offset(10)
                .inTimeRange(start, now)
                .onTimeField("createdAt")
                .inStream("audit-stream")
                .inPartitions(1, 2)
                .fromOffset(100L)
                .toOffset(250L)
                .groupBy("status")
                .aggregate(RecordQuery.AggregationType.COUNT, "id")
                .bucket(RecordQuery.TimeBucket.MINUTE)
                .search("failure")
                .searchIn("message", "details");

        assertThat(query.getCollectionName()).isEqualTo("events");
        assertThat(query.getTenantId()).isEqualTo("tenant-a");
        assertThat(query.getFilters()).hasSize(3);
        assertThat(query.getSorts()).hasSize(2);
        assertThat(query.getProjections()).containsExactly("id", "status");
        assertThat(query.getLimit()).isEqualTo(25);
        assertThat(query.getOffset()).isEqualTo(10);
        assertThat(query.getStartTime()).isEqualTo(start);
        assertThat(query.getEndTime()).isEqualTo(now);
        assertThat(query.getTimeField()).isEqualTo("createdAt");
        assertThat(query.getStreamName()).isEqualTo("audit-stream");
        assertThat(query.getPartitionIds()).containsExactly(1, 2);
        assertThat(query.getStartOffset()).isEqualTo(100L);
        assertThat(query.getEndOffset()).isEqualTo(250L);
        assertThat(query.getGroupByFields()).containsExactly("status");
        assertThat(query.getAggregations()).hasSize(1);
        assertThat(query.getTimeBucket()).isEqualTo(RecordQuery.TimeBucket.MINUTE);
        assertThat(query.getSearchText()).isEqualTo("failure");
        assertThat(query.getSearchFields()).containsExactly("message", "details");
    }

    @Test
    @DisplayName("page sets limit and offset")
    void pageSetsLimitAndOffset() {
        RecordQuery query = RecordQuery.builder()
                .collectionName("entities")
                .build()
                .page(3, 20);

        assertThat(query.getLimit()).isEqualTo(20);
        assertThat(query.getOffset()).isEqualTo(60);
    }

    @Test
    @DisplayName("validate accepts a well-formed query")
    void validateAcceptsWellFormedQuery() {
        RecordQuery query = RecordQuery.builder()
                .collectionName("entities")
                .limit(1)
                .offset(0)
                .build();

        query.validate();
    }

    @Test
    @DisplayName("validate rejects invalid query constraints")
    void validateRejectsInvalidQueryConstraints() {
        assertThatThrownBy(() -> RecordQuery.builder().build().validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collection name is required");

        assertThatThrownBy(() -> RecordQuery.builder().collectionName("c").limit(-1).build().validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit cannot be negative");

        assertThatThrownBy(() -> RecordQuery.builder().collectionName("c").offset(-1).build().validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Offset cannot be negative");

        assertThatThrownBy(() -> RecordQuery.builder()
                        .collectionName("c")
                        .build()
                        .inTimeRange(Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"))
                        .validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start time must be before end time");

        assertThatThrownBy(() -> RecordQuery.builder()
                        .collectionName("c")
                        .build()
                        .fromOffset(200L)
                        .toOffset(100L)
                        .validate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start offset must be less than end offset");
    }
}