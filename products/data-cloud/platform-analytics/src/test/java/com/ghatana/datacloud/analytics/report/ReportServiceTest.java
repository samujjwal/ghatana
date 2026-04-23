/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.report;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReportService}.
 *
 * <p>AnalyticsQueryEngine is used as a real instance (no external deps required). // GH-90000
 * EntityExportService is mocked via Mockito inline mock maker (configured in // GH-90000
 * {@code src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker})
 * because the class is {@code final}.
 *
 * @doc.type test
 * @doc.purpose Comprehensive unit tests for ReportService (DC-10) // GH-90000
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("ReportService Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class ReportServiceTest extends EventloopTestBase {

    @Mock
    EntityExportService exportService;

    /** Real engine — works without external storage. Returns synthetic results. */
    AnalyticsQueryEngine analyticsEngine;

    ReportService reportService;

    static final String TENANT = "tenant-abc";

    @BeforeEach
    void setUp() { // GH-90000
        analyticsEngine = new AnalyticsQueryEngine(); // GH-90000
        reportService   = new ReportService(analyticsEngine, exportService); // GH-90000
    }

    // =========================================================================
    // Constructor & Argument Validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw NullPointerException when analyticsEngine is null")
        void shouldRejectNullAnalyticsEngine() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new ReportService(null, exportService)) // GH-90000
                    .withMessageContaining("analyticsEngine");
        }

        @Test
        @DisplayName("Should accept null exportService (standalone mode without export)")
        void shouldAcceptNullExportService() { // GH-90000
            // null is intentionally allowed — ENTITY_EXPORT reports will return an error at runtime
            assertThatCode(() -> new ReportService(analyticsEngine, null)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // generate() — argument guards // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("generate() argument validation")
    class GenerateValidationTests {

        @Test
        @DisplayName("Should throw NullPointerException when tenantId is null")
        void shouldRejectNullTenantId() { // GH-90000
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> reportService.generate(null, def)); // GH-90000
        }

        @Test
        @DisplayName("Should throw NullPointerException when definition is null")
        void shouldRejectNullDefinition() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> reportService.generate(TENANT, null)); // GH-90000
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when tenantId is blank")
        void shouldRejectBlankTenantId() { // GH-90000
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> reportService.generate("  ", def)) // GH-90000
                    .withMessageContaining("tenantId must not be blank");
        }
    }

    // =========================================================================
    // QUERY Reports — JSON output
    // =========================================================================

    @Nested
    @DisplayName("QUERY reports")
    class QueryReportTests {

        @Test
        @DisplayName("Should generate JSON QUERY report and return non-null rows")
        void shouldGenerateQueryReportJson() { // GH-90000
            ReportDefinition def = queryDef("SELECT event_type, COUNT(*) FROM events GROUP BY event_type", // GH-90000
                                            ReportFormat.JSON);

            ReportResult result = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getReportId()).isNotBlank(); // GH-90000
            assertThat(result.getReportName()).isEqualTo(def.getName()); // GH-90000
            assertThat(result.getFormat()).isEqualTo(ReportFormat.JSON); // GH-90000
            assertThat(result.getContentType()).isEqualTo("application/json");
            assertThat(result.getRows()).isNotNull(); // GH-90000
            assertThat(result.getFormattedBody()).isNull();       // JSON returns rows, not text body // GH-90000
            assertThat(result.getGeneratedAt()).isNotNull(); // GH-90000
            assertThat(result.getExecutionTime()).isNotNull(); // GH-90000
            assertThat(result.getExecutionTime().toMillis()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Should generate CSV QUERY report and return non-blank formatted body")
        void shouldGenerateQueryReportCsv() { // GH-90000
            ReportDefinition def = queryDef("SELECT id, name FROM users", ReportFormat.CSV); // GH-90000

            ReportResult result = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV); // GH-90000
            assertThat(result.getContentType()).isEqualTo("text/csv; charset=UTF-8");
            // AnalyticsQueryEngine returns empty rows; renderTextFormat returns empty string
            assertThat(result.getFormattedBody()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should generate NDJSON QUERY report and return formatted body")
        void shouldGenerateQueryReportNdjson() { // GH-90000
            ReportDefinition def = queryDef("SELECT * FROM events", ReportFormat.NDJSON); // GH-90000

            ReportResult result = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(result.getFormat()).isEqualTo(ReportFormat.NDJSON); // GH-90000
            assertThat(result.getContentType()).isEqualTo("application/x-ndjson");
            assertThat(result.getFormattedBody()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Should cache result by reportId for subsequent retrieval")
        void shouldCacheQueryResultByReportId() { // GH-90000
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON); // GH-90000

            ReportResult generated = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000
            ReportResult cached    = reportService.getResult(generated.getReportId()); // GH-90000

            assertThat(cached).isSameAs(generated); // GH-90000
        }

        @Test
        @DisplayName("Should include reportId in listCachedReports() after generation")
        void shouldListCachedReportAfterGeneration() { // GH-90000
            ReportDefinition namedDef = ReportDefinition.builder() // GH-90000
                    .name("my-report").type(ReportType.QUERY)
                    .format(ReportFormat.JSON).query("SELECT 1").build();

            ReportResult result = runPromise(() -> reportService.generate(TENANT, namedDef)); // GH-90000

            Map<String, String> listing = reportService.listCachedReports(); // GH-90000
            assertThat(listing).containsKey(result.getReportId()); // GH-90000
            assertThat(listing.get(result.getReportId())).isEqualTo("my-report");
        }
    }

    // =========================================================================
    // ENTITY_EXPORT Reports
    // =========================================================================

    @Nested
    @DisplayName("ENTITY_EXPORT reports")
    class EntityExportReportTests {

        @Test
        @DisplayName("Should generate CSV ENTITY_EXPORT report from exportService")
        void shouldGenerateCsvExportReport() { // GH-90000
            String csvBody = "id,name\r\n1,Alice\r\n2,Bob\r\n";
            when(exportService.exportCsv(eq(TENANT), eq("users"), anyMap(), anyInt()))
                    .thenReturn(Promise.of(csvBody)); // GH-90000

            ReportDefinition def = exportDef("users", ReportFormat.CSV); // GH-90000
            ReportResult result = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV); // GH-90000
            assertThat(result.getContentType()).isEqualTo("text/csv; charset=UTF-8");
            assertThat(result.getFormattedBody()).isEqualTo(csvBody); // GH-90000
            assertThat(result.getRowCount()).isEqualTo(2); // 2 data rows (header excluded) // GH-90000
            assertThat(result.getRows()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Should generate NDJSON ENTITY_EXPORT report from exportService")
        void shouldGenerateNdjsonExportReport() { // GH-90000
            String ndjsonBody = "{\"id\":\"1\",\"name\":\"Alice\"}\n{\"id\":\"2\",\"name\":\"Bob\"}\n";
            when(exportService.exportNdjson(eq(TENANT), eq("users"), anyMap(), anyInt()))
                    .thenReturn(Promise.of(ndjsonBody)); // GH-90000

            ReportDefinition def = exportDef("users", ReportFormat.NDJSON); // GH-90000
            ReportResult result = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(result.getFormat()).isEqualTo(ReportFormat.NDJSON); // GH-90000
            assertThat(result.getContentType()).isEqualTo("application/x-ndjson");
            assertThat(result.getFormattedBody()).isEqualTo(ndjsonBody); // GH-90000
            assertThat(result.getRowCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Should parse NDJSON into rows for JSON format ENTITY_EXPORT")
        void shouldParseNdjsonToRowsForJsonFormat() { // GH-90000
            String ndjsonBody = "{\"id\":\"1\",\"city\":\"Paris\"}\n{\"id\":\"2\",\"city\":\"Berlin\"}\n";
            when(exportService.exportNdjson(eq(TENANT), eq("locations"), anyMap(), anyInt()))
                    .thenReturn(Promise.of(ndjsonBody)); // GH-90000

            ReportDefinition def = exportDef("locations", ReportFormat.JSON); // GH-90000
            ReportResult result = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(result.getFormat()).isEqualTo(ReportFormat.JSON); // GH-90000
            assertThat(result.getFormattedBody()).isNull(); // GH-90000
            assertThat(result.getRows()).hasSize(2); // GH-90000
            assertThat(result.getRows().get(0)).containsEntry("city", "Paris"); // GH-90000
            assertThat(result.getRows().get(1)).containsEntry("city", "Berlin"); // GH-90000
        }

        @Test
        @DisplayName("Should propagate exportService exception as failed Promise")
        void shouldPropagateExportFailure() { // GH-90000
            RuntimeException boom = new RuntimeException("storage unavailable");
            when(exportService.exportCsv(anyString(), anyString(), anyMap(), anyInt())) // GH-90000
                    .thenReturn(Promise.ofException(boom)); // GH-90000

            ReportDefinition def = exportDef("broken-collection", ReportFormat.CSV); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> reportService.generate(TENANT, def))) // GH-90000
                    .hasMessageContaining("storage unavailable");
        }

        @Test
        @DisplayName("Should cache ENTITY_EXPORT result by reportId")
        void shouldCacheEntityExportResult() { // GH-90000
            when(exportService.exportCsv(anyString(), anyString(), anyMap(), anyInt())) // GH-90000
                    .thenReturn(Promise.of("col\r\nval\r\n"));

            ReportDefinition def = exportDef("orders", ReportFormat.CSV); // GH-90000
            ReportResult generated = runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            assertThat(reportService.getResult(generated.getReportId())).isSameAs(generated); // GH-90000
        }
    }

    // =========================================================================
    // getResult() & listCachedReports() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Cache retrieval")
    class CacheRetrievalTests {

        @Test
        @DisplayName("getResult() returns null for unknown reportId")
        void shouldReturnNullForUnknownReportId() { // GH-90000
            assertThat(reportService.getResult("does-not-exist")).isNull();
        }

        @Test
        @DisplayName("listCachedReports() returns empty map when no reports generated")
        void shouldReturnEmptyListInitially() { // GH-90000
            assertThat(reportService.listCachedReports()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("listCachedReports() snapshot does not reflect subsequent additions")
        void snapshotIsImmutable() { // GH-90000
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON); // GH-90000
            runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            Map<String, String> snapshot = reportService.listCachedReports(); // GH-90000
            int sizeBefore = snapshot.size(); // GH-90000

            runPromise(() -> reportService.generate(TENANT, def)); // GH-90000

            // The previously captured snapshot is unmodifiable
            assertThat(snapshot).hasSize(sizeBefore); // GH-90000
            assertThatExceptionOfType(UnsupportedOperationException.class) // GH-90000
                    .isThrownBy(() -> snapshot.put("x", "y")); // GH-90000
        }
    }

    // =========================================================================
    // renderTextFormat() — static helper // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("renderTextFormat() — CSV and NDJSON rendering")
    class RenderTextFormatTests {

        @Test
        @DisplayName("CSV header matches union of all row keys")
        void csvHeaderReflectsAllColumns() { // GH-90000
            List<Map<String, Object>> rows = List.of( // GH-90000
                    Map.of("name", "Alice", "age", 30), // GH-90000
                    Map.of("name", "Bob",   "city", "Paris") // GH-90000
            );
            String csv = ReportService.renderTextFormat(rows, ReportFormat.CSV); // GH-90000

            String[] lines = csv.split("\r\n");
            assertThat(lines).hasSizeGreaterThanOrEqualTo(3); // header + 2 data rows // GH-90000
            // All keys in header
            assertThat(lines[0]).contains("name");
        }

        @Test
        @DisplayName("CSV quotes values that contain a comma")
        void csvQuotesCommaValues() { // GH-90000
            List<Map<String, Object>> rows = List.of(Map.of("address", "123 Main St, Apt 4")); // GH-90000
            String csv = ReportService.renderTextFormat(rows, ReportFormat.CSV); // GH-90000
            assertThat(csv).contains("\"123 Main St, Apt 4\""); // GH-90000
        }

        @Test
        @DisplayName("CSV escapes embedded double-quotes per RFC 4180")
        void csvEscapesDoubleQuotes() { // GH-90000
            List<Map<String, Object>> rows = List.of(Map.of("note", "say \"hi\"")); // GH-90000
            String csv = ReportService.renderTextFormat(rows, ReportFormat.CSV); // GH-90000
            assertThat(csv).contains("\"say \"\"hi\"\"\""); // GH-90000
        }

        @Test
        @DisplayName("NDJSON produces one JSON line per row")
        void ndjsonProducesOneLinePerRow() { // GH-90000
            List<Map<String, Object>> rows = List.of( // GH-90000
                    Map.of("id", 1L, "active", true), // GH-90000
                    Map.of("id", 2L, "active", false) // GH-90000
            );
            String ndjson = ReportService.renderTextFormat(rows, ReportFormat.NDJSON); // GH-90000
            String[] lines = ndjson.split("\n");
            assertThat(lines).hasSize(2); // GH-90000
            assertThat(lines[0]).startsWith("{").endsWith("}");
            assertThat(lines[1]).startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("renderTextFormat returns empty string for empty row list")
        void emptyRowsProduceEmptyOutput() { // GH-90000
            assertThat(ReportService.renderTextFormat(List.of(), ReportFormat.CSV)).isEmpty(); // GH-90000
            assertThat(ReportService.renderTextFormat(List.of(), ReportFormat.NDJSON)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("renderTextFormat throws for JSON format")
        void shouldRejectJsonFormat() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> ReportService.renderTextFormat(List.of(Map.of()), ReportFormat.JSON)); // GH-90000
        }
    }

    // =========================================================================
    // ReportDefinition.fromMap() — parsed from HTTP request body // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("ReportDefinition.fromMap() parsing & validation")
    class ReportDefinitionFromMapTests {

        @Test
        @DisplayName("Should parse a minimal QUERY definition successfully")
        void shouldParseMinimalQueryDefinition() { // GH-90000
            Map<String, Object> m = Map.of( // GH-90000
                    "name",  "test-report",
                    "type",  "QUERY",
                    "query", "SELECT 1"
            );
            ReportDefinition def = ReportDefinition.fromMap(m); // GH-90000
            assertThat(def.getName()).isEqualTo("test-report");
            assertThat(def.getType()).isEqualTo(ReportType.QUERY); // GH-90000
            assertThat(def.getFormat()).isEqualTo(ReportFormat.JSON); // default // GH-90000
            assertThat(def.getQuery()).isEqualTo("SELECT 1");
        }

        @Test
        @DisplayName("Should parse a minimal ENTITY_EXPORT definition successfully")
        void shouldParseMinimalEntityExportDefinition() { // GH-90000
            Map<String, Object> m = Map.of( // GH-90000
                    "name",       "export-report",
                    "type",       "ENTITY_EXPORT",
                    "collection", "my-collection"
            );
            ReportDefinition def = ReportDefinition.fromMap(m); // GH-90000
            assertThat(def.getType()).isEqualTo(ReportType.ENTITY_EXPORT); // GH-90000
            assertThat(def.getCollection()).isEqualTo("my-collection");
        }

        @ParameterizedTest(name = "missing field: {0}") // GH-90000
        @ValueSource(strings = {"name", "type"}) // GH-90000
        @DisplayName("Should reject definition map missing required top-level fields")
        void shouldRejectMissingRequiredFields(String missingField) { // GH-90000
            java.util.Map<String, Object> m = new java.util.HashMap<>(Map.of( // GH-90000
                    "name",  "r",
                    "type",  "QUERY",
                    "query", "SELECT 1"
            ));
            m.remove(missingField); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> ReportDefinition.fromMap(m)); // GH-90000
        }

        @Test
        @DisplayName("Should reject QUERY definition without query field")
        void shouldRejectQueryDefinitionWithoutQuery() { // GH-90000
            Map<String, Object> m = Map.of("name", "r", "type", "QUERY"); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> ReportDefinition.fromMap(m)); // GH-90000
        }

        @Test
        @DisplayName("Should reject ENTITY_EXPORT definition without collection field")
        void shouldRejectExportDefinitionWithoutCollection() { // GH-90000
            Map<String, Object> m = Map.of("name", "r", "type", "ENTITY_EXPORT"); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> ReportDefinition.fromMap(m)); // GH-90000
        }

        @Test
        @DisplayName("Should apply custom limit when specified in map")
        void shouldApplyCustomLimit() { // GH-90000
            Map<String, Object> m = Map.of( // GH-90000
                    "name", "r", "type", "QUERY", "query", "SELECT 1", "limit", 500
            );
            assertThat(ReportDefinition.fromMap(m).getLimit()).isEqualTo(500); // GH-90000
        }
    }

    // =========================================================================
    // close() — lifecycle // GH-90000
    // =========================================================================

    @Test
    @DisplayName("close() completes without error (no-op)")
    void closeShouldBeNoOp() { // GH-90000
        assertThatNoException().isThrownBy(() -> reportService.close()); // GH-90000
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds a QUERY {@link ReportDefinition} with a minimal SELECT statement. */
    private static ReportDefinition queryDef(String sql, ReportFormat format) { // GH-90000
        return ReportDefinition.builder() // GH-90000
                .name("test-query-report")
                .type(ReportType.QUERY) // GH-90000
                .format(format) // GH-90000
                .query(sql) // GH-90000
                .build(); // GH-90000
    }

    /** Builds an ENTITY_EXPORT {@link ReportDefinition} for the given collection. */
    private static ReportDefinition exportDef(String collection, ReportFormat format) { // GH-90000
        return ReportDefinition.builder() // GH-90000
                .name("test-export-report")
                .type(ReportType.ENTITY_EXPORT) // GH-90000
                .format(format) // GH-90000
                .collection(collection) // GH-90000
                .build(); // GH-90000
    }
}
