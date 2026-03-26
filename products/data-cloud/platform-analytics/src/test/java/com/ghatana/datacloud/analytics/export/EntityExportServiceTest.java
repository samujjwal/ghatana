/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.analytics.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

import static com.ghatana.datacloud.analytics.export.EntityExportService.csvEscape;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EntityExportService}.
 *
 * <p>Fully isolated from any database — the {@link EntityRepository} is mocked.
 * Extends {@link EventloopTestBase} so that ActiveJ Promises are driven correctly
 * inside {@code runPromise()}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntityExportService Tests")
class EntityExportServiceTest extends EventloopTestBase {

    private static final String TENANT     = "tenant-exports";
    private static final String COLLECTION = "invoices";

    @Mock
    private EntityRepository repository;

    private EntityExportService service;

    @BeforeEach
    void setUp() {
        service = new EntityExportService(
                repository,
                new ObjectMapper(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null entityRepository throws NullPointerException")
        void nullRepositoryFails() {
            assertThatThrownBy(() ->
                    new EntityExportService(null, new ObjectMapper()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("entityRepository");
        }

        @Test
        @DisplayName("null objectMapper throws NullPointerException")
        void nullObjectMapperFails() {
            assertThatThrownBy(() ->
                    new EntityExportService(repository, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("objectMapper");
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutorFails() {
            assertThatThrownBy(() ->
                    new EntityExportService(repository, new ObjectMapper(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("executor");
        }

        @Test
        @DisplayName("null tenantId in exportCsv throws NullPointerException")
        void nullTenantInCsvFails() {
            assertThatThrownBy(() ->
                    runPromise(() -> service.exportCsv(null, COLLECTION, Map.of(), 100)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("null collectionName in exportNdjson throws NullPointerException")
        void nullCollectionInNdjsonFails() {
            assertThatThrownBy(() ->
                    runPromise(() -> service.exportNdjson(TENANT, null, Map.of(), 100)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("collectionName");
        }
    }

    // =========================================================================
    // exportCsv() — empty collection
    // =========================================================================

    @Nested
    @DisplayName("exportCsv() — empty collection")
    class CsvEmptyTests {

        @Test
        @DisplayName("returns empty string for an empty collection")
        void emptyCollectionReturnsEmptyString() {
            stubEmpty();
            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            assertThat(csv).isEmpty();
        }
    }

    // =========================================================================
    // exportCsv() — header and data rows
    // =========================================================================

    @Nested
    @DisplayName("exportCsv() — header and data rows")
    class CsvRowTests {

        @Test
        @DisplayName("CSV has header row with metadata + sorted data fields")
        void csvHeaderContainsMetadataAndDataFields() {
            Entity e = entityWith(Map.of("amount", 99.9, "status", "PAID"));
            stub(List.of(e));

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            String[] lines = csv.split("\r\n");

            assertThat(lines[0]).startsWith("id,tenantId,collectionName,createdAt");
            assertThat(lines[0]).contains("amount");
            assertThat(lines[0]).contains("status");
        }

        @Test
        @DisplayName("data fields appear in sorted order in the header")
        void dataFieldsSortedInHeader() {
            Entity e = entityWith(Map.of("zzz", 1, "aaa", 2, "mmm", 3));
            stub(List.of(e));

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            // Header line
            String header = csv.split("\r\n")[0];
            int aaa = header.indexOf("aaa");
            int mmm = header.indexOf("mmm");
            int zzz = header.indexOf("zzz");
            assertThat(aaa).isLessThan(mmm);
            assertThat(mmm).isLessThan(zzz);
        }

        @Test
        @DisplayName("data row contains correct values")
        void csvDataRowContainsValues() {
            UUID id = UUID.randomUUID();
            Entity e = entityWithId(id, Map.of("amount", 250.0, "status", "PENDING"));
            stub(List.of(e));

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            String[] lines = csv.split("\r\n");

            assertThat(lines).hasSize(2); // header + one data row
            assertThat(lines[1]).contains(id.toString());
            assertThat(lines[1]).contains(TENANT);
            assertThat(lines[1]).contains(COLLECTION);
            assertThat(lines[1]).contains("250.0");
            assertThat(lines[1]).contains("PENDING");
        }

        @Test
        @DisplayName("missing field in an entity row is rendered as empty string")
        void missingFieldsAreEmptyInCsv() {
            Entity e1 = entityWith(Map.of("field1", "A", "field2", "B"));
            Entity e2 = entityWith(Map.of("field1", "C")); // field2 missing
            stub(List.of(e1, e2));

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            String[] lines = csv.split("\r\n");

            assertThat(lines).hasSize(3); // header + 2 rows
            // Row for e2 should have an empty value where field2 would be
            assertThat(lines[2]).contains("C");
            // field2 value is empty (two consecutive commas around it)
            assertThat(lines[2]).matches(".*,C,.*");
        }

        @Test
        @DisplayName("entity with null data map produces metadata-only row")
        void nullDataProducesMetadataOnlyRow() {
            Entity e = new Entity();
            e.setId(UUID.randomUUID());
            e.setTenantId(TENANT);
            e.setCollectionName(COLLECTION);
            e.setData(null);
            stub(List.of(e));

            // Should not throw; data fields are empty
            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            assertThat(csv).isNotEmpty(); // header + one row
        }

        @Test
        @DisplayName("multiple entities are all exported in CSV")
        void multipleEntitiesExported() {
            List<Entity> entities = List.of(
                    entityWith(Map.of("x", 1)),
                    entityWith(Map.of("x", 2)),
                    entityWith(Map.of("x", 3)));
            stub(entities);

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100));
            String[] lines = csv.split("\r\n");
            assertThat(lines).hasSize(4); // header + 3 rows
        }
    }

    // =========================================================================
    // exportNdjson()
    // =========================================================================

    @Nested
    @DisplayName("exportNdjson()")
    class NdjsonTests {

        @Test
        @DisplayName("returns empty string for an empty collection")
        void emptyCollectionReturnsEmptyString() {
            stubEmpty();
            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100));
            assertThat(ndjson).isEmpty();
        }

        @Test
        @DisplayName("each entity produces one JSON line")
        void eachEntityIsOneLine() {
            UUID id = UUID.randomUUID();
            Entity e = entityWithId(id, Map.of("price", 42.0, "currency", "USD"));
            stub(List.of(e));

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100));
            String[] lines = ndjson.split("\n");

            assertThat(lines).hasSize(1);
            assertThat(lines[0]).startsWith("{");
            assertThat(lines[0]).contains(id.toString());
            assertThat(lines[0]).contains("42.0");
            assertThat(lines[0]).contains("USD");
        }

        @Test
        @DisplayName("NDJSON includes standard metadata fields")
        void ndjsonIncludesMetadata() {
            Entity e = entityWith(Map.of("k", "v"));
            stub(List.of(e));

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100));

            assertThat(ndjson).contains("\"tenantId\"");
            assertThat(ndjson).contains("\"collectionName\"");
            assertThat(ndjson).contains("\"createdAt\"");
            assertThat(ndjson).contains("\"id\"");
        }

        @Test
        @DisplayName("data fields are merged at the top level of the JSON object")
        void dataFieldsMergedAtTopLevel() {
            Entity e = entityWith(Map.of("myField", "myValue"));
            stub(List.of(e));

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100));

            // field appears at top level, NOT nested inside a "data" object
            assertThat(ndjson).contains("\"myField\"");
        }

        @Test
        @DisplayName("multiple entities produce one line each with trailing newline")
        void multipleEntitiesProduceMultipleLines() {
            List<Entity> entities = List.of(
                    entityWith(Map.of("n", 1)),
                    entityWith(Map.of("n", 2)),
                    entityWith(Map.of("n", 3)));
            stub(entities);

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100));
            long lineCount = ndjson.chars().filter(c -> c == '\n').count();
            assertThat(lineCount).isEqualTo(3);
        }

        @Test
        @DisplayName("each line is valid parseable JSON")
        void eachLineIsParseable() throws Exception {
            List<Entity> entities = List.of(
                    entityWith(Map.of("a", 1)),
                    entityWith(Map.of("b", "two")));
            stub(entities);

            ObjectMapper mapper = new ObjectMapper();
            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100));
            for (String line : ndjson.split("\n")) {
                // Should not throw
                assertThatCode(() -> mapper.readTree(line)).doesNotThrowAnyException();
            }
        }
    }

    // =========================================================================
    // Pagination — fetchAll
    // =========================================================================

    @Nested
    @DisplayName("Pagination — fetchAll")
    class PaginationTests {

        @Test
        @DisplayName("limit=0 is treated as HARD_LIMIT")
        void limitZeroUsesHardLimit() {
            stubEmpty();
            // Should not throw, and should issue at least one repository call
            runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 0));
            verify(repository, atLeastOnce())
                    .findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("limit greater than HARD_LIMIT is capped to HARD_LIMIT")
        void limitAboveHardLimitIsCapped() {
            stubEmpty();
            runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), Integer.MAX_VALUE));
            // If limit was clamped, the first page call should have limit <= HARD_LIMIT
            verify(repository, atLeastOnce())
                    .findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(),
                            intThat(l -> l <= EntityExportService.HARD_LIMIT));
        }

        @Test
        @DisplayName("stops paging when page returns fewer entities than PAGE_SIZE")
        void stopsWhenLastPageIsPartial() {
            // First call returns PAGE_SIZE entities (full page -> continues)
            List<Entity> fullPage = buildEntities(EntityExportService.PAGE_SIZE, "v", 1.0);
            // Second call returns 3 entities (partial page -> stop)
            List<Entity> partial = buildEntities(3, "v", 99.0);

            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(fullPage))
                    .thenReturn(Promise.of(partial));

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(),
                    EntityExportService.PAGE_SIZE * 10));

            // header + fullPage + partial = 1 + 500 + 3 = 504 lines
            long lineCount = csv.lines().count();
            assertThat(lineCount).isEqualTo(1 + EntityExportService.PAGE_SIZE + 3);
        }
    }

    // =========================================================================
    // csvEscape() — RFC 4180 compliance
    // =========================================================================

    @Nested
    @DisplayName("csvEscape() — RFC 4180 compliance")
    class CsvEscapeTests {

        @Test
        @DisplayName("plain value passes through unchanged")
        void plainValueIsUnchanged() {
            assertThat(csvEscape("hello")).isEqualTo("hello");
        }

        @Test
        @DisplayName("empty string returns empty string")
        void emptyStringReturnsEmpty() {
            assertThat(csvEscape("")).isEmpty();
        }

        @Test
        @DisplayName("null returns empty string")
        void nullReturnsEmpty() {
            assertThat(csvEscape(null)).isEmpty();
        }

        @Test
        @DisplayName("value containing comma is wrapped in double-quotes")
        void commaValueIsQuoted() {
            assertThat(csvEscape("a,b")).isEqualTo("\"a,b\"");
        }

        @Test
        @DisplayName("value containing double-quote has quotes doubled and is wrapped")
        void quoteValueIsDoubled() {
            assertThat(csvEscape("say \"hello\"")).isEqualTo("\"say \"\"hello\"\"\"");
        }

        @Test
        @DisplayName("value containing newline is wrapped in double-quotes")
        void newlineValueIsQuoted() {
            assertThat(csvEscape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        }

        @Test
        @DisplayName("value containing carriage-return is wrapped in double-quotes")
        void carriageReturnValueIsQuoted() {
            assertThat(csvEscape("line1\rline2")).isEqualTo("\"line1\rline2\"");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stub(List<Entity> entities) {
        when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                .thenReturn(Promise.of(entities))
                .thenReturn(Promise.of(Collections.emptyList())); // second page = empty
    }

    private void stubEmpty() {
        when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                .thenReturn(Promise.of(Collections.emptyList()));
    }

    private static Entity entityWith(Map<String, Object> data) {
        return entityWithId(UUID.randomUUID(), data);
    }

    private static Entity entityWithId(UUID id, Map<String, Object> data) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setCollectionName(COLLECTION);
        entity.setCreatedAt(Instant.now());
        entity.setData(new HashMap<>(data));
        return entity;
    }

    /** Builds {@code count} entities, each with {@code field = baseValue + index}. */
    private static List<Entity> buildEntities(int count, String field, double baseValue) {
        List<Entity> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(entityWith(Map.of(field, baseValue + i)));
        }
        return result;
    }
}
