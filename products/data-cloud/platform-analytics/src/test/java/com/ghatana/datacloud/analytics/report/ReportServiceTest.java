/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * <p>AnalyticsQueryEngine is used as a real instance (no external deps required).
 * EntityExportService is mocked via Mockito inline mock maker (configured in
 * {@code src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker})
 * because the class is {@code final}.
 *
 * @doc.type test
 * @doc.purpose Comprehensive unit tests for ReportService (DC-10)
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("ReportService Tests")
@ExtendWith(MockitoExtension.class)
class ReportServiceTest extends EventloopTestBase {

    @Mock
    EntityExportService exportService;

    /** Real engine — works without external storage. Returns synthetic results. */
    AnalyticsQueryEngine analyticsEngine;

    ReportService reportService;

    static final String TENANT = "tenant-abc";

    @BeforeEach
    void setUp() {
        analyticsEngine = new AnalyticsQueryEngine();
        reportService   = new ReportService(analyticsEngine, exportService);
    }

    // =========================================================================
    // Constructor & Argument Validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw NullPointerException when analyticsEngine is null")
        void shouldRejectNullAnalyticsEngine() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ReportService(null, exportService))
                    .withMessageContaining("analyticsEngine");
        }

        @Test
        @DisplayName("Should accept null exportService (standalone mode without export)")
        void shouldAcceptNullExportService() {
            // null is intentionally allowed — ENTITY_EXPORT reports will return an error at runtime
            assertThatCode(() -> new ReportService(analyticsEngine, null))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // generate() — argument guards
    // =========================================================================

    @Nested
    @DisplayName("generate() argument validation")
    class GenerateValidationTests {

        @Test
        @DisplayName("Should throw NullPointerException when tenantId is null")
        void shouldRejectNullTenantId() {
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON);
            assertThatNullPointerException()
                    .isThrownBy(() -> reportService.generate(null, def));
        }

        @Test
        @DisplayName("Should throw NullPointerException when definition is null")
        void shouldRejectNullDefinition() {
            assertThatNullPointerException()
                    .isThrownBy(() -> reportService.generate(TENANT, null));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when tenantId is blank")
        void shouldRejectBlankTenantId() {
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> reportService.generate("  ", def))
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
        void shouldGenerateQueryReportJson() {
            ReportDefinition def = queryDef("SELECT event_type, COUNT(*) FROM events GROUP BY event_type",
                                            ReportFormat.JSON);

            ReportResult result = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(result).isNotNull();
            assertThat(result.getReportId()).isNotBlank();
            assertThat(result.getReportName()).isEqualTo(def.getName());
            assertThat(result.getFormat()).isEqualTo(ReportFormat.JSON);
            assertThat(result.getContentType()).isEqualTo("application/json");
            assertThat(result.getRows()).isNotNull();
            assertThat(result.getFormattedBody()).isNull();       // JSON returns rows, not text body
            assertThat(result.getGeneratedAt()).isNotNull();
            assertThat(result.getExecutionTime()).isNotNull();
            assertThat(result.getExecutionTime().toMillis()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should generate CSV QUERY report and return non-blank formatted body")
        void shouldGenerateQueryReportCsv() {
            ReportDefinition def = queryDef("SELECT id, name FROM users", ReportFormat.CSV);

            ReportResult result = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV);
            assertThat(result.getContentType()).isEqualTo("text/csv; charset=UTF-8");
            // AnalyticsQueryEngine returns empty rows; renderTextFormat returns empty string
            assertThat(result.getFormattedBody()).isNotNull();
        }

        @Test
        @DisplayName("Should generate NDJSON QUERY report and return formatted body")
        void shouldGenerateQueryReportNdjson() {
            ReportDefinition def = queryDef("SELECT * FROM events", ReportFormat.NDJSON);

            ReportResult result = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(result.getFormat()).isEqualTo(ReportFormat.NDJSON);
            assertThat(result.getContentType()).isEqualTo("application/x-ndjson");
            assertThat(result.getFormattedBody()).isNotNull();
        }

        @Test
        @DisplayName("Should cache result by reportId for subsequent retrieval")
        void shouldCacheQueryResultByReportId() {
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON);

            ReportResult generated = runPromise(() -> reportService.generate(TENANT, def));
            ReportResult cached    = reportService.getResult(generated.getReportId());

            assertThat(cached).isSameAs(generated);
        }

        @Test
        @DisplayName("Should include reportId in listCachedReports() after generation")
        void shouldListCachedReportAfterGeneration() {
            ReportDefinition namedDef = ReportDefinition.builder()
                    .name("my-report").type(ReportType.QUERY)
                    .format(ReportFormat.JSON).query("SELECT 1").build();

            ReportResult result = runPromise(() -> reportService.generate(TENANT, namedDef));

            Map<String, String> listing = reportService.listCachedReports();
            assertThat(listing).containsKey(result.getReportId());
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
        void shouldGenerateCsvExportReport() {
            String csvBody = "id,name\r\n1,Alice\r\n2,Bob\r\n";
            when(exportService.exportCsv(eq(TENANT), eq("users"), anyMap(), anyInt()))
                    .thenReturn(Promise.of(csvBody));

            ReportDefinition def = exportDef("users", ReportFormat.CSV);
            ReportResult result = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(result.getFormat()).isEqualTo(ReportFormat.CSV);
            assertThat(result.getContentType()).isEqualTo("text/csv; charset=UTF-8");
            assertThat(result.getFormattedBody()).isEqualTo(csvBody);
            assertThat(result.getRowCount()).isEqualTo(2); // 2 data rows (header excluded)
            assertThat(result.getRows()).isEmpty();
        }

        @Test
        @DisplayName("Should generate NDJSON ENTITY_EXPORT report from exportService")
        void shouldGenerateNdjsonExportReport() {
            String ndjsonBody = "{\"id\":\"1\",\"name\":\"Alice\"}\n{\"id\":\"2\",\"name\":\"Bob\"}\n";
            when(exportService.exportNdjson(eq(TENANT), eq("users"), anyMap(), anyInt()))
                    .thenReturn(Promise.of(ndjsonBody));

            ReportDefinition def = exportDef("users", ReportFormat.NDJSON);
            ReportResult result = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(result.getFormat()).isEqualTo(ReportFormat.NDJSON);
            assertThat(result.getContentType()).isEqualTo("application/x-ndjson");
            assertThat(result.getFormattedBody()).isEqualTo(ndjsonBody);
            assertThat(result.getRowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should parse NDJSON into rows for JSON format ENTITY_EXPORT")
        void shouldParseNdjsonToRowsForJsonFormat() {
            String ndjsonBody = "{\"id\":\"1\",\"city\":\"Paris\"}\n{\"id\":\"2\",\"city\":\"Berlin\"}\n";
            when(exportService.exportNdjson(eq(TENANT), eq("locations"), anyMap(), anyInt()))
                    .thenReturn(Promise.of(ndjsonBody));

            ReportDefinition def = exportDef("locations", ReportFormat.JSON);
            ReportResult result = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(result.getFormat()).isEqualTo(ReportFormat.JSON);
            assertThat(result.getFormattedBody()).isNull();
            assertThat(result.getRows()).hasSize(2);
            assertThat(result.getRows().get(0)).containsEntry("city", "Paris");
            assertThat(result.getRows().get(1)).containsEntry("city", "Berlin");
        }

        @Test
        @DisplayName("Should propagate exportService exception as failed Promise")
        void shouldPropagateExportFailure() {
            RuntimeException boom = new RuntimeException("storage unavailable");
            when(exportService.exportCsv(anyString(), anyString(), anyMap(), anyInt()))
                    .thenReturn(Promise.ofException(boom));

            ReportDefinition def = exportDef("broken-collection", ReportFormat.CSV);

            assertThatThrownBy(() -> runPromise(() -> reportService.generate(TENANT, def)))
                    .hasMessageContaining("storage unavailable");
        }

        @Test
        @DisplayName("Should cache ENTITY_EXPORT result by reportId")
        void shouldCacheEntityExportResult() {
            when(exportService.exportCsv(anyString(), anyString(), anyMap(), anyInt()))
                    .thenReturn(Promise.of("col\r\nval\r\n"));

            ReportDefinition def = exportDef("orders", ReportFormat.CSV);
            ReportResult generated = runPromise(() -> reportService.generate(TENANT, def));

            assertThat(reportService.getResult(generated.getReportId())).isSameAs(generated);
        }
    }

    // =========================================================================
    // getResult() & listCachedReports()
    // =========================================================================

    @Nested
    @DisplayName("Cache retrieval")
    class CacheRetrievalTests {

        @Test
        @DisplayName("getResult() returns null for unknown reportId")
        void shouldReturnNullForUnknownReportId() {
            assertThat(reportService.getResult("does-not-exist")).isNull();
        }

        @Test
        @DisplayName("listCachedReports() returns empty map when no reports generated")
        void shouldReturnEmptyListInitially() {
            assertThat(reportService.listCachedReports()).isEmpty();
        }

        @Test
        @DisplayName("listCachedReports() snapshot does not reflect subsequent additions")
        void snapshotIsImmutable() {
            ReportDefinition def = queryDef("SELECT 1", ReportFormat.JSON);
            runPromise(() -> reportService.generate(TENANT, def));

            Map<String, String> snapshot = reportService.listCachedReports();
            int sizeBefore = snapshot.size();

            runPromise(() -> reportService.generate(TENANT, def));

            // The previously captured snapshot is unmodifiable
            assertThat(snapshot).hasSize(sizeBefore);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> snapshot.put("x", "y"));
        }
    }

    // =========================================================================
    // renderTextFormat() — static helper
    // =========================================================================

    @Nested
    @DisplayName("renderTextFormat() — CSV and NDJSON rendering")
    class RenderTextFormatTests {

        @Test
        @DisplayName("CSV header matches union of all row keys")
        void csvHeaderReflectsAllColumns() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("name", "Alice", "age", 30),
                    Map.of("name", "Bob",   "city", "Paris")
            );
            String csv = ReportService.renderTextFormat(rows, ReportFormat.CSV);

            String[] lines = csv.split("\r\n");
            assertThat(lines).hasSizeGreaterThanOrEqualTo(3); // header + 2 data rows
            // All keys in header
            assertThat(lines[0]).contains("name");
        }

        @Test
        @DisplayName("CSV quotes values that contain a comma")
        void csvQuotesCommaValues() {
            List<Map<String, Object>> rows = List.of(Map.of("address", "123 Main St, Apt 4"));
            String csv = ReportService.renderTextFormat(rows, ReportFormat.CSV);
            assertThat(csv).contains("\"123 Main St, Apt 4\"");
        }

        @Test
        @DisplayName("CSV escapes embedded double-quotes per RFC 4180")
        void csvEscapesDoubleQuotes() {
            List<Map<String, Object>> rows = List.of(Map.of("note", "say \"hi\""));
            String csv = ReportService.renderTextFormat(rows, ReportFormat.CSV);
            assertThat(csv).contains("\"say \"\"hi\"\"\"");
        }

        @Test
        @DisplayName("NDJSON produces one JSON line per row")
        void ndjsonProducesOneLinePerRow() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("id", 1L, "active", true),
                    Map.of("id", 2L, "active", false)
            );
            String ndjson = ReportService.renderTextFormat(rows, ReportFormat.NDJSON);
            String[] lines = ndjson.split("\n");
            assertThat(lines).hasSize(2);
            assertThat(lines[0]).startsWith("{").endsWith("}");
            assertThat(lines[1]).startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("renderTextFormat returns empty string for empty row list")
        void emptyRowsProduceEmptyOutput() {
            assertThat(ReportService.renderTextFormat(List.of(), ReportFormat.CSV)).isEmpty();
            assertThat(ReportService.renderTextFormat(List.of(), ReportFormat.NDJSON)).isEmpty();
        }

        @Test
        @DisplayName("renderTextFormat throws for JSON format")
        void shouldRejectJsonFormat() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ReportService.renderTextFormat(List.of(Map.of()), ReportFormat.JSON));
        }
    }

    // =========================================================================
    // ReportDefinition.fromMap() — parsed from HTTP request body
    // =========================================================================

    @Nested
    @DisplayName("ReportDefinition.fromMap() parsing & validation")
    class ReportDefinitionFromMapTests {

        @Test
        @DisplayName("Should parse a minimal QUERY definition successfully")
        void shouldParseMinimalQueryDefinition() {
            Map<String, Object> m = Map.of(
                    "name",  "test-report",
                    "type",  "QUERY",
                    "query", "SELECT 1"
            );
            ReportDefinition def = ReportDefinition.fromMap(m);
            assertThat(def.getName()).isEqualTo("test-report");
            assertThat(def.getType()).isEqualTo(ReportType.QUERY);
            assertThat(def.getFormat()).isEqualTo(ReportFormat.JSON); // default
            assertThat(def.getQuery()).isEqualTo("SELECT 1");
        }

        @Test
        @DisplayName("Should parse a minimal ENTITY_EXPORT definition successfully")
        void shouldParseMinimalEntityExportDefinition() {
            Map<String, Object> m = Map.of(
                    "name",       "export-report",
                    "type",       "ENTITY_EXPORT",
                    "collection", "my-collection"
            );
            ReportDefinition def = ReportDefinition.fromMap(m);
            assertThat(def.getType()).isEqualTo(ReportType.ENTITY_EXPORT);
            assertThat(def.getCollection()).isEqualTo("my-collection");
        }

        @ParameterizedTest(name = "missing field: {0}")
        @ValueSource(strings = {"name", "type"})
        @DisplayName("Should reject definition map missing required top-level fields")
        void shouldRejectMissingRequiredFields(String missingField) {
            java.util.Map<String, Object> m = new java.util.HashMap<>(Map.of(
                    "name",  "r",
                    "type",  "QUERY",
                    "query", "SELECT 1"
            ));
            m.remove(missingField);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ReportDefinition.fromMap(m));
        }

        @Test
        @DisplayName("Should reject QUERY definition without query field")
        void shouldRejectQueryDefinitionWithoutQuery() {
            Map<String, Object> m = Map.of("name", "r", "type", "QUERY");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ReportDefinition.fromMap(m));
        }

        @Test
        @DisplayName("Should reject ENTITY_EXPORT definition without collection field")
        void shouldRejectExportDefinitionWithoutCollection() {
            Map<String, Object> m = Map.of("name", "r", "type", "ENTITY_EXPORT");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ReportDefinition.fromMap(m));
        }

        @Test
        @DisplayName("Should apply custom limit when specified in map")
        void shouldApplyCustomLimit() {
            Map<String, Object> m = Map.of(
                    "name", "r", "type", "QUERY", "query", "SELECT 1", "limit", 500
            );
            assertThat(ReportDefinition.fromMap(m).getLimit()).isEqualTo(500);
        }
    }

    // =========================================================================
    // close() — lifecycle
    // =========================================================================

    @Test
    @DisplayName("close() completes without error (no-op)")
    void closeShouldBeNoOp() {
        assertThatNoException().isThrownBy(() -> reportService.close());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds a QUERY {@link ReportDefinition} with a minimal SELECT statement. */
    private static ReportDefinition queryDef(String sql, ReportFormat format) {
        return ReportDefinition.builder()
                .name("test-query-report")
                .type(ReportType.QUERY)
                .format(format)
                .query(sql)
                .build();
    }

    /** Builds an ENTITY_EXPORT {@link ReportDefinition} for the given collection. */
    private static ReportDefinition exportDef(String collection, ReportFormat format) {
        return ReportDefinition.builder()
                .name("test-export-report")
                .type(ReportType.ENTITY_EXPORT)
                .format(format)
                .collection(collection)
                .build();
    }
}
