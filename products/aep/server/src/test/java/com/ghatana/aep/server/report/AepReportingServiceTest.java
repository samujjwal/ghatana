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
@DisplayName("AepReportingService")
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
    @DisplayName("should reject null DataCloudClient")
    void rejectsNullClient() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService(null, new SimpleMeterRegistry())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void rejectsNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService(client, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReportRequest Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null tenantId in ReportRequest")
    void rejectsNullTenantId() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService.ReportRequest( // GH-90000
                null, AepReportingService.ReportType.KPI_SUMMARY, null, null))
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null reportType in ReportRequest")
    void rejectsNullReportType() { // GH-90000
        assertThatThrownBy(() -> new AepReportingService.ReportRequest("t1", null, null, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  KPI_SUMMARY
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("KPI_SUMMARY Report")
    class KpiSummaryReport {

        @Test
        @DisplayName("should produce one section with one unique KPI row each")
        void producesKpiRows() { // GH-90000
            when(client.query(eq("t1"), eq("aep_kpi_snapshots"), any()))
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
            assertThat(section.rows().stream().map(r -> r.get("kpiName")))
                    .containsExactlyInAnyOrder("Revenue", "Sessions"); // GH-90000
        }

        @Test
        @DisplayName("should keep only the latest record per KPI name")
        void keepsLatestPerKpi() { // GH-90000
            when(client.query(eq("t1"), eq("aep_kpi_snapshots"), any()))
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
            assertThat(section.rows().get(0).get("value")).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("should return empty rows when no KPI data exists")
        void emptyKpiSection() { // GH-90000
            when(client.query(eq("t1"), eq("aep_kpi_snapshots"), any()))
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
    @DisplayName("ANOMALY_SUMMARY Report")
    class AnomalySummaryReport {

        @Test
        @DisplayName("should produce two sections: by severity and by status")
        void producesTwoSections() { // GH-90000
            when(client.query(eq("t1"), eq("aep_anomalies"), any()))
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
            assertThat(report.sections().get(0).title()).isEqualTo("By Severity");
            assertThat(report.sections().get(1).title()).isEqualTo("By Status");
        }

        @Test
        @DisplayName("should count anomalies correctly by severity")
        void countsBySeverity() { // GH-90000
            when(client.query(eq("t1"), eq("aep_anomalies"), any()))
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
                    .filter(r -> "HIGH".equals(r.get("severity")))
                    .findFirst().orElseThrow(); // GH-90000
            assertThat(highRow.get("count")).isEqualTo(2L);
        }

        @Test
        @DisplayName("summary should mention total and open anomaly counts")
        void summaryContainsCounts() { // GH-90000
            when(client.query(eq("t1"), eq("aep_anomalies"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            anomaly("a1", "t1", "HIGH", "OPEN"), // GH-90000
                            anomaly("a2", "t1", "LOW",  "CLOSED") // GH-90000
                    )));

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.ANOMALY_SUMMARY,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.summary()).contains("Total anomalies: 2");
            assertThat(report.summary()).contains("Open: 1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TENANT_USAGE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TENANT_USAGE Report")
    class TenantUsageReport {

        @Test
        @DisplayName("should produce one section with four collection rows")
        void fourCollectionRows() { // GH-90000
            stubAllCollectionsEmpty("t1");

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.TENANT_USAGE,
                            null, null))
                    .getResult(); // GH-90000

            assertThat(report.sections()).hasSize(1); // GH-90000
            assertThat(report.sections().get(0).rows()).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("should reflect entity counts from DataCloud")
        void reflectsEntityCounts() { // GH-90000
            when(client.query(eq("t1"), eq("aep_patterns"), any()))
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entity("p1", Map.of("tenantId", "t1")), // GH-90000
                            entity("p2", Map.of("tenantId", "t1"))))); // GH-90000
            when(client.query(eq("t1"), eq("aep_pipelines"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(client.query(eq("t1"), eq("aep_anomalies"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(client.query(eq("t1"), eq("aep_kpi_snapshots"), any()))
                    .thenReturn(Promise.of(List.of())); // GH-90000

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.TENANT_USAGE,
                            null, null))
                    .getResult(); // GH-90000

            Map<String, Object> patternsRow = report.sections().get(0).rows().stream() // GH-90000
                    .filter(r -> "aep_patterns".equals(r.get("collection")))
                    .findFirst().orElseThrow(); // GH-90000
            assertThat(patternsRow.get("count")).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SYSTEM_HEALTH (composite) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SYSTEM_HEALTH Report")
    class SystemHealthReport {

        @Test
        @DisplayName("should combine sections from KPI, Anomaly, and Tenant Usage reports")
        void combinesSubReports() { // GH-90000
            stubAllCollectionsEmpty("t1");

            AepReportingService.Report report = service.generate( // GH-90000
                    new AepReportingService.ReportRequest( // GH-90000
                            "t1", AepReportingService.ReportType.SYSTEM_HEALTH,
                            null, null))
                    .getResult(); // GH-90000

            // KPI → 1 section, ANOMALY → 2 sections, TENANT_USAGE → 1 section = 4 total
            assertThat(report.sections()).hasSize(4); // GH-90000
            assertThat(report.summary()).contains("System Health");
        }

        @Test
        @DisplayName("report type should be SYSTEM_HEALTH")
        void reportTypeIsSystemHealth() { // GH-90000
            stubAllCollectionsEmpty("t1");

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
    @DisplayName("report should include tenantId, reportType, and generatedAt")
    void reportHasMetadata() { // GH-90000
        when(client.query(eq("t1"), eq("aep_kpi_snapshots"), any()))
                .thenReturn(Promise.of(List.of())); // GH-90000

        AepReportingService.Report report = service.generate( // GH-90000
                new AepReportingService.ReportRequest( // GH-90000
                        "t1", AepReportingService.ReportType.KPI_SUMMARY,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-31T00:00:00Z")))
                .getResult(); // GH-90000

        assertThat(report.tenantId()).isEqualTo("t1");
        assertThat(report.reportType()).isEqualTo(AepReportingService.ReportType.KPI_SUMMARY); // GH-90000
        assertThat(report.generatedAt()).isNotNull(); // GH-90000
        assertThat(report.from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(report.to()).isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));
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
