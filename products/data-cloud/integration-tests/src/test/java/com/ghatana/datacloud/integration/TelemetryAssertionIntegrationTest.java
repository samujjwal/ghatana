/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.infrastructure.importexport.CsvImporter;
import com.ghatana.datacloud.infrastructure.importexport.ExcelExporter;
import com.ghatana.datacloud.infrastructure.importexport.ImportExportService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-A18: Integration-level telemetry assertions for critical export workflow paths.
 *
 * <p>These tests verify that operational journeys emit expected metrics with
 * stable names and dimensions, and that success counters are not emitted on
 * failed export paths.
 *
 * @doc.type class
 * @doc.purpose Integration telemetry assertions for import/export journeys
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Telemetry assertion integration tests")
class TelemetryAssertionIntegrationTest extends EventloopTestBase {

    @Mock
    private ExcelExporter excelExporter;

    @Mock
    private CsvImporter csvImporter;

    @Mock
    private MetricsCollector metricsCollector;

    @Test
    @DisplayName("export CSV journey emits export.success metric with tenant/collection/format tags")
    void exportCsvJourneyEmitsTelemetry() {
        ImportExportService service = new ImportExportService(excelExporter, csvImporter, metricsCollector);
        List<Map<String, Object>> entities = List.of(
                Map.of("id", "1", "name", "alpha"),
                Map.of("id", "2", "name", "beta")
        );

        byte[] bytes = runPromise(() -> service.export(
                "tenant-telemetry",
                "orders",
                entities,
                ImportExportService.ExportFormat.CSV
        ));

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("id");
        assertThat(csv).contains("name");
        assertThat(csv).contains("alpha");

        verify(metricsCollector).incrementCounter(
                "export.success",
                "tenant", "tenant-telemetry",
                "collection", "orders",
                "format", "CSV"
        );
    }

    @Test
    @DisplayName("export PDF journey emits export.success metric with format=PDF")
    void exportPdfJourneyEmitsTelemetry() {
        ImportExportService service = new ImportExportService(excelExporter, csvImporter, metricsCollector);
        List<Map<String, Object>> entities = List.of(Map.of("id", "7", "name", "invoice"));

        byte[] bytes = runPromise(() -> service.export(
                "tenant-telemetry",
                "billing",
                entities,
                ImportExportService.ExportFormat.PDF
        ));

        String pdfText = new String(bytes, StandardCharsets.UTF_8);
        assertThat(pdfText).contains("Data Export - billing");

        verify(metricsCollector).incrementCounter(
                "export.success",
                "tenant", "tenant-telemetry",
                "collection", "billing",
                "format", "PDF"
        );
    }

    @Test
    @DisplayName("export EXCEL journey emits export.success metric after exporter success")
    void exportExcelJourneyEmitsTelemetryOnSuccess() {
        ImportExportService service = new ImportExportService(excelExporter, csvImporter, metricsCollector);
        List<Map<String, Object>> entities = List.of(Map.of("id", "99", "name", "sheet-row"));
        byte[] excelBytes = "excel-binary".getBytes(StandardCharsets.UTF_8);

        when(excelExporter.exportEntities(eq("tenant-telemetry"), eq("analytics"), anyList(), anyList()))
                .thenReturn(Promise.of(excelBytes));

        byte[] actual = runPromise(() -> service.export(
                "tenant-telemetry",
                "analytics",
                entities,
                ImportExportService.ExportFormat.EXCEL
        ));

        assertThat(actual).isEqualTo(excelBytes);

        verify(metricsCollector).incrementCounter(
                "export.success",
                "tenant", "tenant-telemetry",
                "collection", "analytics",
                "format", "EXCEL"
        );
    }

    @Test
    @DisplayName("failed EXCEL export does not emit success metric")
    void failedExcelExportDoesNotEmitSuccessMetric() {
        ImportExportService service = new ImportExportService(excelExporter, csvImporter, metricsCollector);
        List<Map<String, Object>> entities = List.of(Map.of("id", "100", "name", "broken"));

        when(excelExporter.exportEntities(eq("tenant-telemetry"), eq("analytics"), anyList(), anyList()))
                .thenReturn(Promise.ofException(new RuntimeException("Excel backend unavailable")));

        assertThatThrownBy(() -> runPromise(() -> service.export(
                "tenant-telemetry",
                "analytics",
                entities,
                ImportExportService.ExportFormat.EXCEL
        )))
                .hasMessageContaining("Excel backend unavailable");

        verify(metricsCollector, never()).incrementCounter(
                "export.success",
                "tenant", "tenant-telemetry",
                "collection", "analytics",
                "format", "EXCEL"
        );
    }
}
