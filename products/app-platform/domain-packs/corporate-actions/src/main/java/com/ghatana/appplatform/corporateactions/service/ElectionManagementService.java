package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
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

/**
 * @doc.type    DomainService
 * @doc.purpose Manages optional elections for voluntary CAs (RIGHTS, OPTIONAL DIVIDENDS).
 *              Portal-based K-01 WorkflowPort for institutional elections requiring maker-
 *              checker. Auto-lapses unelected positions after election deadline. Issues
 *              confirmation receipt once election is accepted. Satisfies STORY-D12-009.
 * @doc.layer   Domain
 * @doc.pattern Election management; K-01 maker-checker; deadline auto-lapse; confirmation receipt.
 */
public class ElectionManagementService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final WorkflowPort     workflowPort;
    private final NotificationPort notificationPort;
    private final Counter          electionSubmittedCounter;
    private final Counter          electionApprovedCounter;
    private final Counter          electionLapsedCounter;

    public ElectionManagementService(HikariDataSource dataSource, Executor executor,
                                      WorkflowPort workflowPort, NotificationPort notificationPort,
                                      MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.workflowPort             = workflowPort;
        this.notificationPort         = notificationPort;
        this.electionSubmittedCounter = Counter.builder("ca.election.submitted_total").register(registry);
        this.electionApprovedCounter  = Counter.builder("ca.election.approved_total").register(registry);
        this.electionLapsedCounter    = Counter.builder("ca.election.lapsed_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-01 for institutional election maker-checker. */
    public interface WorkflowPort {
        String createApprovalTask(String entityId, String entityType, String requestedBy);
        void approveTask(String taskId, String approverId);
    }

    public interface NotificationPort {
        void sendConfirmation(String clientId, String subject, String body);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum ElectionStatus { PENDING_APPROVAL, CONFIRMED, LAPSED }

    public record ElectionRecord(String electionId, String caId, String clientId,
                                  String electionChoice, ElectionStatus status,
                                  String workflowTaskId, LocalDate deadline,
                                  LocalDateTime submittedAt, LocalDateTime confirmedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ElectionRecord> submitElection(String caId, String clientId, String choice,
                                                   LocalDate deadline, String requestedBy,
                                                   boolean institutional) {
        return Promise.ofBlocking(executor, () -> {
            if (LocalDate.now().isAfter(deadline)) {
                throw new IllegalStateException("Election deadline has passed for CA: " + caId);
            }
            String electionId = UUID.randomUUID().toString();
            String taskId     = null;
            if (institutional) {
                taskId = workflowPort.createApprovalTask(electionId, "CA_ELECTION", requestedBy);
            }
            ElectionRecord rec = persistElection(electionId, caId, clientId, choice,
                    institutional ? ElectionStatus.PENDING_APPROVAL : ElectionStatus.CONFIRMED,
                    taskId, deadline);
            if (!institutional) {
                sendConfirmation(clientId, caId, choice);
            }
            electionSubmittedCounter.increment();
            return rec;
        });
    }

    public Promise<ElectionRecord> approveElection(String electionId, String taskId,
                                                    String approverId) {
        return Promise.ofBlocking(executor, () -> {
            workflowPort.approveTask(taskId, approverId);
            updateStatus(electionId, ElectionStatus.CONFIRMED);
            ElectionRecord rec = loadElection(electionId);
            sendConfirmation(rec.clientId(), rec.caId(), rec.electionChoice());
            electionApprovedCounter.increment();
            return rec;
        });
    }

    public Promise<List<ElectionRecord>> autoLapseExpired(LocalDate today) {
        return Promise.ofBlocking(executor, () -> {
            List<String> expired = loadExpired(today);
            List<ElectionRecord> lapsed = new ArrayList<>();
            for (String id : expired) {
                updateStatus(id, ElectionStatus.LAPSED);
                lapsed.add(loadElection(id));
                electionLapsedCounter.increment();
            }
            return lapsed;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void sendConfirmation(String clientId, String caId, String choice) {
        notificationPort.sendConfirmation(clientId,
                "Election Confirmed – CA " + caId,
                "Your election (" + choice + ") for corporate action " + caId + " has been confirmed.");
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private ElectionRecord persistElection(String electionId, String caId, String clientId,
                                            String choice, ElectionStatus status,
                                            String taskId, LocalDate deadline) throws SQLException {
        String sql = """
                INSERT INTO ca_elections
                    (election_id, ca_id, client_id, election_choice, status, workflow_task_id,
                     deadline, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (ca_id, client_id) DO UPDATE
                SET election_choice=EXCLUDED.election_choice, status=EXCLUDED.status, submitted_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, electionId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setString(4, choice); ps.setString(5, status.name());
            ps.setString(6, taskId); ps.setObject(7, deadline);
            ps.executeUpdate();
        }
        return loadElection(electionId);
    }

    private void updateStatus(String electionId, ElectionStatus status) throws SQLException {
        String sql = "UPDATE ca_elections SET status=?, confirmed_at=CASE WHEN ?='CONFIRMED' THEN NOW() ELSE confirmed_at END WHERE election_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name()); ps.setString(2, status.name()); ps.setString(3, electionId);
            ps.executeUpdate();
        }
    }

    private List<String> loadExpired(LocalDate today) throws SQLException {
        String sql = "SELECT election_id FROM ca_elections WHERE deadline < ? AND status='PENDING_APPROVAL'";
        List<String> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getString(1)); }
        }
        return result;
    }

    private ElectionRecord loadElection(String electionId) throws SQLException {
        String sql = "SELECT * FROM ca_elections WHERE election_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Not found: " + electionId);
                return new ElectionRecord(rs.getString("election_id"), rs.getString("ca_id"),
                        rs.getString("client_id"), rs.getString("election_choice"),
                        ElectionStatus.valueOf(rs.getString("status")),
                        rs.getString("workflow_task_id"),
                        rs.getObject("deadline", LocalDate.class),
                        rs.getObject("submitted_at", LocalDateTime.class),
                        rs.getObject("confirmed_at", LocalDateTime.class));
            }
        }
    }
}
