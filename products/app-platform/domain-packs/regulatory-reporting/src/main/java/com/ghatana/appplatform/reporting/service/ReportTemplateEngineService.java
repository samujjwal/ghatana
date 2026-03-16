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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type    DomainService
 * @doc.purpose Handlebars-style template engine for regulatory reports. Templates contain
 *              {{placeholder}} tokens filled from a data-binding map. Templates are versioned;
 *              each publish creates a new version row. Storage via StoragePort (S3/MinIO).
 *              Preview renders with sample data without persisting output.
 *              Satisfies STORY-D10-002.
 * @doc.layer   Domain
 * @doc.pattern Template engine; semver versioning; StoragePort; Counter for renders.
 */
public class ReportTemplateEngineService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final StoragePort      storagePort;
    private final Counter          templatePublishedCounter;
    private final Counter          previewRenderedCounter;

    public ReportTemplateEngineService(HikariDataSource dataSource, Executor executor,
                                        StoragePort storagePort, MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.storagePort              = storagePort;
        this.templatePublishedCounter = Counter.builder("reporting.template.published_total").register(registry);
        this.previewRenderedCounter   = Counter.builder("reporting.template.preview_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface StoragePort {
        String store(String bucket, String key, byte[] data, String contentType);
        byte[] retrieve(String bucket, String key);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TemplateVersion(String templateId, String reportCode, int versionMajor,
                                   int versionMinor, String storageKey, boolean active,
                                   LocalDateTime publishedAt) {}

    public record RenderedPreview(String templateId, int versionMajor, int versionMinor,
                                   String renderedContent, List<String> missingKeys) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<TemplateVersion> publishTemplate(String reportCode, String templateContent,
                                                     String publishedBy) {
        return Promise.ofBlocking(executor, () -> {
            int[] nextVersion = resolveNextVersion(reportCode);
            String templateId = UUID.randomUUID().toString();
            byte[] contentBytes = templateContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String key = "templates/" + reportCode + "/v" + nextVersion[0] + "_" + nextVersion[1] + ".hbs";
            storagePort.store("reporting", key, contentBytes, "text/plain");
            TemplateVersion version = persistVersion(templateId, reportCode, nextVersion[0], nextVersion[1], key);
            deactivatePrior(reportCode, templateId);
            templatePublishedCounter.increment();
            return version;
        });
    }

    public Promise<RenderedPreview> previewTemplate(String templateId, Map<String, String> sampleData) {
        return Promise.ofBlocking(executor, () -> {
            TemplateVersion ver = loadTemplateVersion(templateId);
            byte[] raw = storagePort.retrieve("reporting", ver.storageKey());
            String content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            RenderedPreview preview = render(templateId, ver.versionMajor(), ver.versionMinor(),
                    content, sampleData);
            previewRenderedCounter.increment();
            return preview;
        });
    }

    public Promise<String> renderForReport(String reportCode, Map<String, String> data) {
        return Promise.ofBlocking(executor, () -> {
            TemplateVersion ver = loadActiveTemplate(reportCode);
            byte[] raw = storagePort.retrieve("reporting", ver.storageKey());
            String content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            RenderedPreview preview = render(ver.templateId(), ver.versionMajor(),
                    ver.versionMinor(), content, data);
            if (!preview.missingKeys().isEmpty()) {
                throw new IllegalStateException("Missing template keys: " + preview.missingKeys());
            }
            return preview.renderedContent();
        });
    }

    // ─── Rendering ───────────────────────────────────────────────────────────

    private RenderedPreview render(String templateId, int major, int minor,
                                    String content, Map<String, String> data) {
        List<String> missing = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key   = m.group(1);
            String value = data.get(key);
            if (value == null) {
                missing.add(key);
                m.appendReplacement(sb, "{{" + key + "}}");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        m.appendTail(sb);
        return new RenderedPreview(templateId, major, minor, sb.toString(), missing);
    }

    // ─── Versioning ──────────────────────────────────────────────────────────

    private int[] resolveNextVersion(String reportCode) throws SQLException {
        String sql = """
                SELECT COALESCE(MAX(version_major), 0) AS major,
                       COALESCE(MAX(version_minor), -1) AS minor
                FROM report_templates WHERE report_code = ?
                  AND version_major = (SELECT COALESCE(MAX(version_major), 0) FROM report_templates WHERE report_code=?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportCode);
            ps.setString(2, reportCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new int[]{1, 0};
                int major = rs.getInt("major");
                int minor = rs.getInt("minor");
                return new int[]{major == 0 ? 1 : major, minor + 1};
            }
        }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private TemplateVersion persistVersion(String templateId, String reportCode,
                                            int major, int minor, String key) throws SQLException {
        String sql = """
                INSERT INTO report_templates
                    (template_id, report_code, version_major, version_minor, storage_key, active, published_at)
                VALUES (?, ?, ?, ?, ?, TRUE, NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, templateId);
            ps.setString(2, reportCode);
            ps.setInt(3, major);
            ps.setInt(4, minor);
            ps.setString(5, key);
            ps.executeUpdate();
        }
        return loadTemplateVersion(templateId);
    }

    private void deactivatePrior(String reportCode, String currentTemplateId) throws SQLException {
        String sql = "UPDATE report_templates SET active=FALSE WHERE report_code=? AND template_id != ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportCode);
            ps.setString(2, currentTemplateId);
            ps.executeUpdate();
        }
    }

    private TemplateVersion loadTemplateVersion(String templateId) throws SQLException {
        String sql = "SELECT * FROM report_templates WHERE template_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Template not found: " + templateId);
                return new TemplateVersion(rs.getString("template_id"), rs.getString("report_code"),
                        rs.getInt("version_major"), rs.getInt("version_minor"),
                        rs.getString("storage_key"), rs.getBoolean("active"),
                        rs.getObject("published_at", LocalDateTime.class));
            }
        }
    }

    private TemplateVersion loadActiveTemplate(String reportCode) throws SQLException {
        String sql = "SELECT * FROM report_templates WHERE report_code=? AND active=TRUE";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reportCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No active template for: " + reportCode);
                return new TemplateVersion(rs.getString("template_id"), rs.getString("report_code"),
                        rs.getInt("version_major"), rs.getInt("version_minor"),
                        rs.getString("storage_key"), rs.getBoolean("active"),
                        rs.getObject("published_at", LocalDateTime.class));
            }
        }
    }
}
