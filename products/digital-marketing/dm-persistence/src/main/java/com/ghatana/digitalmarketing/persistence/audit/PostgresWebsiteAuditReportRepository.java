package com.ghatana.digitalmarketing.persistence.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditFinding;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link WebsiteAuditReportRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics (INSERT … ON CONFLICT DO UPDATE) for idempotent saves.
 * Serializes WebsiteAuditFinding list as JSONB.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS website audit report persistence
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresWebsiteAuditReportRepository implements WebsiteAuditReportRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresWebsiteAuditReportRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_website_audit_reports " +
        "  (report_id, workspace_id, website_url, overall_score, findings, summary, recommendations, " +
        "   model_version, audited_at, audited_by) " +
        "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (report_id, workspace_id) DO UPDATE SET " +
        "  website_url = EXCLUDED.website_url, " +
        "  overall_score = EXCLUDED.overall_score, " +
        "  findings = EXCLUDED.findings, " +
        "  summary = EXCLUDED.summary, " +
        "  recommendations = EXCLUDED.recommendations";

    private static final String SELECT_BY_ID_SQL =
        "SELECT report_id, workspace_id, website_url, overall_score, findings, summary, recommendations, " +
        "       model_version, audited_at, audited_by " +
        "FROM dmos_website_audit_reports " +
        "WHERE report_id = ? AND workspace_id = ?";

    private static final String SELECT_LATEST_BY_WORKSPACE_SQL =
        "SELECT report_id, workspace_id, website_url, overall_score, findings, summary, recommendations, " +
        "       model_version, audited_at, audited_by " +
        "FROM dmos_website_audit_reports " +
        "WHERE workspace_id = ? " +
        "ORDER BY audited_at DESC " +
        "LIMIT 1";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public PostgresWebsiteAuditReportRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<WebsiteAuditReport> save(WebsiteAuditReport report) {
        Objects.requireNonNull(report, "report must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, report.getReportId());
                stmt.setString(2, report.getWorkspaceId().getValue());
                stmt.setString(3, report.getWebsiteUrl());
                stmt.setInt(4, calculateOverallScore(report.getFindings()));
                stmt.setString(5, objectMapper.writeValueAsString(report.getFindings()));
                stmt.setString(6, buildSummary(report.getFindings()));
                stmt.setString(7, buildRecommendations(report.getFindings()));
                stmt.setString(8, "deterministic-v1"); // model_version
                stmt.setTimestamp(9, Timestamp.from(report.getGeneratedAt()));
                stmt.setString(10, report.getGeneratedBy());
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] website audit report upserted: id={} workspace={}",
                    report.getReportId(), report.getWorkspaceId().getValue());
                return report;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save report id={}: {}", report.getReportId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save website audit report: " + report.getReportId(), e);
            } catch (JsonProcessingException e) {
                LOG.error("[DMOS-PERSIST] failed to serialize report id={}: {}", report.getReportId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to serialize website audit report: " + report.getReportId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<WebsiteAuditReport>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_LATEST_BY_WORKSPACE_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find latest report for workspace={}: {}",
                    workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to find latest website audit report for workspace: " + workspaceId.getValue(), e);
            }
        });
    }

    private static WebsiteAuditReport mapRow(ResultSet rs) throws SQLException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Instant auditedAt = rs.getTimestamp("audited_at").toInstant();
        
        List<WebsiteAuditFinding> findings = parseFindings(mapper, rs.getString("findings"));
        
        return WebsiteAuditReport.builder()
            .reportId(rs.getString("report_id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .websiteUrl(rs.getString("website_url"))
            .findings(findings)
            .generatedAt(auditedAt)
            .generatedBy(rs.getString("audited_by"))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<WebsiteAuditFinding> parseFindings(ObjectMapper mapper, String json) throws JsonProcessingException {
        List<?> rawList = mapper.readValue(json, List.class);
        List<WebsiteAuditFinding> findings = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof java.util.Map<?, ?>) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                findings.add(new WebsiteAuditFinding(
                    (String) map.get("category"),
                    (String) map.get("severity"),
                    (String) map.get("description"),
                    (String) map.get("recommendation")
                ));
            }
        }
        return List.copyOf(findings);
    }

    private static int calculateOverallScore(List<WebsiteAuditFinding> findings) {
        if (findings.isEmpty()) {
            return 100; // Perfect score if no findings
        }
        int criticalCount = 0;
        for (WebsiteAuditFinding finding : findings) {
            if ("critical".equalsIgnoreCase(finding.severity())) {
                criticalCount++;
            }
        }
        // Simple scoring: 100 - (critical * 20), minimum 0
        return Math.max(0, 100 - (criticalCount * 20));
    }

    private static String buildSummary(List<WebsiteAuditFinding> findings) {
        if (findings.isEmpty()) {
            return "No issues found. Website is healthy.";
        }
        long criticalCount = findings.stream().filter(f -> "critical".equalsIgnoreCase(f.severity())).count();
        long warningCount = findings.stream().filter(f -> "warning".equalsIgnoreCase(f.severity())).count();
        return String.format("Found %d critical and %d warning issues.", criticalCount, warningCount);
    }

    private static String buildRecommendations(List<WebsiteAuditFinding> findings) {
        if (findings.isEmpty()) {
            return "No recommendations needed.";
        }
        StringBuilder sb = new StringBuilder();
        for (WebsiteAuditFinding finding : findings) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(finding.recommendation());
        }
        return sb.toString();
    }
}
