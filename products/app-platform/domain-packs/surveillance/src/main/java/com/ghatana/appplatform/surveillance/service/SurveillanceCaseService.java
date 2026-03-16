package com.ghatana.appplatform.surveillance.service;

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
 * @doc.purpose Manages the full surveillance case lifecycle: OPENED → INVESTIGATING →
 *              EVIDENCE_GATHERED → DECISION → CLOSED (SUBSTANTIATED | UNSUBSTANTIATED).
 *              Maintains SLA compliance via K-02 configurable deadline days with escalation
 *              on breach. K-01 WorkflowPort for analyst task assignment. K-07 AuditPort
 *              for immutable case history. Satisfies STORY-D08-012.
 * @doc.layer   Domain
 * @doc.pattern Case lifecycle; K-02 SLA; K-01 WorkflowPort; K-07 AuditPort; Gauge open cases.
 */
public class SurveillanceCaseService {

    private static final int DEFAULT_SLA_DAYS = 30;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final WorkflowPort     workflowPort;
    private final AuditPort        auditPort;
    private final ConfigPort       configPort;
    private final Counter          caseOpenedCounter;
    private final Counter          slaBreachedCounter;
    private final AtomicLong       openCaseCount = new AtomicLong();

    public SurveillanceCaseService(HikariDataSource dataSource, Executor executor,
                                    WorkflowPort workflowPort, AuditPort auditPort,
                                    ConfigPort configPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.workflowPort   = workflowPort;
        this.auditPort      = auditPort;
        this.configPort     = configPort;
        this.caseOpenedCounter = Counter.builder("surveillance.case.opened_total").register(registry);
        this.slaBreachedCounter = Counter.builder("surveillance.case.sla_breached_total").register(registry);
        Gauge.builder("surveillance.case.open_count", openCaseCount, AtomicLong::doubleValue)
             .register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-01 WorkflowPort for analyst task assignment. */
    public interface WorkflowPort {
        String createCaseTask(String caseId, String subjectClient, CasePriority priority,
                              int slaDays, LocalDateTime deadline);
        void escalateCaseTask(String taskId, String reason);
    }

    /** K-07 immutable audit trail. */
    public interface AuditPort {
        void logCaseTransition(String caseId, String fromState, String toState, String analyst);
    }

    /** K-02 configurable thresholds. */
    public interface ConfigPort {
        int getCaseSlaDays(CasePriority priority);
    }

    // ─── Domain types ─────────────────────────────────────────────────────────

    public enum CasePriority { LOW, MEDIUM, HIGH, CRITICAL }
    public enum CaseStatus   { OPENED, INVESTIGATING, EVIDENCE_GATHERED, DECISION, CLOSED }
    public enum CaseOutcome  { SUBSTANTIATED, UNSUBSTANTIATED, PENDING }

    public record SurveillanceCase(String caseId, List<String> alertIds, String assignedAnalyst,
                                   String subjectClient, List<String> instruments,
                                   CasePriority priority, CaseStatus status, CaseOutcome outcome,
                                   LocalDateTime openedAt, LocalDateTime deadline,
                                   String workflowTaskId) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<SurveillanceCase> openCase(List<String> alertIds, String subjectClient,
                                               List<String> instruments, CasePriority priority,
                                               String assignedAnalyst) {
        return Promise.ofBlocking(executor, () -> {
            int slaDays  = configPort.getCaseSlaDays(priority);
            LocalDateTime deadline = LocalDateTime.now().plusDays(slaDays);
            String caseId = UUID.randomUUID().toString();

            String taskId = workflowPort.createCaseTask(caseId, subjectClient, priority, slaDays, deadline);
            SurveillanceCase sc = persistCase(caseId, alertIds, assignedAnalyst, subjectClient,
                    instruments, priority, CaseStatus.OPENED, deadline, taskId);
            auditPort.logCaseTransition(caseId, "NONE", "OPENED", assignedAnalyst);
            caseOpenedCounter.increment();
            openCaseCount.incrementAndGet();
            return sc;
        });
    }

    public Promise<SurveillanceCase> transitionCase(String caseId, CaseStatus toStatus,
                                                     String analystId, String notes) {
        return Promise.ofBlocking(executor, () -> {
            SurveillanceCase sc = loadCase(caseId);
            updateCaseStatus(caseId, toStatus, notes);
            auditPort.logCaseTransition(caseId, sc.status().name(), toStatus.name(), analystId);
            if (toStatus == CaseStatus.CLOSED) openCaseCount.decrementAndGet();
            return loadCase(caseId);
        });
    }

    public Promise<SurveillanceCase> closeCase(String caseId, CaseOutcome outcome,
                                                String analystId, String closingStatement) {
        return Promise.ofBlocking(executor, () -> {
            updateCaseOutcome(caseId, outcome, closingStatement);
            auditPort.logCaseTransition(caseId, "DECISION", "CLOSED", analystId);
            openCaseCount.decrementAndGet();
            return loadCase(caseId);
        });
    }

    /** K-02 SLA breach check — call from scheduler. */
    public Promise<List<String>> escalateSlaBreaches() {
        return Promise.ofBlocking(executor, () -> {
            List<SurveillanceCase> breached = loadBreachedCases();
            List<String> escalatedIds = new ArrayList<>();
            for (SurveillanceCase sc : breached) {
                workflowPort.escalateCaseTask(sc.workflowTaskId(),
                        "SLA breached: deadline was " + sc.deadline());
                markEscalated(sc.caseId());
                slaBreachedCounter.increment();
                escalatedIds.add(sc.caseId());
            }
            return escalatedIds;
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private SurveillanceCase persistCase(String caseId, List<String> alertIds, String analyst,
                                          String subjectClient, List<String> instruments,
                                          CasePriority priority, CaseStatus status,
                                          LocalDateTime deadline, String taskId) throws SQLException {
        String sql = """
                INSERT INTO surveillance_cases
                    (case_id, alert_ids, assigned_analyst, subject_client, instruments,
                     priority, status, outcome, opened_at, deadline, workflow_task_id)
                VALUES (?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, 'PENDING', NOW(), ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ps.setString(2, toJsonArray(alertIds));
            ps.setString(3, analyst);
            ps.setString(4, subjectClient);
            ps.setString(5, toJsonArray(instruments));
            ps.setString(6, priority.name());
            ps.setString(7, status.name());
            ps.setObject(8, deadline);
            ps.setString(9, taskId);
            ps.executeUpdate();
        }
        return loadCase(caseId);
    }

    private SurveillanceCase loadCase(String caseId) throws SQLException {
        String sql = """
                SELECT case_id, alert_ids, assigned_analyst, subject_client, instruments,
                       priority, status, outcome, opened_at, deadline, workflow_task_id
                FROM surveillance_cases WHERE case_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Case not found: " + caseId);
                return mapCase(rs);
            }
        }
    }

    private List<SurveillanceCase> loadBreachedCases() throws SQLException {
        String sql = """
                SELECT case_id, alert_ids, assigned_analyst, subject_client, instruments,
                       priority, status, outcome, opened_at, deadline, workflow_task_id
                FROM surveillance_cases
                WHERE status NOT IN ('CLOSED') AND deadline < NOW() AND escalated = FALSE
                """;
        List<SurveillanceCase> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapCase(rs));
        }
        return result;
    }

    private void updateCaseStatus(String caseId, CaseStatus status, String notes) throws SQLException {
        String sql = "UPDATE surveillance_cases SET status=?, notes=?, updated_at=NOW() WHERE case_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, notes);
            ps.setString(3, caseId);
            ps.executeUpdate();
        }
    }

    private void updateCaseOutcome(String caseId, CaseOutcome outcome, String statement) throws SQLException {
        String sql = "UPDATE surveillance_cases SET status='CLOSED', outcome=?, closing_statement=?, updated_at=NOW() WHERE case_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outcome.name());
            ps.setString(2, statement);
            ps.setString(3, caseId);
            ps.executeUpdate();
        }
    }

    private void markEscalated(String caseId) throws SQLException {
        String sql = "UPDATE surveillance_cases SET escalated=TRUE WHERE case_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ps.executeUpdate();
        }
    }

    private SurveillanceCase mapCase(ResultSet rs) throws SQLException {
        return new SurveillanceCase(
                rs.getString("case_id"),
                List.of(),                   // alert_ids parsed from jsonb — simplified
                rs.getString("assigned_analyst"),
                rs.getString("subject_client"),
                List.of(),                   // instruments parsed from jsonb — simplified
                CasePriority.valueOf(rs.getString("priority")),
                CaseStatus.valueOf(rs.getString("status")),
                CaseOutcome.valueOf(rs.getString("outcome")),
                rs.getObject("opened_at", LocalDateTime.class),
                rs.getObject("deadline", LocalDateTime.class),
                rs.getString("workflow_task_id"));
    }

    private String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append('"').append(items.get(i)).append('"');
            if (i < items.size() - 1) sb.append(',');
        }
        return sb.append(']').toString();
    }
}
