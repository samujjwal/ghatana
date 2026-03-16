package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Renders reports to XBRL and inline XBRL (iXBRL) format. Maps report fields
 *              to XBRL taxonomy concepts via K-04 T3 taxonomy plugin (XbrlTaxonomyPort).
 *              Manages namespace declarations, context elements, and unit definitions.
 *              iXBRL wraps XBRL tags within human-readable HTML. Satisfies STORY-D10-005.
 * @doc.layer   Domain
 * @doc.pattern XBRL rendering; K-04 T3 taxonomy plugin; iXBRL; namespace management; Counter.
 */
public class XbrlRendererService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final XbrlTaxonomyPort taxonomyPort;
    private final StoragePort      storagePort;
    private final Counter          xbrlRenderedCounter;
    private final Counter          ixbrlRenderedCounter;

    public XbrlRendererService(HikariDataSource dataSource, Executor executor,
                                XbrlTaxonomyPort taxonomyPort, StoragePort storagePort,
                                MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.taxonomyPort         = taxonomyPort;
        this.storagePort          = storagePort;
        this.xbrlRenderedCounter  = Counter.builder("reporting.xbrl.rendered_total").register(registry);
        this.ixbrlRenderedCounter = Counter.builder("reporting.ixbrl.rendered_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-04 T3 plugin port for Nepal SEBON XBRL taxonomy. */
    public interface XbrlTaxonomyPort {
        List<TaxonomyMapping> getMappings(String reportCode);
        String getTaxonomyNamespace(String reportCode);
        String getTaxonomyLocation(String reportCode);
    }

    public interface StoragePort {
        String store(String bucket, String key, byte[] data, String contentType);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TaxonomyMapping(String fieldName, String conceptName, String dataType,
                                   String unit, String periodType) {}

    public record XbrlArtifact(String artifactId, String reportId, String storageKey,
                                String format, int conceptCount, LocalDateTime renderedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<XbrlArtifact> renderXbrl(String reportId, String reportCode,
                                             String contextDate, Map<String, String> facts) {
        return Promise.ofBlocking(executor, () -> {
            List<TaxonomyMapping> mappings = taxonomyPort.getMappings(reportCode);
            String namespace = taxonomyPort.getTaxonomyNamespace(reportCode);
            String location  = taxonomyPort.getTaxonomyLocation(reportCode);
            String xbrl = buildXbrl(reportId, reportCode, namespace, location,
                    contextDate, facts, mappings);
            byte[] data = xbrl.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String key  = "reports/" + reportId + "/xbrl/" + UUID.randomUUID() + ".xml";
            storagePort.store("reporting", key, data, "application/xml");
            xbrlRenderedCounter.increment();
            return persistArtifact(reportId, key, "XBRL", mappings.size());
        });
    }

    public Promise<XbrlArtifact> renderIxbrl(String reportId, String reportCode,
                                               String contextDate, Map<String, String> facts,
                                               String htmlBody) {
        return Promise.ofBlocking(executor, () -> {
            List<TaxonomyMapping> mappings = taxonomyPort.getMappings(reportCode);
            String namespace = taxonomyPort.getTaxonomyNamespace(reportCode);
            String ixbrl = buildIxbrl(reportId, reportCode, namespace, contextDate,
                    facts, mappings, htmlBody);
            byte[] data = ixbrl.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String key  = "reports/" + reportId + "/ixbrl/" + UUID.randomUUID() + ".html";
            storagePort.store("reporting", key, data, "text/html");
            ixbrlRenderedCounter.increment();
            return persistArtifact(reportId, key, "iXBRL", mappings.size());
        });
    }

    // ─── XBRL builders ───────────────────────────────────────────────────────

    private String buildXbrl(String reportId, String reportCode, String namespace,
                               String location, String contextDate, Map<String, String> facts,
                               List<TaxonomyMapping> mappings) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<xbrl xmlns=\"http://www.xbrl.org/2003/instance\"\n");
        sb.append("      xmlns:").append(reportCode).append("=\"").append(namespace).append("\"\n");
        sb.append("      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("      xsi:schemaLocation=\"").append(namespace).append(" ").append(location).append("\">\n");
        sb.append("  <context id=\"ctx1\"><entity><identifier scheme=\"http://sebon.gov.np\">")
          .append(reportId).append("</identifier></entity>");
        sb.append("<period><instant>").append(contextDate).append("</instant></period></context>\n");
        sb.append("  <unit id=\"NPR\"><measure>iso4217:NPR</measure></unit>\n");
        for (TaxonomyMapping m : mappings) {
            String value = facts.get(m.fieldName());
            if (value != null) {
                sb.append("  <").append(reportCode).append(":").append(m.conceptName())
                  .append(" contextRef=\"ctx1\"");
                if ("monetary".equals(m.dataType())) sb.append(" unitRef=\"NPR\" decimals=\"2\"");
                sb.append(">").append(escapeXml(value)).append("</")
                  .append(reportCode).append(":").append(m.conceptName()).append(">\n");
            }
        }
        sb.append("</xbrl>");
        return sb.toString();
    }

    private String buildIxbrl(String reportId, String reportCode, String namespace,
                                String contextDate, Map<String, String> facts,
                                List<TaxonomyMapping> mappings, String htmlBody) {
        // Inject ix:nonNumeric / ix:nonFraction tags into the HTML body
        String enriched = htmlBody;
        for (TaxonomyMapping m : mappings) {
            String value = facts.get(m.fieldName());
            if (value == null) continue;
            String tag = "monetary".equals(m.dataType())
                    ? "<ix:nonFraction name=\"" + reportCode + ":" + m.conceptName() + "\" contextRef=\"ctx1\" unitRef=\"NPR\" decimals=\"2\" format=\"ixt:numdotdecimal\">" + escapeXml(value) + "</ix:nonFraction>"
                    : "<ix:nonNumeric name=\"" + reportCode + ":" + m.conceptName() + "\" contextRef=\"ctx1\">" + escapeXml(value) + "</ix:nonNumeric>";
            enriched = enriched.replace("{{" + m.fieldName() + "}}", tag);
        }
        return "<!DOCTYPE html>\n<html xmlns:ix=\"http://www.xbrl.org/2013/inlineXBRL\" xmlns:" +
                reportCode + "=\"" + namespace + "\">\n" +
                "<head><title>iXBRL Report</title></head>\n<body>\n" +
                enriched + "\n</body>\n</html>";
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private XbrlArtifact persistArtifact(String reportId, String key, String format,
                                          int conceptCount) throws SQLException {
        String artifactId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO report_xbrl_artifacts
                    (artifact_id, report_id, storage_key, format, concept_count, rendered_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (artifact_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifactId);
            ps.setString(2, reportId);
            ps.setString(3, key);
            ps.setString(4, format);
            ps.setInt(5, conceptCount);
            ps.executeUpdate();
        }
        return new XbrlArtifact(artifactId, reportId, key, format, conceptCount, LocalDateTime.now());
    }
}
