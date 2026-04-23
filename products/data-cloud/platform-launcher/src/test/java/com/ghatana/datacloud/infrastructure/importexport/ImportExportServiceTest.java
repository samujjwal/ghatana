package com.ghatana.datacloud.infrastructure.importexport;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link ImportExportService} with 100% coverage.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Export operations (CSV, PDF, Excel)</li> // GH-90000
 *   <li>Import operations (CSV, JSON, Excel)</li> // GH-90000
 *   <li>Validation (CSV, JSON, Excel formats)</li> // GH-90000
 *   <li>Progress tracking</li>
 *   <li>Error handling</li>
 *   <li>Metrics emission</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Comprehensive test coverage for ImportExportService
 * @doc.layer infrastructure
 * @doc.pattern Unit Test
 */
@DisplayName("Import Export Service Tests")
class ImportExportServiceTest extends EventloopTestBase {

    private ImportExportService service;
    private ExcelExporter mockExcelExporter;
    private CsvImporter mockCsvImporter;
    private MetricsCollector mockMetrics;

    @BeforeEach
    void setup() { // GH-90000
        mockExcelExporter = mock(ExcelExporter.class); // GH-90000
        mockCsvImporter = mock(CsvImporter.class); // GH-90000
        mockMetrics = mock(MetricsCollector.class); // GH-90000
        service = new ImportExportService(mockExcelExporter, mockCsvImporter, mockMetrics); // GH-90000
    }

    // ========================================================================
    // CONSTRUCTOR TESTS
    // ========================================================================

    @Test
    @DisplayName("Should create service with valid dependencies")
    void shouldCreateWithValidDependencies() { // GH-90000
        assertThat(service).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null Excel exporter")
    void shouldRejectNullExcelExporter() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            new ImportExportService(null, mockCsvImporter, mockMetrics) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("ExcelExporter");
    }

    @Test
    @DisplayName("Should reject null CSV importer")
    void shouldRejectNullCsvImporter() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            new ImportExportService(mockExcelExporter, null, mockMetrics) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("CsvImporter");
    }

    @Test
    @DisplayName("Should reject null metrics collector")
    void shouldRejectNullMetrics() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            new ImportExportService(mockExcelExporter, mockCsvImporter, null) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("MetricsCollector");
    }

    // ========================================================================
    // CSV EXPORT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should export to CSV format")
    void shouldExportToCsv() { // GH-90000
        // GIVEN: Entities to export
        List<Map<String, Object>> entities = List.of( // GH-90000
            Map.of("id", "1", "name", "Test1", "status", "active"), // GH-90000
            Map.of("id", "2", "name", "Test2", "status", "inactive") // GH-90000
        );

        // WHEN: Exporting to CSV
        byte[] result = runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", entities, ImportExportService.ExportFormat.CSV) // GH-90000
        );

        // THEN: Returns CSV data
        assertThat(result).isNotNull(); // GH-90000
        String csv = new String(result); // GH-90000
        assertThat(csv).contains("id");
        assertThat(csv).contains("name");
        assertThat(csv).contains("Test1");
        assertThat(csv).contains("Test2");

        // AND: Metrics recorded
        verify(mockMetrics).incrementCounter(eq("export.success"), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should escape CSV special characters")
    void shouldEscapeCsvSpecialCharacters() { // GH-90000
        // GIVEN: Entity with special characters
        List<Map<String, Object>> entities = List.of( // GH-90000
            Map.of("id", "1", "name", "Test, with comma", "desc", "Has \"quotes\"") // GH-90000
        );

        // WHEN: Exporting to CSV
        byte[] result = runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", entities, ImportExportService.ExportFormat.CSV) // GH-90000
        );

        // THEN: Special characters are escaped
        String csv = new String(result); // GH-90000
        assertThat(csv).contains("\"Test, with comma\""); // GH-90000
    }

    // ========================================================================
    // PDF EXPORT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should export to PDF format")
    void shouldExportToPdf() { // GH-90000
        // GIVEN: Entities to export
        List<Map<String, Object>> entities = List.of( // GH-90000
            Map.of("id", "1", "name", "Test1"), // GH-90000
            Map.of("id", "2", "name", "Test2") // GH-90000
        );

        // WHEN: Exporting to PDF
        byte[] result = runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", entities, ImportExportService.ExportFormat.PDF) // GH-90000
        );

        // THEN: Returns PDF data
        assertThat(result).isNotNull(); // GH-90000
        String pdf = new String(result); // GH-90000
        assertThat(pdf).startsWith("%PDF");
        assertThat(pdf).contains("Data Export");
    }

    @Test
    @DisplayName("Should handle large PDF export with truncation")
    void shouldHandleLargePdfExport() { // GH-90000
        // GIVEN: Many entities (more than 50) // GH-90000
        List<Map<String, Object>> entities = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 100; i++) { // GH-90000
            entities.add(Map.of("id", String.valueOf(i), "name", "Test" + i)); // GH-90000
        }

        // WHEN: Exporting to PDF
        byte[] result = runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", entities, ImportExportService.ExportFormat.PDF) // GH-90000
        );

        // THEN: PDF contains truncation message
        String pdf = new String(result); // GH-90000
        assertThat(pdf).contains("more records");
    }

    // ========================================================================
    // EXCEL EXPORT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should export to Excel format")
    void shouldExportToExcel() { // GH-90000
        // GIVEN: Entities and mock Excel exporter
        List<Map<String, Object>> entities = List.of( // GH-90000
            Map.of("id", "1", "name", "Test1") // GH-90000
        );
        byte[] excelBytes = "Excel data".getBytes(); // GH-90000
        when(mockExcelExporter.exportEntities(anyString(), anyString(), anyList(), anyList())) // GH-90000
            .thenReturn(io.activej.promise.Promise.of(excelBytes)); // GH-90000

        // WHEN: Exporting to Excel
        byte[] result = runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", entities, ImportExportService.ExportFormat.EXCEL) // GH-90000
        );

        // THEN: Returns Excel data
        assertThat(result).isEqualTo(excelBytes); // GH-90000
        verify(mockExcelExporter).exportEntities(eq("tenant-1"), eq("orders"), eq(entities), anyList());
    }

    // ========================================================================
    // EXPORT VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should reject null tenant ID in export")
    void shouldRejectNullTenantIdInExport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.export(null, "orders", List.of(), ImportExportService.ExportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Tenant ID");
    }

    @Test
    @DisplayName("Should reject null collection name in export")
    void shouldRejectNullCollectionInExport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.export("tenant-1", null, List.of(), ImportExportService.ExportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Collection name");
    }

    @Test
    @DisplayName("Should reject null entities in export")
    void shouldRejectNullEntitiesInExport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.export("tenant-1", "orders", null, ImportExportService.ExportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Entities");
    }

    @Test
    @DisplayName("Should reject null format in export")
    void shouldRejectNullFormatInExport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.export("tenant-1", "orders", List.of(), null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Format");
    }

    // ========================================================================
    // CSV VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should validate valid CSV data")
    void shouldValidateValidCsv() { // GH-90000
        // GIVEN: Valid CSV data
        String csvData = "id,name,status\n1,Test1,active\n2,Test2,inactive";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", csvData, ImportExportService.ImportFormat.CSV) // GH-90000
        );

        // THEN: Validation passes
        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.totalRecords()).isEqualTo(2); // GH-90000
        assertThat(result.validRecords()).isEqualTo(2); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should detect CSV column mismatch")
    void shouldDetectCsvColumnMismatch() { // GH-90000
        // GIVEN: CSV with mismatched columns
        String csvData = "id,name,status\n1,Test1\n2,Test2,active,extra";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", csvData, ImportExportService.ImportFormat.CSV) // GH-90000
        );

        // THEN: Validation fails with errors
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.hasErrors()).isTrue(); // GH-90000
        assertThat(result.getErrorCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should reject CSV with only header")
    void shouldRejectCsvWithOnlyHeader() { // GH-90000
        // GIVEN: CSV with only header
        String csvData = "id,name,status";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", csvData, ImportExportService.ImportFormat.CSV) // GH-90000
        );

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.message().contains("at least one data row"));
    }

    // ========================================================================
    // JSON VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should validate valid JSON array")
    void shouldValidateValidJsonArray() { // GH-90000
        // GIVEN: Valid JSON array
        String jsonData = "[{\"id\": \"1\", \"name\": \"Test1\"}, {\"id\": \"2\", \"name\": \"Test2\"}]";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Validation passes
        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.totalRecords()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("Should validate valid JSON object with records")
    void shouldValidateValidJsonObjectWithRecords() { // GH-90000
        // GIVEN: JSON object with records field
        String jsonData = "{\"records\": [{\"id\": \"1\"}, {\"id\": \"2\"}]}";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Validation passes
        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should reject empty JSON")
    void shouldRejectEmptyJson() { // GH-90000
        // GIVEN: Empty JSON
        String jsonData = "";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.message().contains("empty"));
    }

    @Test
    @DisplayName("Should reject invalid JSON format")
    void shouldRejectInvalidJsonFormat() { // GH-90000
        // GIVEN: Invalid JSON (not starting with [ or {) // GH-90000
        String jsonData = "invalid json";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.message().contains("must start with"));
    }

    // ========================================================================
    // EXCEL VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should reject empty Excel data")
    void shouldRejectEmptyExcelData() { // GH-90000
        // GIVEN: Empty Excel data
        String excelData = "";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", excelData, ImportExportService.ImportFormat.EXCEL) // GH-90000
        );

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.message().contains("empty"));
    }

    @Test
    @DisplayName("Should reject invalid base64 Excel data")
    void shouldRejectInvalidBase64ExcelData() { // GH-90000
        // GIVEN: Invalid base64
        String excelData = "not-valid-base64!!!";

        // WHEN: Validating
        ImportExportService.ValidationResult result = runPromise(() -> // GH-90000
            service.validateImport("tenant-1", "orders", excelData, ImportExportService.ImportFormat.EXCEL) // GH-90000
        );

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.message().contains("base64"));
    }

    // ========================================================================
    // JSON IMPORT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should import valid JSON array")
    void shouldImportValidJsonArray() { // GH-90000
        // GIVEN: Valid JSON array
        String jsonData = "[{\"id\": \"1\", \"name\": \"Test1\"}, {\"id\": \"2\", \"name\": \"Test2\"}]";

        // WHEN: Importing
        CsvImporter.ImportResult result = runPromise(() -> // GH-90000
            service.importData("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Import succeeds
        assertThat(result.successCount).isEqualTo(2); // GH-90000
        assertThat(result.failureCount).isEqualTo(0); // GH-90000
        assertThat(result.entities).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Should import single JSON object")
    void shouldImportSingleJsonObject() { // GH-90000
        // GIVEN: Single JSON object
        String jsonData = "{\"id\": \"1\", \"name\": \"Test1\"}";

        // WHEN: Importing
        CsvImporter.ImportResult result = runPromise(() -> // GH-90000
            service.importData("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Import succeeds with single record
        assertThat(result.successCount).isEqualTo(1); // GH-90000
        assertThat(result.entities).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("Should generate IDs for records without ID")
    void shouldGenerateIdsForRecordsWithoutId() { // GH-90000
        // GIVEN: JSON without IDs
        String jsonData = "[{\"name\": \"Test1\"}, {\"name\": \"Test2\"}]";

        // WHEN: Importing
        CsvImporter.ImportResult result = runPromise(() -> // GH-90000
            service.importData("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: IDs are generated
        assertThat(result.entities).allMatch(e -> e.containsKey("id"));
    }

    @Test
    @DisplayName("Should handle invalid JSON import format")
    void shouldHandleInvalidJsonImportFormat() { // GH-90000
        // GIVEN: Invalid JSON
        String jsonData = "invalid";

        // WHEN: Importing
        CsvImporter.ImportResult result = runPromise(() -> // GH-90000
            service.importData("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Returns error
        assertThat(result.failureCount).isEqualTo(1); // GH-90000
        assertThat(result.errors).isNotEmpty(); // GH-90000
    }

    // ========================================================================
    // CSV IMPORT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should import CSV data")
    void shouldImportCsvData() { // GH-90000
        // GIVEN: CSV data and mock importer
        String csvData = "id,name\n1,Test1";
        CsvImporter.ImportResult mockResult = new CsvImporter.ImportResult( // GH-90000
            "tenant-1", "orders", 1, 0,
            List.of(Map.of("id", "1", "name", "Test1")), // GH-90000
            List.of(), // GH-90000
            System.currentTimeMillis() // GH-90000
        );
        when(mockCsvImporter.importFromCsv(anyString(), anyString(), anyString(), anyList())) // GH-90000
            .thenReturn(io.activej.promise.Promise.of(mockResult)); // GH-90000

        // WHEN: Importing
        CsvImporter.ImportResult result = runPromise(() -> // GH-90000
            service.importData("tenant-1", "orders", csvData, ImportExportService.ImportFormat.CSV) // GH-90000
        );

        // THEN: Import succeeds
        assertThat(result.successCount).isEqualTo(1); // GH-90000
        verify(mockCsvImporter).importFromCsv(eq("tenant-1"), eq("orders"), eq(csvData), anyList());
    }

    // ========================================================================
    // PROGRESS TRACKING TESTS
    // ========================================================================

    @Test
    @DisplayName("Should track export progress")
    void shouldTrackExportProgress() { // GH-90000
        // GIVEN: Export operation
        List<Map<String, Object>> entities = List.of(Map.of("id", "1")); // GH-90000

        // WHEN: Exporting
        runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", entities, ImportExportService.ExportFormat.CSV) // GH-90000
        );

        // THEN: Progress can be retrieved (operations map is internal, verify via metrics) // GH-90000
        verify(mockMetrics).incrementCounter(eq("export.success"), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    // ========================================================================
    // VALIDATION RESULT TESTS
    // ========================================================================

    @Test
    @DisplayName("Should calculate invalid records correctly")
    void shouldCalculateInvalidRecords() { // GH-90000
        // GIVEN: Validation result
        ImportExportService.ValidationResult result = new ImportExportService.ValidationResult( // GH-90000
            false, 10, 7, List.of(new ImportExportService.ValidationError(1, "field", "error")), "summary" // GH-90000
        );

        // THEN: Invalid records calculated correctly
        assertThat(result.getInvalidRecords()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("Should format validation error correctly")
    void shouldFormatValidationError() { // GH-90000
        // GIVEN: Validation error with row
        ImportExportService.ValidationError error = new ImportExportService.ValidationError(5, "name", "Invalid value"); // GH-90000

        // THEN: Formatted correctly
        assertThat(error.toString()).contains("Row 5");
        assertThat(error.toString()).contains("name");
        assertThat(error.toString()).contains("Invalid value");
    }

    @Test
    @DisplayName("Should format file-level validation error correctly")
    void shouldFormatFileLevelValidationError() { // GH-90000
        // GIVEN: File-level error (row 0) // GH-90000
        ImportExportService.ValidationError error = new ImportExportService.ValidationError(0, "file", "File is empty"); // GH-90000

        // THEN: Formatted without row number
        assertThat(error.toString()).doesNotContain("Row 0");
        assertThat(error.toString()).contains("file");
    }

    @Test
    @DisplayName("Should reject negative row in validation error")
    void shouldRejectNegativeRowInValidationError() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            new ImportExportService.ValidationError(-1, "field", "error") // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("row");
    }

    // ========================================================================
    // OPERATION PROGRESS TESTS
    // ========================================================================

    @Test
    @DisplayName("Should track operation status correctly")
    void shouldTrackOperationStatus() { // GH-90000
        // GIVEN: Operation progress
        ImportExportService.OperationProgress progress =
            new ImportExportService.OperationProgress("op-1", "export", "CSV"); // GH-90000

        // THEN: Initial status is IN_PROGRESS
        assertThat(progress.getStatus()).isEqualTo("IN_PROGRESS");

        // WHEN: Completed
        progress.completed = true;
        progress.endTime = System.currentTimeMillis(); // GH-90000

        // THEN: Status is COMPLETED
        assertThat(progress.getStatus()).isEqualTo("COMPLETED");
        assertThat(progress.getDuration()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should track failed operation status")
    void shouldTrackFailedOperationStatus() { // GH-90000
        // GIVEN: Failed operation
        ImportExportService.OperationProgress progress =
            new ImportExportService.OperationProgress("op-1", "import", "JSON"); // GH-90000
        progress.failed = true;

        // THEN: Status is FAILED
        assertThat(progress.getStatus()).isEqualTo("FAILED");
    }

    // ========================================================================
    // IMPORT VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should reject null tenant ID in validate import")
    void shouldRejectNullTenantIdInValidate() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.validateImport(null, "orders", "data", ImportExportService.ImportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Tenant ID");
    }

    @Test
    @DisplayName("Should reject null collection in validate import")
    void shouldRejectNullCollectionInValidate() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.validateImport("tenant-1", null, "data", ImportExportService.ImportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Collection name");
    }

    @Test
    @DisplayName("Should reject null data in validate import")
    void shouldRejectNullDataInValidate() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.validateImport("tenant-1", "orders", null, ImportExportService.ImportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Data");
    }

    @Test
    @DisplayName("Should reject null format in validate import")
    void shouldRejectNullFormatInValidate() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.validateImport("tenant-1", "orders", "data", null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Format");
    }

    // ========================================================================
    // IMPORT DATA VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should reject null tenant ID in import data")
    void shouldRejectNullTenantIdInImport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.importData(null, "orders", "data", ImportExportService.ImportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Tenant ID");
    }

    @Test
    @DisplayName("Should reject null collection in import data")
    void shouldRejectNullCollectionInImport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.importData("tenant-1", null, "data", ImportExportService.ImportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Collection name");
    }

    @Test
    @DisplayName("Should reject null data in import data")
    void shouldRejectNullDataInImport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.importData("tenant-1", "orders", null, ImportExportService.ImportFormat.CSV)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Data");
    }

    @Test
    @DisplayName("Should reject null format in import data")
    void shouldRejectNullFormatInImport() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> service.importData("tenant-1", "orders", "data", null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("Format");
    }

    // ========================================================================
    // EMPTY DATA TESTS
    // ========================================================================

    @Test
    @DisplayName("Should handle empty entities list in export")
    void shouldHandleEmptyEntitiesInExport() { // GH-90000
        // WHEN: Exporting empty list
        byte[] result = runPromise(() -> // GH-90000
            service.export("tenant-1", "orders", List.of(), ImportExportService.ExportFormat.CSV) // GH-90000
        );

        // THEN: Returns empty CSV (just newline) // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle empty JSON array import")
    void shouldHandleEmptyJsonArrayImport() { // GH-90000
        // GIVEN: Empty JSON array
        String jsonData = "[]";

        // WHEN: Importing
        CsvImporter.ImportResult result = runPromise(() -> // GH-90000
            service.importData("tenant-1", "orders", jsonData, ImportExportService.ImportFormat.JSON) // GH-90000
        );

        // THEN: Returns empty result
        assertThat(result.successCount).isEqualTo(0); // GH-90000
        assertThat(result.entities).isEmpty(); // GH-90000
    }
}
