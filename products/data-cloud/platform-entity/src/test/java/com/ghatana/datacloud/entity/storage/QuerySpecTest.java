package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("QuerySpec")
class QuerySpecTest {

    @Test
    @DisplayName("builder defaults to empty immutable query")
    void builderDefaultsToEmptyImmutableQuery() {
        QuerySpec query = QuerySpec.builder().build();

        assertThat(query.getFilter()).isEmpty();
        assertThat(query.getSortFields()).isEmpty();
        assertThat(query.getLimit()).isZero();
        assertThat(query.getOffset()).isZero();
        assertThat(query.getProjections()).isEmpty();
        assertThat(query.getMetadata()).isEmpty();
        assertThat(query.hasFilters()).isFalse();
        assertThat(query.hasTimeWindow()).isFalse();
        assertThat(query.hasProjections()).isFalse();
        assertThat(query.hasPagination()).isFalse();
        assertThat(query.isPaginated()).isFalse();
        assertThat(query.getFilters()).isEmpty();
        assertThat(query.getSorts()).isEmpty();
        assertThat(query.getCollectionId()).isNull();
    }

    @Test
    @DisplayName("builder populates filter sort projections pagination time window and metadata")
    void builderPopulatesAllFields() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-31T23:59:59Z");

        QuerySpec query = QuerySpec.builder()
                .filter("status = 'active'")
                .sort("createdAt", QuerySpec.SortDirection.DESC)
                .sort(List.of(new QuerySpec.SortField("name", QuerySpec.SortDirection.ASC)))
                .limit(50)
                .offset(10)
                .projection("id", "name")
                .timeWindow(start, end)
                .collectionId("events")
                .metadata("explainPlan", "true")
                .build();

        assertThat(query.getFilter()).contains("status = 'active'");
        assertThat(query.getSortFields()).hasSize(2);
        assertThat(query.getSortFields().get(0).toString()).isEqualTo("createdAt desc");
        assertThat(query.getSortFields().get(1).toString()).isEqualTo("name asc");
        assertThat(query.getLimit()).isEqualTo(50);
        assertThat(query.getOffset()).isEqualTo(10);
        assertThat(query.getProjections()).containsExactly("id", "name");
        assertThat(query.getTimeWindowStart()).contains(start);
        assertThat(query.getTimeWindowEnd()).contains(end);
        assertThat(query.hasFilters()).isTrue();
        assertThat(query.hasTimeWindow()).isTrue();
        assertThat(query.hasProjections()).isTrue();
        assertThat(query.hasPagination()).isTrue();
        assertThat(query.isPaginated()).isTrue();
        assertThat(query.getFilters()).containsExactly("status = 'active'");
        assertThat(query.getCollectionId()).isEqualTo("events");
        assertThat(query.getMetadata()).containsEntry("collectionId", "events");
        assertThat(query.getMetadata()).containsEntry("explainPlan", "true");
        assertThat(query.toString()).contains("filter='status = 'active''");
        assertThat(query.toString()).contains("limit=50");
    }

    @Test
    @DisplayName("builder ignores blank filter and blank collection id")
    void builderIgnoresBlankInputs() {
        QuerySpec query = QuerySpec.builder()
                .filter("   ")
                .collectionId(" ")
                .build();

        assertThat(query.getFilter()).isEmpty();
        assertThat(query.getCollectionId()).isNull();
    }

    @Test
    @DisplayName("builder validates limit offset sort field and time window")
    void builderValidatesInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().limit(-1))
                .withMessageContaining("Limit cannot be negative");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().limit(QuerySpec.MAX_LIMIT + 1))
                .withMessageContaining("exceeds maximum allowed value");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().offset(-1))
                .withMessageContaining("Offset cannot be negative");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().sort(" ", QuerySpec.SortDirection.ASC))
                .withMessageContaining("Sort field name cannot be null or blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().timeWindow(null, Instant.now()))
                .withMessageContaining("cannot be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().timeWindow(Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")))
                .withMessageContaining("start cannot be after end");
    }

    @Test
    @DisplayName("sort direction parser accepts canonical aliases and rejects invalid input")
    void sortDirectionParserWorks() {
        assertThat(QuerySpec.SortDirection.fromString("ASC")).isEqualTo(QuerySpec.SortDirection.ASC);
        assertThat(QuerySpec.SortDirection.fromString("ascending")).isEqualTo(QuerySpec.SortDirection.ASC);
        assertThat(QuerySpec.SortDirection.fromString("DESC")).isEqualTo(QuerySpec.SortDirection.DESC);
        assertThat(QuerySpec.SortDirection.fromString("descending")).isEqualTo(QuerySpec.SortDirection.DESC);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.SortDirection.fromString(""))
                .withMessageContaining("cannot be null or blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.SortDirection.fromString("sideways"))
                .withMessageContaining("Unknown sort direction");
    }

    @Test
    @DisplayName("sort field validates constructor arguments")
    void sortFieldValidatesArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QuerySpec.SortField("", QuerySpec.SortDirection.ASC))
                .withMessageContaining("Field name cannot be null or blank");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new QuerySpec.SortField("createdAt", null))
                .withMessageContaining("Sort direction cannot be null");
    }

    @Test
    @DisplayName("query collections are immutable defensive copies")
    void queryCollectionsAreImmutable() {
        List<QuerySpec.SortField> sortFields = new ArrayList<>();
        sortFields.add(new QuerySpec.SortField("createdAt", QuerySpec.SortDirection.DESC));
        List<String> projections = new ArrayList<>();
        projections.add("id");

        QuerySpec query = QuerySpec.builder()
                .sort(sortFields)
                .projection(projections)
                .metadata("k", "v")
                .build();

        sortFields.add(new QuerySpec.SortField("name", QuerySpec.SortDirection.ASC));
        projections.add("name");

        assertThat(query.getSortFields()).hasSize(1);
        assertThat(query.getProjections()).containsExactly("id");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> QuerySpec.builder().sort((String) null, QuerySpec.SortDirection.ASC));
    }
}
