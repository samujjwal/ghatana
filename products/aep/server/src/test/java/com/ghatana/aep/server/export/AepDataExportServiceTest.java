/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.export;

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
 * Unit tests for {@link AepDataExportService}.
 *
 * @doc.type class
 * @doc.purpose Tests for CSV/JSON/NDJSON export, pagination, and filtering
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepDataExportService")
class AepDataExportServiceTest {

    @Mock
    DataCloudClient client;

    AepDataExportService service;

    @BeforeEach
    void setUp() {
        service = new AepDataExportService(client, new SimpleMeterRegistry());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null DataCloudClient")
    void rejectsNullClient() {
        assertThatThrownBy(() -> new AepDataExportService(null, new SimpleMeterRegistry()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void rejectsNullRegistry() {
        assertThatThrownBy(() -> new AepDataExportService(client, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CSV Export
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CSV Export")
    class CsvExport {

        @Test
        @DisplayName("should include header row with all field names including _id")
        void headersIncludeId() {
            Entity e = entity("p1", Map.of("tenantId", "t1", "name", "Pattern1", "status", "ACTIVE"));
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e)));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("aep_patterns")
                            .format(AepDataExportService.ExportFormat.CSV)
                            .build()).getResult();

            String[] lines = result.content().split("\n");
            assertThat(lines[0]).contains("_id");
            assertThat(lines[0]).contains("name");
            assertThat(lines[0]).contains("status");
        }

        @Test
        @DisplayName("should include entity data in data rows")
        void dataRowsContainEntityValues() {
            Entity e = entity("p1", Map.of("tenantId", "t1", "name", "Pattern1"));
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e)));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("aep_patterns")
                            .format(AepDataExportService.ExportFormat.CSV)
                            .build()).getResult();

            assertThat(result.content()).contains("p1");
            assertThat(result.content()).contains("Pattern1");
        }

        @Test
        @DisplayName("should return empty content for empty collection")
        void emptyCsvForEmptyCollection() {
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of()));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("aep_patterns")
                            .format(AepDataExportService.ExportFormat.CSV)
                            .build()).getResult();

            assertThat(result.content()).isEmpty();
            assertThat(result.rowCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should double-quote values containing commas")
        void quotesValuesWithCommas() {
            String quoted = AepDataExportService.csvQuote("a,b");
            assertThat(quoted).isEqualTo("\"a,b\"");
        }

        @Test
        @DisplayName("should escape double-quotes inside values")
        void escapesDoubleQuotes() {
            String quoted = AepDataExportService.csvQuote("say \"hello\"");
            assertThat(quoted).isEqualTo("\"say \"\"hello\"\"\"");
        }

        @Test
        @DisplayName("should not quote plain values")
        void doesNotQuotePlainValues() {
            assertThat(AepDataExportService.csvQuote("plain")).isEqualTo("plain");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  JSON Export
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON Export")
    class JsonExport {

        @Test
        @DisplayName("should produce a JSON array starting with [ and ending with ]")
        void jsonArrayStructure() {
            Entity e = entity("p1", Map.of("tenantId", "t1", "name", "P1"));
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e)));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("c1")
                            .format(AepDataExportService.ExportFormat.JSON)
                            .build()).getResult();

            assertThat(result.content().trim()).startsWith("[");
            assertThat(result.content().trim()).endsWith("]");
        }

        @Test
        @DisplayName("should include _id field in JSON output")
        void includesIdInJson() {
            Entity e = entity("entity-42", Map.of("tenantId", "t1"));
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e)));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("c1")
                            .format(AepDataExportService.ExportFormat.JSON)
                            .build()).getResult();

            assertThat(result.content()).contains("\"_id\":\"entity-42\"");
        }

        @Test
        @DisplayName("should produce empty JSON array for empty collection")
        void emptyJsonArray() {
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of()));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("c1")
                            .format(AepDataExportService.ExportFormat.JSON)
                            .build()).getResult();

            assertThat(result.content().trim()).isEqualTo("[\n]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NDJSON Export
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NDJSON Export")
    class NdjsonExport {

        @Test
        @DisplayName("should produce one JSON object per line")
        void oneObjectPerLine() {
            Entity e1 = entity("p1", Map.of("tenantId", "t1"));
            Entity e2 = entity("p2", Map.of("tenantId", "t1"));
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e1, e2)));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("c1")
                            .format(AepDataExportService.ExportFormat.NDJSON)
                            .build()).getResult();

            String[] lines = result.content().split("\n");
            assertThat(lines).hasSize(2);
            for (String line : lines) {
                assertThat(line.trim()).startsWith("{").endsWith("}");
            }
        }

        @Test
        @DisplayName("should produce empty string for empty collection")
        void emptyNdjson() {
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of()));

            AepDataExportService.ExportResult result = service.export(
                    AepDataExportService.ExportRequest.builder()
                            .tenantId("t1").collection("c1")
                            .format(AepDataExportService.ExportFormat.NDJSON)
                            .build()).getResult();

            assertThat(result.content()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Row Count & Metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rowCount should equal number of exported entities")
    void rowCountMatchesEntityCount() {
        when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(
                entity("p1", Map.of("tenantId", "t1")),
                entity("p2", Map.of("tenantId", "t1")),
                entity("p3", Map.of("tenantId", "t1"))
        )));

        AepDataExportService.ExportResult result = service.export(
                AepDataExportService.ExportRequest.builder()
                        .tenantId("t1").collection("c1").build()).getResult();

        assertThat(result.rowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("exportedAt should be set in the result")
    void exportedAtIsPresent() {
        when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of()));

        AepDataExportService.ExportResult result = service.export(
                AepDataExportService.ExportRequest.builder()
                        .tenantId("t1").collection("c1").build()).getResult();

        assertThat(result.exportedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ExportRequest Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ExportRequest Validation")
    class ExportRequestValidation {

        @Test
        @DisplayName("should reject null tenantId")
        void rejectsNullTenantId() {
            assertThatThrownBy(() -> AepDataExportService.ExportRequest.builder()
                    .collection("c1").format(AepDataExportService.ExportFormat.JSON).build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null collection")
        void rejectsNullCollection() {
            assertThatThrownBy(() -> AepDataExportService.ExportRequest.builder()
                    .tenantId("t1").format(AepDataExportService.ExportFormat.JSON).build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should default to JSON format when none specified in builder")
        void defaultsToJson() {
            AepDataExportService.ExportRequest req = AepDataExportService.ExportRequest.builder()
                    .tenantId("t1").collection("c1").build();
            assertThat(req.format()).isEqualTo(AepDataExportService.ExportFormat.JSON);
        }

        @Test
        @DisplayName("should default maxRows to 10000 when not specified")
        void defaultMaxRows() {
            AepDataExportService.ExportRequest req = AepDataExportService.ExportRequest.builder()
                    .tenantId("t1").collection("c1").build();
            assertThat(req.maxRows()).isEqualTo(10_000);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    private static Entity entity(String id, Map<String, Object> data) {
        return new Entity(id, "test_collection", data, Instant.now(), Instant.now(), 1L);
    }
}
