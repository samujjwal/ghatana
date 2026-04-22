package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("QuerySpec [GH-90000]")
class QuerySpecTest {

    @Test
    @DisplayName("builder defaults to empty immutable query [GH-90000]")
    void builderDefaultsToEmptyImmutableQuery() { // GH-90000
        QuerySpec query = QuerySpec.builder().build(); // GH-90000

        assertThat(query.getFilter()).isEmpty(); // GH-90000
        assertThat(query.getSortFields()).isEmpty(); // GH-90000
        assertThat(query.getLimit()).isZero(); // GH-90000
        assertThat(query.getOffset()).isZero(); // GH-90000
        assertThat(query.getProjections()).isEmpty(); // GH-90000
        assertThat(query.getMetadata()).isEmpty(); // GH-90000
        assertThat(query.hasFilters()).isFalse(); // GH-90000
        assertThat(query.hasTimeWindow()).isFalse(); // GH-90000
        assertThat(query.hasProjections()).isFalse(); // GH-90000
        assertThat(query.hasPagination()).isFalse(); // GH-90000
        assertThat(query.isPaginated()).isFalse(); // GH-90000
        assertThat(query.getFilters()).isEmpty(); // GH-90000
        assertThat(query.getSorts()).isEmpty(); // GH-90000
        assertThat(query.getCollectionId()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("builder populates filter sort projections pagination time window and metadata [GH-90000]")
    void builderPopulatesAllFields() { // GH-90000
        Instant start = Instant.parse("2026-01-01T00:00:00Z [GH-90000]");
        Instant end = Instant.parse("2026-01-31T23:59:59Z [GH-90000]");

        QuerySpec query = QuerySpec.builder() // GH-90000
                .filter("status = 'active' [GH-90000]")
                .sort("createdAt", QuerySpec.SortDirection.DESC) // GH-90000
                .sort(List.of(new QuerySpec.SortField("name", QuerySpec.SortDirection.ASC))) // GH-90000
                .limit(50) // GH-90000
                .offset(10) // GH-90000
                .projection("id", "name") // GH-90000
                .timeWindow(start, end) // GH-90000
                .collectionId("events [GH-90000]")
                .metadata("explainPlan", "true") // GH-90000
                .build(); // GH-90000

        assertThat(query.getFilter()).contains("status = 'active' [GH-90000]");
        assertThat(query.getSortFields()).hasSize(2); // GH-90000
        assertThat(query.getSortFields().get(0).toString()).isEqualTo("createdAt desc [GH-90000]");
        assertThat(query.getSortFields().get(1).toString()).isEqualTo("name asc [GH-90000]");
        assertThat(query.getLimit()).isEqualTo(50); // GH-90000
        assertThat(query.getOffset()).isEqualTo(10); // GH-90000
        assertThat(query.getProjections()).containsExactly("id", "name"); // GH-90000
        assertThat(query.getTimeWindowStart()).contains(start); // GH-90000
        assertThat(query.getTimeWindowEnd()).contains(end); // GH-90000
        assertThat(query.hasFilters()).isTrue(); // GH-90000
        assertThat(query.hasTimeWindow()).isTrue(); // GH-90000
        assertThat(query.hasProjections()).isTrue(); // GH-90000
        assertThat(query.hasPagination()).isTrue(); // GH-90000
        assertThat(query.isPaginated()).isTrue(); // GH-90000
        assertThat(query.getFilters()).containsExactly("status = 'active' [GH-90000]");
        assertThat(query.getCollectionId()).isEqualTo("events [GH-90000]");
        assertThat(query.getMetadata()).containsEntry("collectionId", "events"); // GH-90000
        assertThat(query.getMetadata()).containsEntry("explainPlan", "true"); // GH-90000
        assertThat(query.toString()).contains("filter='status = 'active'' [GH-90000]");
        assertThat(query.toString()).contains("limit=50 [GH-90000]");
    }

    @Test
    @DisplayName("builder ignores blank filter and blank collection id [GH-90000]")
    void builderIgnoresBlankInputs() { // GH-90000
        QuerySpec query = QuerySpec.builder() // GH-90000
                .filter("    [GH-90000]")
                .collectionId("  [GH-90000]")
                .build(); // GH-90000

        assertThat(query.getFilter()).isEmpty(); // GH-90000
        assertThat(query.getCollectionId()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("builder validates limit offset sort field and time window [GH-90000]")
    void builderValidatesInputs() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().limit(-1)) // GH-90000
                .withMessageContaining("Limit cannot be negative [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().limit(QuerySpec.MAX_LIMIT + 1)) // GH-90000
                .withMessageContaining("exceeds maximum allowed value [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().offset(-1)) // GH-90000
                .withMessageContaining("Offset cannot be negative [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().sort(" ", QuerySpec.SortDirection.ASC)) // GH-90000
                .withMessageContaining("Sort field name cannot be null or blank [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().timeWindow(null, Instant.now())) // GH-90000
                .withMessageContaining("cannot be null [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().timeWindow(Instant.parse("2026-02-01T00:00:00Z [GH-90000]"), Instant.parse("2026-01-01T00:00:00Z [GH-90000]")))
                .withMessageContaining("start cannot be after end [GH-90000]");
    }

    @Test
    @DisplayName("sort direction parser accepts canonical aliases and rejects invalid input [GH-90000]")
    void sortDirectionParserWorks() { // GH-90000
        assertThat(QuerySpec.SortDirection.fromString("ASC [GH-90000]")).isEqualTo(QuerySpec.SortDirection.ASC);
        assertThat(QuerySpec.SortDirection.fromString("ascending [GH-90000]")).isEqualTo(QuerySpec.SortDirection.ASC);
        assertThat(QuerySpec.SortDirection.fromString("DESC [GH-90000]")).isEqualTo(QuerySpec.SortDirection.DESC);
        assertThat(QuerySpec.SortDirection.fromString("descending [GH-90000]")).isEqualTo(QuerySpec.SortDirection.DESC);
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.SortDirection.fromString(" [GH-90000]"))
                .withMessageContaining("cannot be null or blank [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.SortDirection.fromString("sideways [GH-90000]"))
                .withMessageContaining("Unknown sort direction [GH-90000]");
    }

    @Test
    @DisplayName("sort field validates constructor arguments [GH-90000]")
    void sortFieldValidatesArguments() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> new QuerySpec.SortField("", QuerySpec.SortDirection.ASC)) // GH-90000
                .withMessageContaining("Field name cannot be null or blank [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> new QuerySpec.SortField("createdAt", null)) // GH-90000
                .withMessageContaining("Sort direction cannot be null [GH-90000]");
    }

    @Test
    @DisplayName("query collections are immutable defensive copies [GH-90000]")
    void queryCollectionsAreImmutable() { // GH-90000
        List<QuerySpec.SortField> sortFields = new ArrayList<>(); // GH-90000
        sortFields.add(new QuerySpec.SortField("createdAt", QuerySpec.SortDirection.DESC)); // GH-90000
        List<String> projections = new ArrayList<>(); // GH-90000
        projections.add("id [GH-90000]");

        QuerySpec query = QuerySpec.builder() // GH-90000
                .sort(sortFields) // GH-90000
                .projection(projections) // GH-90000
                .metadata("k", "v") // GH-90000
                .build(); // GH-90000

        sortFields.add(new QuerySpec.SortField("name", QuerySpec.SortDirection.ASC)); // GH-90000
        projections.add("name [GH-90000]");

        assertThat(query.getSortFields()).hasSize(1); // GH-90000
        assertThat(query.getProjections()).containsExactly("id [GH-90000]");
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> QuerySpec.builder().sort((String) null, QuerySpec.SortDirection.ASC)); // GH-90000
    }
}
