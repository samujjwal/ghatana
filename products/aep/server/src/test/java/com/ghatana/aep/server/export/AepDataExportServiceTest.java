/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepDataExportService [GH-90000]")
class AepDataExportServiceTest {

    @Mock
    DataCloudClient client;

    AepDataExportService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new AepDataExportService(client, new SimpleMeterRegistry()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null DataCloudClient [GH-90000]")
    void rejectsNullClient() { // GH-90000
        assertThatThrownBy(() -> new AepDataExportService(null, new SimpleMeterRegistry())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null MeterRegistry [GH-90000]")
    void rejectsNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new AepDataExportService(client, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CSV Export
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CSV Export [GH-90000]")
    class CsvExport {

        @Test
        @DisplayName("should include header row with all field names including _id [GH-90000]")
        void headersIncludeId() { // GH-90000
            Entity e = entity("p1", Map.of("tenantId", "t1", "name", "Pattern1", "status", "ACTIVE")); // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e))); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("aep_patterns [GH-90000]")
                            .format(AepDataExportService.ExportFormat.CSV) // GH-90000
                            .build()).getResult(); // GH-90000

            String[] lines = result.content().split("\n [GH-90000]");
            assertThat(lines[0]).contains("_id [GH-90000]");
            assertThat(lines[0]).contains("name [GH-90000]");
            assertThat(lines[0]).contains("status [GH-90000]");
        }

        @Test
        @DisplayName("should include entity data in data rows [GH-90000]")
        void dataRowsContainEntityValues() { // GH-90000
            Entity e = entity("p1", Map.of("tenantId", "t1", "name", "Pattern1")); // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e))); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("aep_patterns [GH-90000]")
                            .format(AepDataExportService.ExportFormat.CSV) // GH-90000
                            .build()).getResult(); // GH-90000

            assertThat(result.content()).contains("p1 [GH-90000]");
            assertThat(result.content()).contains("Pattern1 [GH-90000]");
        }

        @Test
        @DisplayName("should return empty content for empty collection [GH-90000]")
        void emptyCsvForEmptyCollection() { // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of())); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("aep_patterns [GH-90000]")
                            .format(AepDataExportService.ExportFormat.CSV) // GH-90000
                            .build()).getResult(); // GH-90000

            assertThat(result.content()).isEmpty(); // GH-90000
            assertThat(result.rowCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should double-quote values containing commas [GH-90000]")
        void quotesValuesWithCommas() { // GH-90000
            String quoted = AepDataExportService.csvQuote("a,b [GH-90000]");
            assertThat(quoted).isEqualTo("\"a,b\""); // GH-90000
        }

        @Test
        @DisplayName("should escape double-quotes inside values [GH-90000]")
        void escapesDoubleQuotes() { // GH-90000
            String quoted = AepDataExportService.csvQuote("say \"hello\""); // GH-90000
            assertThat(quoted).isEqualTo("\"say \"\"hello\"\"\""); // GH-90000
        }

        @Test
        @DisplayName("should not quote plain values [GH-90000]")
        void doesNotQuotePlainValues() { // GH-90000
            assertThat(AepDataExportService.csvQuote("plain [GH-90000]")).isEqualTo("plain [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  JSON Export
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON Export [GH-90000]")
    class JsonExport {

        @Test
        @DisplayName("should produce a JSON array starting with [ and ending with ] [GH-90000]")
        void jsonArrayStructure() { // GH-90000
            Entity e = entity("p1", Map.of("tenantId", "t1", "name", "P1")); // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e))); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]")
                            .format(AepDataExportService.ExportFormat.JSON) // GH-90000
                            .build()).getResult(); // GH-90000

            assertThat(result.content().trim()).startsWith("[ [GH-90000]");
            assertThat(result.content().trim()).endsWith("] [GH-90000]");
        }

        @Test
        @DisplayName("should include _id field in JSON output [GH-90000]")
        void includesIdInJson() { // GH-90000
            Entity e = entity("entity-42", Map.of("tenantId", "t1")); // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e))); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]")
                            .format(AepDataExportService.ExportFormat.JSON) // GH-90000
                            .build()).getResult(); // GH-90000

            assertThat(result.content()).contains("\"_id\":\"entity-42\""); // GH-90000
        }

        @Test
        @DisplayName("should produce empty JSON array for empty collection [GH-90000]")
        void emptyJsonArray() { // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of())); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]")
                            .format(AepDataExportService.ExportFormat.JSON) // GH-90000
                            .build()).getResult(); // GH-90000

            assertThat(result.content().trim()).isEqualTo("[\n] [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NDJSON Export
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NDJSON Export [GH-90000]")
    class NdjsonExport {

        @Test
        @DisplayName("should produce one JSON object per line [GH-90000]")
        void oneObjectPerLine() { // GH-90000
            Entity e1 = entity("p1", Map.of("tenantId", "t1")); // GH-90000
            Entity e2 = entity("p2", Map.of("tenantId", "t1")); // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of(e1, e2))); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]")
                            .format(AepDataExportService.ExportFormat.NDJSON) // GH-90000
                            .build()).getResult(); // GH-90000

            String[] lines = result.content().split("\n [GH-90000]");
            assertThat(lines).hasSize(2); // GH-90000
            for (String line : lines) { // GH-90000
                assertThat(line.trim()).startsWith("{ [GH-90000]").endsWith("} [GH-90000]");
            }
        }

        @Test
        @DisplayName("should produce empty string for empty collection [GH-90000]")
        void emptyNdjson() { // GH-90000
            when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of())); // GH-90000

            AepDataExportService.ExportResult result = service.export( // GH-90000
                    AepDataExportService.ExportRequest.builder() // GH-90000
                            .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]")
                            .format(AepDataExportService.ExportFormat.NDJSON) // GH-90000
                            .build()).getResult(); // GH-90000

            assertThat(result.content()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Row Count & Metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rowCount should equal number of exported entities [GH-90000]")
    void rowCountMatchesEntityCount() { // GH-90000
        when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of( // GH-90000
                entity("p1", Map.of("tenantId", "t1")), // GH-90000
                entity("p2", Map.of("tenantId", "t1")), // GH-90000
                entity("p3", Map.of("tenantId", "t1")) // GH-90000
        )));

        AepDataExportService.ExportResult result = service.export( // GH-90000
                AepDataExportService.ExportRequest.builder() // GH-90000
                        .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]").build()).getResult();

        assertThat(result.rowCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("exportedAt should be set in the result [GH-90000]")
    void exportedAtIsPresent() { // GH-90000
        when(client.query(any(), any(), any())).thenReturn(Promise.of(List.of())); // GH-90000

        AepDataExportService.ExportResult result = service.export( // GH-90000
                AepDataExportService.ExportRequest.builder() // GH-90000
                        .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]").build()).getResult();

        assertThat(result.exportedAt()).isNotNull(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ExportRequest Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ExportRequest Validation [GH-90000]")
    class ExportRequestValidation {

        @Test
        @DisplayName("should reject null tenantId [GH-90000]")
        void rejectsNullTenantId() { // GH-90000
            assertThatThrownBy(() -> AepDataExportService.ExportRequest.builder() // GH-90000
                    .collection("c1 [GH-90000]").format(AepDataExportService.ExportFormat.JSON).build())
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject null collection [GH-90000]")
        void rejectsNullCollection() { // GH-90000
            assertThatThrownBy(() -> AepDataExportService.ExportRequest.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").format(AepDataExportService.ExportFormat.JSON).build())
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should default to JSON format when none specified in builder [GH-90000]")
        void defaultsToJson() { // GH-90000
            AepDataExportService.ExportRequest req = AepDataExportService.ExportRequest.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]").build();
            assertThat(req.format()).isEqualTo(AepDataExportService.ExportFormat.JSON); // GH-90000
        }

        @Test
        @DisplayName("should default maxRows to 10000 when not specified [GH-90000]")
        void defaultMaxRows() { // GH-90000
            AepDataExportService.ExportRequest req = AepDataExportService.ExportRequest.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").collection("c1 [GH-90000]").build();
            assertThat(req.maxRows()).isEqualTo(10_000); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    private static Entity entity(String id, Map<String, Object> data) { // GH-90000
        return new Entity(id, "test_collection", data, Instant.now(), Instant.now(), 1L); // GH-90000
    }
}
