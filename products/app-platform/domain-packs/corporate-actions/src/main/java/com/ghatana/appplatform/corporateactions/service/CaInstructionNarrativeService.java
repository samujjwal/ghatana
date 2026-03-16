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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Generates CA holder-facing instruction narratives (150-300 words) using a
 *              K-04 T2 local LLM sandbox (no external API). Narratives include BS+AD dates,
 *              CA type, entitlement method, and election deadline. Requires HITL approval via
 *              K-01 WorkflowPort before FINAL status. K-07 audit for drafts and approvals.
 *              Satisfies STORY-D12-014.
 * @doc.layer   Domain
 * @doc.pattern K-04 T2 local LLM; K-01 HITL approval workflow; K-07 audit; K-15 dual dates;
 *              DRAFT→APPROVED/REJECTED; ON CONFLICT (ca_id) DO UPDATE; Counter.
 */
public class CaInstructionNarrativeService {

    private static final int MIN_WORD_COUNT = 150;
    private static final int MAX_WORD_COUNT = 300;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LlmSandboxPort   llmPort;
    private final WorkflowPort     workflowPort;
    private final CalendarPort     calendarPort;
    private final AuditPort        auditPort;
    private final Counter          draftCounter;
    private final Counter          approvedCounter;
    private final Counter          rejectedCounter;

    public CaInstructionNarrativeService(HikariDataSource dataSource, Executor executor,
                                          LlmSandboxPort llmPort, WorkflowPort workflowPort,
                                          CalendarPort calendarPort, AuditPort auditPort,
                                          MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.llmPort          = llmPort;
        this.workflowPort     = workflowPort;
        this.calendarPort     = calendarPort;
        this.auditPort        = auditPort;
        this.draftCounter     = Counter.builder("ca.narrative.drafts_total").register(registry);
        this.approvedCounter  = Counter.builder("ca.narrative.approved_total").register(registry);
        this.rejectedCounter  = Counter.builder("ca.narrative.rejected_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-04 T2 local LLM — no external API calls. */
    public interface LlmSandboxPort {
        String generateNarrative(String prompt);
    }

    /** K-01 WorkflowPort for HITL maker-checker. */
    public interface WorkflowPort {
        String createApprovalTask(String entityId, String entityType, String assigneeRole,
                                   String description);
    }

    /** K-15 dual-calendar. */
    public interface CalendarPort {
        String toNepaliDate(LocalDate adDate);
        LocalDate addBusinessDays(LocalDate from, int days);
    }

    /** K-07 audit. */
    public interface AuditPort {
        void logNarrativeDraft(String narrativeId, String caId, String prompt, String draft);
        void logNarrativeApproval(String narrativeId, String caId, String approvedBy,
                                   String outcome, String comments);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CaNarrative(String narrativeId, String caId, String status,
                               String draftText, String approvedText,
                               String taskId, LocalDateTime generatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Generate a DRAFT narrative for the given CA and submit for HITL approval. */
    public Promise<CaNarrative> generateNarrative(String caId, String approverRole) {
        return Promise.ofBlocking(executor, () -> {
            CaInfo ca     = loadCaInfo(caId);
            String bsEx   = calendarPort.toNepaliDate(ca.exDate());
            String bsRec  = calendarPort.toNepaliDate(ca.recordDate());
            String bsPay  = calendarPort.toNepaliDate(ca.paymentDate());
            String bsDead = ca.electionDeadline() != null
                    ? calendarPort.toNepaliDate(ca.electionDeadline()) : "N/A";

            String prompt = buildPrompt(ca, bsEx, bsRec, bsPay, bsDead);
            String draft  = llmPort.generateNarrative(prompt);
            draft         = enforceWordCount(draft);

            String narrativeId = UUID.randomUUID().toString();
            auditPort.logNarrativeDraft(narrativeId, caId, prompt, draft);
            draftCounter.increment();

            String taskId = workflowPort.createApprovalTask(narrativeId, "CA_NARRATIVE",
                    approverRole, "Review CA instruction narrative for " + caId);

            return persistNarrative(narrativeId, caId, "DRAFT", draft, null, taskId);
        });
    }

    /** Approve a DRAFT narrative — transitions to FINAL. */
    public Promise<CaNarrative> approveNarrative(String narrativeId, String approvedBy,
                                                   String comments) {
        return Promise.ofBlocking(executor, () -> {
            CaNarrative existing = loadNarrative(narrativeId);
            if (!"DRAFT".equals(existing.status())) {
                throw new IllegalStateException("Narrative " + narrativeId + " is not in DRAFT status");
            }
            auditPort.logNarrativeApproval(narrativeId, existing.caId(), approvedBy,
                    "APPROVED", comments);
            approvedCounter.increment();
            return updateNarrativeStatus(narrativeId, "APPROVED", existing.draftText());
        });
    }

    /** Reject a DRAFT narrative — transitions to REJECTED. */
    public Promise<CaNarrative> rejectNarrative(String narrativeId, String rejectedBy,
                                                  String comments) {
        return Promise.ofBlocking(executor, () -> {
            CaNarrative existing = loadNarrative(narrativeId);
            if (!"DRAFT".equals(existing.status())) {
                throw new IllegalStateException("Narrative " + narrativeId + " is not in DRAFT status");
            }
            auditPort.logNarrativeApproval(narrativeId, existing.caId(), rejectedBy,
                    "REJECTED", comments);
            rejectedCounter.increment();
            return updateNarrativeStatus(narrativeId, "REJECTED", null);
        });
    }

    // ─── Prompt builder ───────────────────────────────────────────────────────

    private String buildPrompt(CaInfo ca, String bsEx, String bsRec, String bsPay, String bsDead) {
        return """
                You are a financial compliance officer. Write a formal %d-%d word instruction notice \
                for a corporate action. Use the following details:
                Corporate Action Type: %s
                Symbol: %s
                Ex-Date: %s (BS) / %s (AD)
                Record Date: %s (BS) / %s (AD)
                Payment Date: %s (BS) / %s (AD)
                Entitlement Ratio: %s
                Election Deadline: %s (BS)
                Entitlement Method: %s
                Write in formal English suitable for SEBON-regulated investor notice.
                """.formatted(MIN_WORD_COUNT, MAX_WORD_COUNT,
                ca.caType(), ca.symbol(),
                bsEx, ca.exDate(), calendarPort.toNepaliDate(ca.recordDate()), ca.recordDate(),
                bsPay, ca.paymentDate(),
                ca.entitlementRatio(), bsDead, ca.entitlementMethod());
    }

    private String enforceWordCount(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length < MIN_WORD_COUNT) {
            throw new IllegalStateException("LLM narrative too short: " + words.length + " words");
        }
        if (words.length > MAX_WORD_COUNT) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_WORD_COUNT; i++) {
                if (i > 0) sb.append(' ');
                sb.append(words[i]);
            }
            return sb.toString();
        }
        return text;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private CaNarrative persistNarrative(String narrativeId, String caId, String status,
                                          String draftText, String approvedText,
                                          String taskId) throws SQLException {
        String sql = """
                INSERT INTO ca_narratives
                    (narrative_id, ca_id, status, draft_text, approved_text, task_id, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (ca_id) DO UPDATE
                    SET status=EXCLUDED.status, draft_text=EXCLUDED.draft_text,
                        approved_text=EXCLUDED.approved_text, task_id=EXCLUDED.task_id,
                        generated_at=EXCLUDED.generated_at
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, narrativeId); ps.setString(2, caId); ps.setString(3, status);
            ps.setString(4, draftText); ps.setString(5, approvedText); ps.setString(6, taskId);
            ps.executeUpdate();
        }
        return new CaNarrative(narrativeId, caId, status, draftText, approvedText, taskId,
                LocalDateTime.now());
    }

    private CaNarrative updateNarrativeStatus(String narrativeId, String status,
                                               String approvedText) throws SQLException {
        String sql = """
                UPDATE ca_narratives SET status=?, approved_text=?
                WHERE narrative_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status); ps.setString(2, approvedText); ps.setString(3, narrativeId);
            ps.executeUpdate();
        }
        return loadNarrative(narrativeId);
    }

    private CaNarrative loadNarrative(String narrativeId) throws SQLException {
        String sql = "SELECT * FROM ca_narratives WHERE narrative_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, narrativeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Narrative not found: " + narrativeId);
                return new CaNarrative(rs.getString("narrative_id"), rs.getString("ca_id"),
                        rs.getString("status"), rs.getString("draft_text"),
                        rs.getString("approved_text"), rs.getString("task_id"),
                        rs.getObject("generated_at", LocalDateTime.class));
            }
        }
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    record CaInfo(String caId, String caType, String symbol, LocalDate exDate,
                  LocalDate recordDate, LocalDate paymentDate, LocalDate electionDeadline,
                  String entitlementRatio, String entitlementMethod) {}

    private CaInfo loadCaInfo(String caId) throws SQLException {
        String sql = """
                SELECT ca_type, symbol, ex_date, record_date, payment_date,
                       election_deadline, entitlement_ratio, entitlement_method
                FROM corporate_actions WHERE ca_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("CA not found: " + caId);
                return new CaInfo(caId, rs.getString("ca_type"), rs.getString("symbol"),
                        rs.getObject("ex_date", LocalDate.class),
                        rs.getObject("record_date", LocalDate.class),
                        rs.getObject("payment_date", LocalDate.class),
                        rs.getObject("election_deadline", LocalDate.class),
                        rs.getString("entitlement_ratio"),
                        rs.getString("entitlement_method"));
            }
        }
    }
}
