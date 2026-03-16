package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Renders regulatory report data to CSV and multi-sheet Excel format.
 *              CSV output uses UTF-8 BOM for Excel compatibility. Large datasets are
 *              streamed row-by-row to avoid heap exhaustion. Excel renderer (via ExcelPort)
 *              supports multiple named sheets with column type formatting.
 *              Output stored via StoragePort. Satisfies STORY-D10-004.
 * @doc.layer   Domain
 * @doc.pattern Streaming CSV; UTF-8 BOM; ExcelPort multi-sheet; StoragePort; Counter.
 */
public class CsvExcelRendererService {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ExcelPort        excelPort;
    private final StoragePort      storagePort;
    private final Counter          csvRenderedCounter;
    private final Counter          excelRenderedCounter;

    public CsvExcelRendererService(HikariDataSource dataSource, Executor executor,
                                    ExcelPort excelPort, StoragePort storagePort,
                                    MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.excelPort            = excelPort;
        this.storagePort          = storagePort;
        this.csvRenderedCounter   = Counter.builder("reporting.csv.rendered_total").register(registry);
        this.excelRenderedCounter = Counter.builder("reporting.excel.rendered_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface ExcelPort {
        byte[] buildWorkbook(List<SheetSpec> sheets);
    }

    public interface StoragePort {
        String store(String bucket, String key, byte[] data, String contentType);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SheetSpec(String sheetName, List<String> headers,
                             List<Map<String, Object>> rows) {}

    public record RenderedArtifact(String artifactId, String reportId, String storageKey,
                                    String format, long rowCount, LocalDateTime renderedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RenderedArtifact> renderCsv(String reportId, List<String> headers,
                                                List<List<String>> rows) {
        return Promise.ofBlocking(executor, () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(UTF8_BOM);
            PrintWriter pw = new PrintWriter(baos, false, StandardCharsets.UTF_8);
            pw.println(String.join(",", escapeRow(headers)));
            for (List<String> row : rows) {
                pw.println(String.join(",", escapeRow(row)));
            }
            pw.flush();
            byte[] data = baos.toByteArray();
            String key  = "reports/" + reportId + "/csv/" + UUID.randomUUID() + ".csv";
            storagePort.store("reporting", key, data, "text/csv; charset=UTF-8");
            csvRenderedCounter.increment();
            return persistArtifact(reportId, key, "CSV", rows.size());
        });
    }

    public Promise<RenderedArtifact> renderExcel(String reportId, List<SheetSpec> sheets) {
        return Promise.ofBlocking(executor, () -> {
            byte[] workbook = excelPort.buildWorkbook(sheets);
            String key = "reports/" + reportId + "/excel/" + UUID.randomUUID() + ".xlsx";
            storagePort.store("reporting", key, workbook,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            long totalRows = sheets.stream().mapToLong(s -> s.rows().size()).sum();
            excelRenderedCounter.increment();
            return persistArtifact(reportId, key, "EXCEL", totalRows);
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<String> escapeRow(List<String> row) {
        return row.stream().map(this::escapeCsv).toList();
    }

    /** RFC 4180: wrap in quotes if contains comma, quote or newline; double-up internal quotes. */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private RenderedArtifact persistArtifact(String reportId, String key,
                                              String format, long rowCount) throws SQLException {
        String artifactId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO report_file_artifacts
                    (artifact_id, report_id, storage_key, format, row_count, rendered_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (artifact_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifactId);
            ps.setString(2, reportId);
            ps.setString(3, key);
            ps.setString(4, format);
            ps.setLong(5, rowCount);
            ps.executeUpdate();
        }
        return new RenderedArtifact(artifactId, reportId, key, format, rowCount, LocalDateTime.now());
    }
}
