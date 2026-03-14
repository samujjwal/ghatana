package com.ghatana.appplatform.audit.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.audit.chain.HashChainService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Generates a cryptographically documented PDF evidence package for compliance audits.
 *
 * <p>The output PDF contains three sections:
 * <ol>
 *   <li><strong>Cover page</strong> — export metadata: tenant, date range, requester, purpose.</li>
 *   <li><strong>Audit evidence table</strong> — paginated rows with dual-calendar timestamps
 *       (Gregorian + BS), actor, action, resource, and outcome.</li>
 *   <li><strong>Hash chain summary</strong> — genesis hash, last hash, total entry count,
 *       and a chain-integrity attestation note.</li>
 * </ol>
 *
 * <p>Row count is capped at {@value #MAX_EXPORT_ROWS} to prevent OOM on unbounded date ranges.
 * Use a smaller date window or the CSV/NDJSON export for bulk extracts.
 *
 * @doc.type class
 * @doc.purpose PDF evidence package generator for compliance audit exports (STORY-K07-010)
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuditEvidencePdfGenerator {

    private static final Logger LOG = Logger.getLogger(AuditEvidencePdfGenerator.class.getName());

    /** Safety cap: throw rather than build a multi-GB PDF. */
    static final int MAX_EXPORT_ROWS = 50_000;

    private static final int ROWS_PER_PAGE = 25;

    // ── Page geometry (A4) ────────────────────────────────────────────────────
    private static final float PAGE_WIDTH   = PDRectangle.A4.getWidth();   // 595.28
    private static final float PAGE_HEIGHT  = PDRectangle.A4.getHeight();  // 841.89
    private static final float MARGIN       = 50f;
    private static final float CONTENT_W    = PAGE_WIDTH - 2 * MARGIN;     // 495.28

    // ── Table column widths (sum = CONTENT_W) ────────────────────────────────
    private static final float[] COL_WIDTHS = { 35f, 90f, 65f, 80f, 85f, 75f, 65f };
    private static final String[] COL_HEADERS = {
        "Seq#", "Gregorian Date", "BS Date", "Actor", "Action", "Resource", "Outcome"
    };

    private static final float ROW_HEIGHT   = 16f;
    private static final float HEADER_H     = 20f;

    // ── Colours (greyscale via grey fill) ────────────────────────────────────
    private static final float[] HEADER_BG = { 0.2f, 0.2f, 0.2f };   // dark grey
    private static final float[] ALT_ROW_BG = { 0.94f, 0.94f, 0.94f };

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final DataSource dataSource;
    private final HashChainService hashChainService;
    private final ObjectMapper jsonMapper;

    /**
     * @param dataSource       JDBC data source for the audit_logs table
     * @param hashChainService for hash display in the chain summary page
     */
    public AuditEvidencePdfGenerator(DataSource dataSource, HashChainService hashChainService) {
        this.dataSource       = dataSource;
        this.hashChainService = hashChainService;
        this.jsonMapper       = new ObjectMapper();
    }

    // ── Internal row model ───────────────────────────────────────────────────

    /**
     * Flat row read from {@code audit_logs} for rendering.
     *
     * @param seqNum            per-tenant sequence number
     * @param gregorian         Gregorian timestamp string (UTC ISO)
     * @param bsDate            Bikram Sambat date string (may be empty)
     * @param actorId           actor user_id extracted from JSONB
     * @param action            event action / type
     * @param resourceLabel     "type/id" extracted from JSONB
     * @param outcome           SUCCESS / FAILURE / PARTIAL
     * @param currentHash       SHA-256 hash of this entry
     */
    record AuditRow(
        long seqNum,
        String gregorian,
        String bsDate,
        String actorId,
        String action,
        String resourceLabel,
        String outcome,
        String currentHash
    ) {}

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Generate a PDF evidence package and write it to {@code output}.
     *
     * @param tenantId    tenant scope
     * @param from        range start (inclusive)
     * @param to          range end (inclusive)
     * @param requestedBy user or system that requested the export
     * @param purpose     free-text statement of purpose for the cover page
     * @param output      destination stream (not closed by this method)
     * @throws IllegalStateException if the row count exceeds {@value #MAX_EXPORT_ROWS}
     */
    public void generate(String tenantId, Instant from, Instant to,
                         String requestedBy, String purpose, OutputStream output) {
        List<AuditRow> rows = fetchRows(tenantId, from, to);
        LOG.info("[AuditEvidencePdfGenerator] Rendering PDF tenant=" + tenantId
            + " rows=" + rows.size());
        try (PDDocument doc = new PDDocument()) {
            applyDocumentMetadata(doc, tenantId, from, to, requestedBy);
            writeCoverPage(doc, tenantId, from, to, requestedBy, purpose, rows.size());
            writeDataPages(doc, rows);
            writeHashSummaryPage(doc, rows);
            doc.save(output);
        } catch (IOException e) {
            throw new UncheckedIOException("PDF evidence generation failed for tenant=" + tenantId, e);
        }
    }

    // ── JDBC fetch ───────────────────────────────────────────────────────────

    private List<AuditRow> fetchRows(String tenantId, Instant from, Instant to) {
        String sql = """
            SELECT sequence_number, timestamp_gregorian, timestamp_bs,
                   actor, action, resource, outcome, current_hash
              FROM audit_logs
             WHERE tenant_id = ?
               AND timestamp_gregorian >= ?
               AND timestamp_gregorian <= ?
             ORDER BY sequence_number ASC
            """;
        List<AuditRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            ps.setFetchSize(500);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rows.size() >= MAX_EXPORT_ROWS) {
                        throw new IllegalStateException(
                            "PDF export row limit (" + MAX_EXPORT_ROWS + ") exceeded for tenant="
                            + tenantId + ". Use a smaller date range or CSV/NDJSON export.");
                    }
                    rows.add(toRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Audit PDF fetch failed for tenant=" + tenantId, e);
        }
        return rows;
    }

    private AuditRow toRow(ResultSet rs) throws SQLException {
        long seq      = rs.getLong("sequence_number");
        String greg   = formatTimestamp(rs.getTimestamp("timestamp_gregorian"));
        String bs     = rs.getString("timestamp_bs");
        String action = rs.getString("action");
        String hash   = rs.getString("current_hash");
        String outcome = rs.getString("outcome");

        String actorId       = extractJsonField(rs.getString("actor"), "user_id");
        String resourceLabel = extractResourceLabel(rs.getString("resource"));

        return new AuditRow(seq, greg, bs == null ? "" : bs, actorId, action, resourceLabel, outcome, hash);
    }

    private String extractJsonField(String jsonb, String field) {
        if (jsonb == null || jsonb.isBlank()) return "";
        try {
            Map<String, Object> map = jsonMapper.readValue(jsonb,
                new TypeReference<Map<String, Object>>() {});
            Object v = map.get(field);
            return v == null ? "" : v.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractResourceLabel(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) return "";
        try {
            Map<String, Object> map = jsonMapper.readValue(jsonb,
                new TypeReference<Map<String, Object>>() {});
            String type = objStr(map.get("type"));
            String id   = objStr(map.get("id"));
            return type.isEmpty() ? id : (id.isEmpty() ? type : type + "/" + truncate(id, 12));
        } catch (Exception e) {
            return "";
        }
    }

    private static String objStr(Object o) { return o == null ? "" : o.toString(); }

    private static String formatTimestamp(Timestamp ts) {
        if (ts == null) return "";
        return TS_FMT.format(ts.toInstant());
    }

    // ── PDF writing helpers ──────────────────────────────────────────────────

    private void applyDocumentMetadata(PDDocument doc, String tenantId,
                                        Instant from, Instant to, String requestedBy) {
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle("Audit Evidence Package — " + tenantId);
        info.setSubject("Compliance audit export " + TS_FMT.format(from) + " to " + TS_FMT.format(to));
        info.setAuthor(requestedBy);
        info.setCreator("Ghatana AuditEvidencePdfGenerator");
        info.setProducer("Apache PDFBox 3.x");
    }

    // ── Cover page ───────────────────────────────────────────────────────────

    private void writeCoverPage(PDDocument doc, String tenantId, Instant from, Instant to,
                                String requestedBy, String purpose, int totalRows) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        PDType1Font fontBold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = PAGE_HEIGHT - MARGIN - 60f;

            // Title banner
            drawFilledRect(cs, MARGIN, y - 4, CONTENT_W, 40f, HEADER_BG);
            drawText(cs, fontBold, 18f, 1f, 1f, 1f, MARGIN + 10, y + 14, "Audit Evidence Package");
            y -= 70;

            // Metadata block
            String[][] fields = {
                { "Tenant ID:",      tenantId },
                { "Date Range:",     TS_FMT.format(from) + " UTC  →  " + TS_FMT.format(to) + " UTC" },
                { "Requested By:",   requestedBy },
                { "Generated At:",   TS_FMT.format(Instant.now()) + " UTC" },
                { "Total Entries:",  String.valueOf(totalRows) },
            };
            for (String[] row : fields) {
                drawText(cs, fontBold, 10f, 0.2f, 0.2f, 0.2f, MARGIN, y, row[0]);
                drawText(cs, fontNormal, 10f, 0f, 0f, 0f, MARGIN + 110, y, row[1]);
                y -= 18;
            }

            y -= 10;
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y, 0.5f, new float[]{0.6f, 0.6f, 0.6f});
            y -= 20;

            // Purpose
            drawText(cs, fontBold, 10f, 0.2f, 0.2f, 0.2f, MARGIN, y, "Purpose Statement:");
            y -= 16;
            drawText(cs, fontNormal, 9f, 0f, 0f, 0f, MARGIN + 10, y, truncate(purpose, 120));
            y -= 30;

            // Legal / attestation note
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y, 0.5f, new float[]{0.6f, 0.6f, 0.6f});
            y -= 18;
            drawText(cs, fontNormal, 8f, 0.4f, 0.4f, 0.4f, MARGIN, y,
                "This document is a system-generated, cryptographically protected audit extract.");
            y -= 13;
            drawText(cs, fontNormal, 8f, 0.4f, 0.4f, 0.4f, MARGIN, y,
                "Each entry is part of an SHA-256 hash chain. Any tampering will break chain integrity.");
            y -= 13;
            drawText(cs, fontNormal, 8f, 0.4f, 0.4f, 0.4f, MARGIN, y,
                "Chain verification must be performed using the Ghatana audit verification endpoint.");

            // Digital signature placeholder block
            y -= 40;
            drawLine(cs, MARGIN, y, MARGIN + 180, y, 0.5f, new float[]{0.3f, 0.3f, 0.3f});
            y -= 13;
            drawText(cs, fontNormal, 8f, 0.3f, 0.3f, 0.3f, MARGIN, y, "Authorized Signature / Stamp");
        }
    }

    // ── Data pages ───────────────────────────────────────────────────────────

    private void writeDataPages(PDDocument doc, List<AuditRow> rows) throws IOException {
        int totalPages     = (int) Math.ceil((double) rows.size() / ROWS_PER_PAGE);
        int dataPageOffset = doc.getNumberOfPages();   // pages before data section

        for (int p = 0; p < totalPages; p++) {
            List<AuditRow> pageRows = rows.subList(
                p * ROWS_PER_PAGE, Math.min((p + 1) * ROWS_PER_PAGE, rows.size()));
            writeDataPage(doc, pageRows, p + 1, totalPages, dataPageOffset);
        }

        // If no rows, write an empty placeholder page
        if (totalPages == 0) writeEmptyDataPage(doc);
    }

    private void writeDataPage(PDDocument doc, List<AuditRow> rows,
                                int pageNum, int totalPages, int pageOffset) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        PDType1Font fontBold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = PAGE_HEIGHT - MARGIN;

            // Page header
            drawText(cs, fontBold, 9f, 0.3f, 0.3f, 0.3f, MARGIN, y,
                "Audit Evidence — Page " + (pageOffset + pageNum) + " of " + (pageOffset + totalPages));
            y -= 18;
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y, 0.5f, new float[]{0.4f, 0.4f, 0.4f});
            y -= 4;

            // Column header row
            drawFilledRect(cs, MARGIN, y - HEADER_H + 4, CONTENT_W, HEADER_H, HEADER_BG);
            float cx = MARGIN + 3;
            for (int i = 0; i < COL_HEADERS.length; i++) {
                drawText(cs, fontBold, 7.5f, 1f, 1f, 1f, cx, y - 10, COL_HEADERS[i]);
                cx += COL_WIDTHS[i];
            }
            y -= HEADER_H;

            // Data rows
            for (int r = 0; r < rows.size(); r++) {
                AuditRow row = rows.get(r);
                boolean alt = (r % 2 == 1);
                if (alt) {
                    drawFilledRect(cs, MARGIN, y - ROW_HEIGHT + 4, CONTENT_W, ROW_HEIGHT, ALT_ROW_BG);
                }
                drawLine(cs, MARGIN, y - ROW_HEIGHT + 4, PAGE_WIDTH - MARGIN, y - ROW_HEIGHT + 4,
                    0.3f, new float[]{0.85f, 0.85f, 0.85f});

                String[] cells = {
                    String.valueOf(row.seqNum()),
                    row.gregorian(),
                    row.bsDate(),
                    truncate(row.actorId(), 14),
                    truncate(row.action(), 16),
                    truncate(row.resourceLabel(), 14),
                    row.outcome()
                };
                cx = MARGIN + 3;
                for (int i = 0; i < cells.length; i++) {
                    float r2 = 0f; float g = 0f; float b = 0f;
                    if ("FAILURE".equals(cells[i])) { r2 = 0.7f; }
                    if ("PARTIAL".equals(cells[i])) { r2 = 0.5f; g = 0.35f; }
                    drawText(cs, fontNormal, 7f, r2, g, b, cx, y - 11, cells[i]);
                    cx += COL_WIDTHS[i];
                }
                y -= ROW_HEIGHT;
            }

            // Page footer
            y = MARGIN - 12;
            drawLine(cs, MARGIN, y + 8, PAGE_WIDTH - MARGIN, y + 8, 0.3f,
                new float[]{0.7f, 0.7f, 0.7f});
            drawText(cs, fontNormal, 7f, 0.5f, 0.5f, 0.5f, MARGIN, y,
                "Generated by Ghatana Audit Evidence System  |  Confidential");
        }
    }

    private void writeEmptyDataPage(PDDocument doc) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            drawText(cs, font, 11f, 0.4f, 0.4f, 0.4f, MARGIN, PAGE_HEIGHT / 2f,
                "No audit entries found for the specified date range and tenant.");
        }
    }

    // ── Hash chain summary page ───────────────────────────────────────────────

    private void writeHashSummaryPage(PDDocument doc, List<AuditRow> rows) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        PDType1Font fontBold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fontMono   = new PDType1Font(Standard14Fonts.FontName.COURIER);
        PDType1Font fontNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        String genesisRef = HashChainService.GENESIS_HASH;
        String firstHash  = rows.isEmpty() ? "(no entries)" : rows.get(0).currentHash();
        String lastHash   = rows.isEmpty() ? "(no entries)" : rows.get(rows.size() - 1).currentHash();
        long   firstSeq   = rows.isEmpty() ? 0 : rows.get(0).seqNum();
        long   lastSeq    = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).seqNum();

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = PAGE_HEIGHT - MARGIN;

            drawFilledRect(cs, MARGIN, y - 4, CONTENT_W, 30f, HEADER_BG);
            drawText(cs, fontBold, 13f, 1f, 1f, 1f, MARGIN + 10, y + 10,
                "Hash Chain Integrity Summary");
            y -= 45;

            // Summary table
            String[][] summary = {
                { "Total Entries Exported:",         String.valueOf(rows.size()) },
                { "First Sequence Number:",          rows.isEmpty() ? "N/A" : String.valueOf(firstSeq) },
                { "Last Sequence Number:",           rows.isEmpty() ? "N/A" : String.valueOf(lastSeq) },
                { "Genesis Reference Hash (64 hex):", "" },
                { "", genesisRef },
                { "First Entry Current Hash (64 hex):", "" },
                { "", firstHash },
                { "Last Entry Current Hash (64 hex):",  "" },
                { "", lastHash },
            };
            for (String[] row : summary) {
                if (row[0].isEmpty()) {
                    drawText(cs, fontMono, 7f, 0.15f, 0.15f, 0.15f, MARGIN + 12, y, row[1]);
                    y -= 13;
                } else if (row[1].isEmpty()) {
                    drawText(cs, fontBold, 9f, 0.15f, 0.15f, 0.15f, MARGIN, y, row[0]);
                    y -= 14;
                } else {
                    drawText(cs, fontBold, 9f, 0.15f, 0.15f, 0.15f, MARGIN, y, row[0]);
                    drawText(cs, fontNormal, 9f, 0f, 0f, 0f, MARGIN + 180, y, row[1]);
                    y -= 16;
                }
            }

            y -= 20;
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y, 0.5f, new float[]{0.6f, 0.6f, 0.6f});
            y -= 20;

            // Attestation note
            drawText(cs, fontBold, 10f, 0.2f, 0.2f, 0.2f, MARGIN, y, "Integrity Attestation");
            y -= 16;
            String[] notes = {
                "Chain integrity verification requires replaying all entries between the first and last",
                "sequence numbers using the same SHA-256 algorithm. Use the Ghatana audit verification",
                "API endpoint to perform full chain verification and obtain a signed attestation report.",
                "",
                "Algorithm:  current_hash = SHA-256( previous_hash || sorted_canonical_json(entry) )",
                "Genesis:    previous_hash for sequence #1 = 64 zero characters (0000...0000)",
            };
            for (String note : notes) {
                drawText(cs, fontNormal, 8.5f, 0.3f, 0.3f, 0.3f, MARGIN, y, note);
                y -= 13;
            }

            // Digital signature line for the summary page
            y -= 25;
            drawLine(cs, MARGIN, y, MARGIN + 200, y, 0.5f, new float[]{0.3f, 0.3f, 0.3f});
            y -= 13;
            drawText(cs, fontNormal, 8f, 0.3f, 0.3f, 0.3f, MARGIN, y,
                "Data Controller / Compliance Officer Signature");
            drawLine(cs, MARGIN + 250, y + 13, MARGIN + 450, y + 13, 0.5f, new float[]{0.3f, 0.3f, 0.3f});
            drawText(cs, fontNormal, 8f, 0.3f, 0.3f, 0.3f, MARGIN + 250, y, "Date");
        }
    }

    // ── Low-level drawing primitives ─────────────────────────────────────────

    private static void drawText(PDPageContentStream cs, PDType1Font font, float size,
                                  float r, float g, float b, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(r, g, b);
        cs.newLineAtOffset(x, y);
        cs.showText(text == null ? "" : text);
        cs.endText();
    }

    private static void drawFilledRect(PDPageContentStream cs, float x, float y,
                                        float width, float height, float[] rgb) throws IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.addRect(x, y, width, height);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f);  // reset to black
    }

    private static void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2,
                                  float lineWidth, float[] rgb) throws IOException {
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.setLineWidth(lineWidth);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);  // reset
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}
