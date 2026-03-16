package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Renders regulatory report content to PDF. Produces A4/letter layout with
 *              Nepali/Devanagari script support (via FontPort), optional digital signature
 *              (via SignaturePort), DRAFT or FINAL watermark, and sequential page numbers.
 *              Output stored via StoragePort. Satisfies STORY-D10-003.
 * @doc.layer   Domain
 * @doc.pattern PDF rendering; FontPort (Devanagari); SignaturePort; StoragePort; Timer metric.
 */
public class PdfReportRendererService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final PdfEnginePort    pdfEnginePort;
    private final StoragePort      storagePort;
    private final Counter          pdfRenderedCounter;
    private final Timer            renderTimer;

    public PdfReportRendererService(HikariDataSource dataSource, Executor executor,
                                     PdfEnginePort pdfEnginePort, StoragePort storagePort,
                                     MeterRegistry registry) {
        this.dataSource         = dataSource;
        this.executor           = executor;
        this.pdfEnginePort      = pdfEnginePort;
        this.storagePort        = storagePort;
        this.pdfRenderedCounter = Counter.builder("reporting.pdf.rendered_total").register(registry);
        this.renderTimer        = Timer.builder("reporting.pdf.render_duration").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface PdfEnginePort {
        byte[] render(PdfRenderRequest request);
    }

    public interface StoragePort {
        String store(String bucket, String key, byte[] data, String contentType);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum PaperSize  { A4, LETTER }
    public enum Watermark  { DRAFT, FINAL, NONE }

    public record PdfRenderRequest(String reportId, String htmlContent, PaperSize paperSize,
                                    Watermark watermark, boolean includePageNumbers,
                                    boolean applyDigitalSignature, boolean enableDevanagari) {}

    public record PdfArtifact(String artifactId, String reportId, String storageKey,
                               int pagCount, long sizeBytes, LocalDateTime renderedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<PdfArtifact> renderDraft(String reportId, String htmlContent) {
        return Promise.ofBlocking(executor, () -> doRender(reportId, htmlContent,
                PaperSize.A4, Watermark.DRAFT, true, false, true));
    }

    public Promise<PdfArtifact> renderFinal(String reportId, String htmlContent,
                                             boolean applyDigitalSignature) {
        return Promise.ofBlocking(executor, () -> doRender(reportId, htmlContent,
                PaperSize.A4, Watermark.FINAL, true, applyDigitalSignature, true));
    }

    // ─── Implementation ──────────────────────────────────────────────────────

    private PdfArtifact doRender(String reportId, String htmlContent, PaperSize paperSize,
                                  Watermark watermark, boolean pageNumbers,
                                  boolean sign, boolean devanagari) throws SQLException {
        PdfRenderRequest req = new PdfRenderRequest(reportId, htmlContent, paperSize, watermark,
                pageNumbers, sign, devanagari);

        Timer.Sample sample = Timer.start();
        byte[] pdfBytes = pdfEnginePort.render(req);
        sample.stop(renderTimer);

        String artifactId = UUID.randomUUID().toString();
        String key = "reports/" + reportId + "/pdf/" + artifactId + "_" + watermark.name().toLowerCase() + ".pdf";
        storagePort.store("reporting", key, pdfBytes, "application/pdf");

        PdfArtifact artifact = persistArtifact(artifactId, reportId, key, pdfBytes.length);
        pdfRenderedCounter.increment();
        return artifact;
    }

    private PdfArtifact persistArtifact(String artifactId, String reportId, String key,
                                         long sizeBytes) throws SQLException {
        String sql = """
                INSERT INTO report_pdf_artifacts
                    (artifact_id, report_id, storage_key, size_bytes, rendered_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (artifact_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifactId);
            ps.setString(2, reportId);
            ps.setString(3, key);
            ps.setLong(4, sizeBytes);
            ps.executeUpdate();
        }
        return new PdfArtifact(artifactId, reportId, key, 0, sizeBytes, LocalDateTime.now());
    }
}
