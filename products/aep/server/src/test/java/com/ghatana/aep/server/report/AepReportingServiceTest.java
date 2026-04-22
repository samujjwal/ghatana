/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.report;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepReportingService}.
 *
 * @doc.type class
 * @doc.purpose Tests for all report types and report structure
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepReportingService [GH-90000]")
class AepReportingServiceTest {

    @Mock
    DataCloudClient client;

    AepReportingService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new AepReportingService(client, new SimpleMeterRegistry()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null DataCloudClient [GH-90000]")
    void rejectsNullClient() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService(null, new SimpleMeterRegistry())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null MeterRegistry [GH-90000]")
    void rejectsNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService(client, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReportRequest Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null tenantId in ReportRequest [GH-90000]")
    void rejectsNullTenantId() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService.ReportRequest( // GH-90000
                null, AepReportingService.ReportType.KPI_SUMMARY, null, null))
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null reportType in ReportRequest [GH-90000]")
    void rejectsNullReportType() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService.ReportRequest("t1", null, null, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  KPI_SUMMARY
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("KPI_SUMMARY Report [GH-90000]")
    class KpiSummaryReport {

        @Test
        @DisplayName("should produce one section with one unique KPI row each [GH-90000]")
        void producesKpiRows() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_kpi_snapshots [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            kpi("k1", "t1", "Revenue", 1500.0, "2026-01-01T00:00:00Z"), // GH-90000
                            kpi("k2", "t1", "Sessions", 300.0, "2026-01-01T01:00:00Z") // GH-90000
                    )));

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.KPI_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.sections()).hasSize(1); // GH-90000
            AepReportingService.ReportSection section = report.sections().get(0); // GH-90000
            assertThat(section.rows()).hasSize(2); // GH-90000
            assertThat(section.rows().stream().map(r -> r.get("kpiName [GH-90000]")))
                    .containsExactlyInAnyOrder("Revenue", "Sessions"); // GH-90000
        }

        @Test
        @DisplayName("should keep only the latest record per KPI name [GH-90000]")
        void keepsLatestPerKpi() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_kpi_snapshots [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            kpi("k1", "t1", "Revenue", 1000.0, "2026-01-01T00:00:00Z"), // GH-90000
                            kpi("k2", "t1", "Revenue", 1500.0, "2026-01-01T06:00:00Z")  // newer // GH-90000
                    )));

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.KPI_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            AepReportingService.ReportSection section = report.sections().get(0); // GH-90000
            // Only one row for "Revenue" — the latest one
            assertThat(section.rows()).hasSize(1); // GH-90000
            assertThat(section.rows().get(0).get("value [GH-90000]")).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("should return empty rows when no KPI data exists [GH-90000]")
        void emptyKpiSection() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_kpi_snapshots [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.KPI_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.sections().get(0).rows()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ANOMALY_SUMMARY
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ANOMALY_SUMMARY Report [GH-90000]")
    class AnomalySummaryReport {

        @Test
        @DisplayName("should produce two sections: by severity and by status [GH-90000]")
        void producesTwoSections() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_anomalies [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            anomaly("a1", "t1", "HIGH",   "OPEN"), // GH-90000
                            anomaly("a2", "t1", "HIGH",   "OPEN"), // GH-90000
                            anomaly("a3", "t1", "MEDIUM", "CLOSED") // GH-90000
                    )));

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.ANOMALY_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.sections()).hasSize(2); // GH-90000
            assertThat(report.sections().get(0).title()).isEqualTo("By Severity [GH-90000]");
            assertThat(report.sections().get(1).title()).isEqualTo("By Status [GH-90000]");
        }

        @Test
        @DisplayName("should count anomalies correctly by severity [GH-90000]")
        void countsBySeverity() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_anomalies [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            anomaly("a1", "t1", "HIGH", "OPEN"), // GH-90000
                            anomaly("a2", "t1", "HIGH", "OPEN"), // GH-90000
                            anomaly("a3", "t1", "LOW",  "OPEN") // GH-90000
                    )));

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.ANOMALY_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            List<Map<String, Object>> severityRows = report.sections().get(0).rows(); // GH-90000
            Map<String, Object> highRow = severityRows.stream() // GH-90000
                    .filter(r -> "HIGH".equals(r.get("severity [GH-90000]")))
                    .findFirst().orElseThrow(); // GH-90000
            assertThat(highRow.get("count [GH-90000]")).isEqualTo(2L);
        }

        @Test
        @DisplayName("summary should mention total and open anomaly counts [GH-90000]")
        void summaryContainsCounts() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_anomalies [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            anomaly("a1", "t1", "HIGH", "OPEN"), // GH-90000
                            anomaly("a2", "t1", "LOW",  "CLOSED") // GH-90000
                    )));

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.ANOMALY_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.summary()).contains("Total anomalies: 2 [GH-90000]");
            assertThat(report.summary()).contains("Open: 1 [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TENANT_USAGE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TENANT_USAGE Report [GH-90000]")
    class TenantUsageReport {

        @Test
        @DisplayName("should produce one section with four collection rows [GH-90000]")
        void fourCollectionRows() { // GH-90000
            stubAllCollectionsEmpty("t1 [GH-90000]");

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.TENANT_USAGE,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.sections()).hasSize(1); // GH-90000
            assertThat(report.sections().get(0).rows()).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("should reflect entity counts from DataCloud [GH-90000]")
        void reflectsEntityCounts() { // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_patterns [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entity("p1", Map.of("tenantId", "t1")), // GH-90000
                            entity("p2", Map.of("tenantId", "t1"))))); // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_anomalies [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(client.query(eq("t1 [GH-90000]"), eq("aep_kpi_snapshots [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.TENANT_USAGE,
                            null, null))
                    .getResult(); // GH-90000

            Map<String, Object> patternsRow = report.sections().get(0).rows().stream() // GH-90000
                    .filter(r -> "aep_patterns".equals(r.get("collection [GH-90000]")))
                    .findFirst().orElseThrow(); // GH-90000
            assertThat(patternsRow.get("count [GH-90000]")).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SYSTEM_HEALTH (composite) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SYSTEM_HEALTH Report [GH-90000]")
    class SystemHealthReport {

        @Test
        @DisplayName("should combine sections from KPI, Anomaly, and Tenant Usage reports [GH-90000]")
        void combinesSubReports() { // GH-90000
            stubAllCollectionsEmpty("t1 [GH-90000]");

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.SYSTEM_HEALTH,
                            null, null))
                    .getResult(); // GH-90000

            // KPI → 1 section, ANOMALY → 2 sections, TENANT_USAGE → 1 section = 4 total
            assertThat(report.sections()).hasSize(4); // GH-90000
            assertThat(report.summary()).contains("System Health [GH-90000]");
        }

        @Test
        @DisplayName("report type should be SYSTEM_HEALTH [GH-90000]")
        void reportTypeIsSystemHealth() { // GH-90000
            stubAllCollectionsEmpty("t1 [GH-90000]");

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.SYSTEM_HEALTH,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.reportType()).isEqualTo(AepReportingService.ReportType.SYSTEM_HEALTH); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Report Metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("report should include tenantId, reportType, and generatedAt [GH-90000]")
    void reportHasMetadata() { // GH-90000
        when(client.query(eq("t1 [GH-90000]"), eq("aep_kpi_snapshots [GH-90000]"), any()))
                .thenReturn(Promise.of(List.of())); // GH-90000

        AepReportingService.Report report = service.generate( // GH-90000
                new AepReportingService.ReportRequest( // GH-90000
                        "t1", AepReportingService.ReportType.KPI_SUMMARY,
                        Instant.parse("2026-01-01T00:00:00Z [GH-90000]"),
                        Instant.parse("2026-01-31T00:00:00Z [GH-90000]")))
                .getResult(); // GH-90000

        assertThat(report.tenantId()).isEqualTo("t1 [GH-90000]");
        assertThat(report.reportType()).isEqualTo(AepReportingService.ReportType.KPI_SUMMARY); // GH-90000
        assertThat(report.generatedAt()).isNotNull(); // GH-90000
        assertThat(report.from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z [GH-90000]"));
        assertThat(report.to()).isEqualTo(Instant.parse("2026-01-31T00:00:00Z [GH-90000]"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void stubAllCollectionsEmpty(String tenantId) { // GH-90000
        when(client.query(eq(tenantId), any(), any())) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000
    }

    private static Entity entity(String id, Map<String, Object> data) { // GH-90000
        return new Entity(id, "test_collection", data, Instant.now(), Instant.now(), 1L); // GH-90000
    }

    private static Entity kpi(String id, String tenantId, String name, double value, String recordedAt) { // GH-90000
        return entity(id, Map.of( // GH-90000
                "tenantId", tenantId,
                "kpiName", name,
                "value", value,
                "unit", "units",
                "recordedAt", recordedAt
        ));
    }

    private static Entity anomaly(String id, String tenantId, String severity, String status) { // GH-90000
        return entity(id, Map.of( // GH-90000
                "tenantId", tenantId,
                "severity", severity,
                "status", status,
                "kpiName", "SomeKpi",
                "detectedAt", Instant.now().toString() // GH-90000
        ));
    }
}
