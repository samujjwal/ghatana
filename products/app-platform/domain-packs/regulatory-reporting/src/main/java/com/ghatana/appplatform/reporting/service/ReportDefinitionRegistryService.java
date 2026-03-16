package com.ghatana.appplatform.reporting.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose CRUD registry for regulatory report definitions. Each definition captures
 *              report code, regulator (SEBON/NRB/IRD), frequency, submission deadline
 *              (K-15 BS fiscal-year calendar), template reference, and active status.
 *              Maker-checker enforces publish via WorkflowPort (K-01). Publishes
 *              ReportDefinitionActivated event. Satisfies STORY-D10-001.
 * @doc.layer   Domain
 * @doc.pattern Registry CRUD; K-01 maker-checker; K-15 BS calendar; Counter + Gauge.
 */
public class ReportDefinitionRegistryService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final WorkflowPort     workflowPort;
    private final Counter          definitionActivatedCounter;
    private final AtomicLong       activeDefinitionCount = new AtomicLong();

    public ReportDefinitionRegistryService(HikariDataSource dataSource, Executor executor,
                                            CalendarPort calendarPort, WorkflowPort workflowPort,
                                            MeterRegistry registry) {
        this.dataSource                = dataSource;
        this.executor                  = executor;
        this.calendarPort              = calendarPort;
        this.workflowPort              = workflowPort;
        this.definitionActivatedCounter = Counter.builder("reporting.definition.activated_total").register(registry);
        Gauge.builder("reporting.definition.active_count", activeDefinitionCount, AtomicLong::doubleValue)
             .register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-15 BS/AD calendar port. */
    public interface CalendarPort {
        String toNepaliDate(LocalDate adDate);
        LocalDate nextDeadlineDate(String frequency, LocalDate referenceDate);
    }

    /** K-01 maker-checker workflow. */
    public interface WorkflowPort {
        String createApprovalTask(String entityId, String entityType, String requestedBy);
        void approveTask(String taskId, String approverId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum ReportFrequency { DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUAL, AD_HOC }
    public enum ReportStatus    { DRAFT, PENDING_APPROVAL, ACTIVE, DEPRECATED }

    public record ReportDefinition(String definitionId, String reportCode, String reportName,
                                    String regulator, ReportFrequency frequency,
                                    LocalDate nextDeadline, String nextDeadlineBs,
                                    String templateId, ReportStatus status,
                                    String createdBy, LocalDateTime createdAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ReportDefinition> createDefinition(String reportCode, String reportName,
                                                       String regulator, ReportFrequency frequency,
                                                       String templateId, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            LocalDate nextDeadline   = calendarPort.nextDeadlineDate(frequency.name(), LocalDate.now());
            String    nextDeadlineBs = calendarPort.toNepaliDate(nextDeadline);
            String    definitionId   = UUID.randomUUID().toString();
            ReportDefinition def = persistDefinition(definitionId, reportCode, reportName, regulator,
                    frequency, nextDeadline, nextDeadlineBs, templateId, requestedBy);
            workflowPort.createApprovalTask(definitionId, "REPORT_DEFINITION", requestedBy);
            return def;
        });
    }

    public Promise<ReportDefinition> activateDefinition(String definitionId, String taskId,
                                                         String approverId) {
        return Promise.ofBlocking(executor, () -> {
            workflowPort.approveTask(taskId, approverId);
            updateStatus(definitionId, ReportStatus.ACTIVE);
            activeDefinitionCount.incrementAndGet();
            definitionActivatedCounter.increment();
            return loadDefinition(definitionId);
        });
    }

    public Promise<List<ReportDefinition>> listActive() {
        return Promise.ofBlocking(executor, this::loadActiveDefinitions);
    }

    public Promise<ReportDefinition> deprecateDefinition(String definitionId) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(definitionId, ReportStatus.DEPRECATED);
            activeDefinitionCount.decrementAndGet();
            return loadDefinition(definitionId);
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private ReportDefinition persistDefinition(String definitionId, String reportCode,
                                                String reportName, String regulator,
                                                ReportFrequency frequency, LocalDate nextDeadline,
                                                String nextDeadlineBs, String templateId,
                                                String createdBy) throws SQLException {
        String sql = """
                INSERT INTO report_definitions
                    (definition_id, report_code, report_name, regulator, frequency,
                     next_deadline, next_deadline_bs, template_id, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL', ?, NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, definitionId);
            ps.setString(2, reportCode);
            ps.setString(3, reportName);
            ps.setString(4, regulator);
            ps.setString(5, frequency.name());
            ps.setObject(6, nextDeadline);
            ps.setString(7, nextDeadlineBs);
            ps.setString(8, templateId);
            ps.setString(9, createdBy);
            ps.executeUpdate();
        }
        return loadDefinition(definitionId);
    }

    private void updateStatus(String definitionId, ReportStatus status) throws SQLException {
        String sql = "UPDATE report_definitions SET status=? WHERE definition_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, definitionId);
            ps.executeUpdate();
        }
    }

    private ReportDefinition loadDefinition(String definitionId) throws SQLException {
        String sql = """
                SELECT definition_id, report_code, report_name, regulator, frequency,
                       next_deadline, next_deadline_bs, template_id, status, created_by, created_at
                FROM report_definitions WHERE definition_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, definitionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Definition not found: " + definitionId);
                return mapDefinition(rs);
            }
        }
    }

    private List<ReportDefinition> loadActiveDefinitions() throws SQLException {
        String sql = """
                SELECT definition_id, report_code, report_name, regulator, frequency,
                       next_deadline, next_deadline_bs, template_id, status, created_by, created_at
                FROM report_definitions WHERE status='ACTIVE'
                ORDER BY next_deadline
                """;
        List<ReportDefinition> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapDefinition(rs));
        }
        return result;
    }

    private ReportDefinition mapDefinition(ResultSet rs) throws SQLException {
        return new ReportDefinition(rs.getString("definition_id"), rs.getString("report_code"),
                rs.getString("report_name"), rs.getString("regulator"),
                ReportFrequency.valueOf(rs.getString("frequency")),
                rs.getObject("next_deadline", LocalDate.class), rs.getString("next_deadline_bs"),
                rs.getString("template_id"), ReportStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"), rs.getObject("created_at", LocalDateTime.class));
    }
}
