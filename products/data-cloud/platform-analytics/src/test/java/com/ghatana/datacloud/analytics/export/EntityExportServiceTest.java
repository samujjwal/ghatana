/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. // GH-90000
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
 * inside {@code runPromise()}. // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EntityExportService Tests")
class EntityExportServiceTest extends EventloopTestBase {

    private static final String TENANT     = "tenant-exports";
    private static final String COLLECTION = "invoices";

    @Mock
    private EntityRepository repository;

    private EntityExportService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new EntityExportService( // GH-90000
                repository,
                new ObjectMapper(), // GH-90000
                Executors.newVirtualThreadPerTaskExecutor()); // GH-90000
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null entityRepository throws NullPointerException")
        void nullRepositoryFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new EntityExportService(null, new ObjectMapper())) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("entityRepository");
        }

        @Test
        @DisplayName("null objectMapper throws NullPointerException")
        void nullObjectMapperFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new EntityExportService(repository, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("objectMapper");
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutorFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new EntityExportService(repository, new ObjectMapper(), null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("executor");
        }

        @Test
        @DisplayName("null tenantId in exportCsv throws NullPointerException")
        void nullTenantInCsvFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> service.exportCsv(null, COLLECTION, Map.of(), 100))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("null collectionName in exportNdjson throws NullPointerException")
        void nullCollectionInNdjsonFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> service.exportNdjson(TENANT, null, Map.of(), 100))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("collectionName");
        }
    }

    // =========================================================================
    // exportCsv() — empty collection // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("exportCsv() — empty collection")
    class CsvEmptyTests {

        @Test
        @DisplayName("returns empty string for an empty collection")
        void emptyCollectionReturnsEmptyString() { // GH-90000
            stubEmpty(); // GH-90000
            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            assertThat(csv).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // exportCsv() — header and data rows // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("exportCsv() — header and data rows")
    class CsvRowTests {

        @Test
        @DisplayName("CSV has header row with metadata + sorted data fields")
        void csvHeaderContainsMetadataAndDataFields() { // GH-90000
            Entity e = entityWith(Map.of("amount", 99.9, "status", "PAID")); // GH-90000
            stub(List.of(e)); // GH-90000

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            String[] lines = csv.split("\r\n");

            assertThat(lines[0]).startsWith("id,tenantId,collectionName,createdAt");
            assertThat(lines[0]).contains("amount");
            assertThat(lines[0]).contains("status");
        }

        @Test
        @DisplayName("data fields appear in sorted order in the header")
        void dataFieldsSortedInHeader() { // GH-90000
            Entity e = entityWith(Map.of("zzz", 1, "aaa", 2, "mmm", 3)); // GH-90000
            stub(List.of(e)); // GH-90000

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            // Header line
            String header = csv.split("\r\n")[0];
            int aaa = header.indexOf("aaa");
            int mmm = header.indexOf("mmm");
            int zzz = header.indexOf("zzz");
            assertThat(aaa).isLessThan(mmm); // GH-90000
            assertThat(mmm).isLessThan(zzz); // GH-90000
        }

        @Test
        @DisplayName("data row contains correct values")
        void csvDataRowContainsValues() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity e = entityWithId(id, Map.of("amount", 250.0, "status", "PENDING")); // GH-90000
            stub(List.of(e)); // GH-90000

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            String[] lines = csv.split("\r\n");

            assertThat(lines).hasSize(2); // header + one data row // GH-90000
            assertThat(lines[1]).contains(id.toString()); // GH-90000
            assertThat(lines[1]).contains(TENANT); // GH-90000
            assertThat(lines[1]).contains(COLLECTION); // GH-90000
            assertThat(lines[1]).contains("250.0");
            assertThat(lines[1]).contains("PENDING");
        }

        @Test
        @DisplayName("missing field in an entity row is rendered as empty string")
        void missingFieldsAreEmptyInCsv() { // GH-90000
            Entity e1 = entityWith(Map.of("field1", "A", "field2", "B")); // GH-90000
            Entity e2 = entityWith(Map.of("field1", "C")); // field2 missing // GH-90000
            stub(List.of(e1, e2)); // GH-90000

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            String[] lines = csv.split("\r\n");

            assertThat(lines).hasSize(3); // header + 2 rows // GH-90000
            // Row for e2 should have an empty value where field2 would be
            assertThat(lines[2]).contains("C");
            // field2 value is empty (two consecutive commas around it) // GH-90000
            assertThat(lines[2]).matches(".*,C,.*");
        }

        @Test
        @DisplayName("entity with null data map produces metadata-only row")
        void nullDataProducesMetadataOnlyRow() { // GH-90000
            Entity e = new Entity(); // GH-90000
            e.setId(UUID.randomUUID()); // GH-90000
            e.setTenantId(TENANT); // GH-90000
            e.setCollectionName(COLLECTION); // GH-90000
            e.setData(null); // GH-90000
            stub(List.of(e)); // GH-90000

            // Should not throw; data fields are empty
            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            assertThat(csv).isNotEmpty(); // header + one row // GH-90000
        }

        @Test
        @DisplayName("multiple entities are all exported in CSV")
        void multipleEntitiesExported() { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWith(Map.of("x", 1)), // GH-90000
                    entityWith(Map.of("x", 2)), // GH-90000
                    entityWith(Map.of("x", 3))); // GH-90000
            stub(entities); // GH-90000

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            String[] lines = csv.split("\r\n");
            assertThat(lines).hasSize(4); // header + 3 rows // GH-90000
        }
    }

    // =========================================================================
    // exportNdjson() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("exportNdjson()")
    class NdjsonTests {

        @Test
        @DisplayName("returns empty string for an empty collection")
        void emptyCollectionReturnsEmptyString() { // GH-90000
            stubEmpty(); // GH-90000
            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            assertThat(ndjson).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("each entity produces one JSON line")
        void eachEntityIsOneLine() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity e = entityWithId(id, Map.of("price", 42.0, "currency", "USD")); // GH-90000
            stub(List.of(e)); // GH-90000

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            String[] lines = ndjson.split("\n");

            assertThat(lines).hasSize(1); // GH-90000
            assertThat(lines[0]).startsWith("{");
            assertThat(lines[0]).contains(id.toString()); // GH-90000
            assertThat(lines[0]).contains("42.0");
            assertThat(lines[0]).contains("USD");
        }

        @Test
        @DisplayName("NDJSON includes standard metadata fields")
        void ndjsonIncludesMetadata() { // GH-90000
            Entity e = entityWith(Map.of("k", "v")); // GH-90000
            stub(List.of(e)); // GH-90000

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100)); // GH-90000

            assertThat(ndjson).contains("\"tenantId\""); // GH-90000
            assertThat(ndjson).contains("\"collectionName\""); // GH-90000
            assertThat(ndjson).contains("\"createdAt\""); // GH-90000
            assertThat(ndjson).contains("\"id\""); // GH-90000
        }

        @Test
        @DisplayName("data fields are merged at the top level of the JSON object")
        void dataFieldsMergedAtTopLevel() { // GH-90000
            Entity e = entityWith(Map.of("myField", "myValue")); // GH-90000
            stub(List.of(e)); // GH-90000

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100)); // GH-90000

            // field appears at top level, NOT nested inside a "data" object
            assertThat(ndjson).contains("\"myField\""); // GH-90000
        }

        @Test
        @DisplayName("multiple entities produce one line each with trailing newline")
        void multipleEntitiesProduceMultipleLines() { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWith(Map.of("n", 1)), // GH-90000
                    entityWith(Map.of("n", 2)), // GH-90000
                    entityWith(Map.of("n", 3))); // GH-90000
            stub(entities); // GH-90000

            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            long lineCount = ndjson.chars().filter(c -> c == '\n').count(); // GH-90000
            assertThat(lineCount).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("each line is valid parseable JSON")
        void eachLineIsParseable() throws Exception { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWith(Map.of("a", 1)), // GH-90000
                    entityWith(Map.of("b", "two"))); // GH-90000
            stub(entities); // GH-90000

            ObjectMapper mapper = new ObjectMapper(); // GH-90000
            String ndjson = runPromise(() -> service.exportNdjson(TENANT, COLLECTION, Map.of(), 100)); // GH-90000
            for (String line : ndjson.split("\n")) {
                // Should not throw
                assertThatCode(() -> mapper.readTree(line)).doesNotThrowAnyException(); // GH-90000
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
        void limitZeroUsesHardLimit() { // GH-90000
            stubEmpty(); // GH-90000
            // Should not throw, and should issue at least one repository call
            runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), 0)); // GH-90000
            verify(repository, atLeastOnce()) // GH-90000
                    .findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt()); // GH-90000
        }

        @Test
        @DisplayName("limit greater than HARD_LIMIT is capped to HARD_LIMIT")
        void limitAboveHardLimitIsCapped() { // GH-90000
            stubEmpty(); // GH-90000
            runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), Integer.MAX_VALUE)); // GH-90000
            // If limit was clamped, the first page call should have limit <= HARD_LIMIT
            verify(repository, atLeastOnce()) // GH-90000
                    .findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), // GH-90000
                            intThat(l -> l <= EntityExportService.HARD_LIMIT)); // GH-90000
        }

        @Test
        @DisplayName("stops paging when page returns fewer entities than PAGE_SIZE")
        void stopsWhenLastPageIsPartial() { // GH-90000
            // First call returns PAGE_SIZE entities (full page -> continues) // GH-90000
            List<Entity> fullPage = buildEntities(EntityExportService.PAGE_SIZE, "v", 1.0); // GH-90000
            // Second call returns 3 entities (partial page -> stop) // GH-90000
            List<Entity> partial = buildEntities(3, "v", 99.0); // GH-90000

                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(fullPage)) // GH-90000
                    .thenReturn(Promise.of(partial)); // GH-90000

            String csv = runPromise(() -> service.exportCsv(TENANT, COLLECTION, Map.of(), // GH-90000
                    EntityExportService.PAGE_SIZE * 10));

            // header + fullPage + partial = 1 + 500 + 3 = 504 lines
            long lineCount = csv.lines().count(); // GH-90000
            assertThat(lineCount).isEqualTo(1 + EntityExportService.PAGE_SIZE + 3); // GH-90000
        }
    }

    // =========================================================================
    // csvEscape() — RFC 4180 compliance // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("csvEscape() — RFC 4180 compliance")
    class CsvEscapeTests {

        @Test
        @DisplayName("plain value passes through unchanged")
        void plainValueIsUnchanged() { // GH-90000
            assertThat(csvEscape("hello")).isEqualTo("hello");
        }

        @Test
        @DisplayName("empty string returns empty string")
        void emptyStringReturnsEmpty() { // GH-90000
            assertThat(csvEscape("")).isEmpty();
        }

        @Test
        @DisplayName("null returns empty string")
        void nullReturnsEmpty() { // GH-90000
            assertThat(csvEscape(null)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("value containing comma is wrapped in double-quotes")
        void commaValueIsQuoted() { // GH-90000
            assertThat(csvEscape("a,b")).isEqualTo("\"a,b\"");
        }

        @Test
        @DisplayName("value containing double-quote has quotes doubled and is wrapped")
        void quoteValueIsDoubled() { // GH-90000
            assertThat(csvEscape("say \"hello\"")).isEqualTo("\"say \"\"hello\"\"\""); // GH-90000
        }

        @Test
        @DisplayName("value containing newline is wrapped in double-quotes")
        void newlineValueIsQuoted() { // GH-90000
            assertThat(csvEscape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        }

        @Test
        @DisplayName("value containing carriage-return is wrapped in double-quotes")
        void carriageReturnValueIsQuoted() { // GH-90000
            assertThat(csvEscape("line1\rline2")).isEqualTo("\"line1\rline2\"");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stub(List<Entity> entities) { // GH-90000
        when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                .thenReturn(Promise.of(entities)) // GH-90000
                .thenReturn(Promise.of(Collections.emptyList())); // second page = empty // GH-90000
    }

    private void stubEmpty() { // GH-90000
        when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                .thenReturn(Promise.of(Collections.emptyList())); // GH-90000
    }

    private static Entity entityWith(Map<String, Object> data) { // GH-90000
        return entityWithId(UUID.randomUUID(), data); // GH-90000
    }

    private static Entity entityWithId(UUID id, Map<String, Object> data) { // GH-90000
        Entity entity = new Entity(); // GH-90000
        entity.setId(id); // GH-90000
        entity.setTenantId(TENANT); // GH-90000
        entity.setCollectionName(COLLECTION); // GH-90000
        entity.setCreatedAt(Instant.now()); // GH-90000
        entity.setData(new HashMap<>(data)); // GH-90000
        return entity;
    }

    /** Builds {@code count} entities, each with {@code field = baseValue + index}. */
    private static List<Entity> buildEntities(int count, String field, double baseValue) { // GH-90000
        List<Entity> result = new ArrayList<>(count); // GH-90000
        for (int i = 0; i < count; i++) { // GH-90000
            result.add(entityWith(Map.of(field, baseValue + i))); // GH-90000
        }
        return result;
    }
}
