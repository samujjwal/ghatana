package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Local LLM (K-04 T2 sandbox, no external API) generates 200–400 word suspicious
 *              activity report narratives in SEBON/NRB regulatory format. Inputs: alert struct
 *              (type, accounts, anomaly score, SHAP features, trade timeline, regulatory context).
 *              Outputs: draft narrative requiring HITL approval before being marked FINAL.
 *              K-09 advisory tier. K-07 AuditPort logs every prompt, draft, and approval.
 *              Satisfies STORY-D08-016.
 * @doc.layer   Domain
 * @doc.pattern LLM narrative; K-04 T2 sandbox; HITL approval; K-07 immutable prompt/draft log.
 */
public class AlertNarrativeGenerationService {

    private static final int MIN_WORD_COUNT = 200;
    private static final int MAX_WORD_COUNT = 400;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LlmSandboxPort   llmSandboxPort;
    private final AuditPort        auditPort;
    private final Counter          narrativeDraftedCounter;
    private final Counter          narrativeApprovedCounter;

    public AlertNarrativeGenerationService(HikariDataSource dataSource, Executor executor,
                                            LlmSandboxPort llmSandboxPort, AuditPort auditPort,
                                            MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.llmSandboxPort           = llmSandboxPort;
        this.auditPort                = auditPort;
        this.narrativeDraftedCounter  = Counter.builder("surveillance.narrative.drafted_total").register(registry);
        this.narrativeApprovedCounter = Counter.builder("surveillance.narrative.approved_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-04 T2 local LLM sandbox — no external API calls. */
    public interface LlmSandboxPort {
        String generate(String prompt, int minWords, int maxWords);
    }

    /** K-07 immutable audit of prompts and drafts. */
    public interface AuditPort {
        void logNarrativeDraft(String narrativeId, String alertId, String prompt, String draft,
                               LocalDateTime at);
        void logNarrativeApproval(String narrativeId, String approverAnalystId, LocalDateTime at);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum NarrativeStatus { DRAFT, APPROVED, REJECTED }

    public record AlertContext(String alertId, String alertType, List<String> accounts,
                               double anomalyScore, List<String> shapFeatures,
                               String tradeSummary, String regulatoryContext) {}

    public record AlertNarrative(String narrativeId, String alertId, String prompt,
                                  String draftText, NarrativeStatus status,
                                  String approvedBy, LocalDateTime createdAt,
                                  LocalDateTime reviewedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<AlertNarrative> generateDraft(AlertContext ctx) {
        return Promise.ofBlocking(executor, () -> {
            String prompt = buildPrompt(ctx);
            String draft  = llmSandboxPort.generate(prompt, MIN_WORD_COUNT, MAX_WORD_COUNT);
            String narrativeId = UUID.randomUUID().toString();
            AlertNarrative narrative = persistNarrative(narrativeId, ctx.alertId(), prompt, draft);
            auditPort.logNarrativeDraft(narrativeId, ctx.alertId(), prompt, draft, LocalDateTime.now());
            narrativeDraftedCounter.increment();
            return narrative;
        });
    }

    public Promise<AlertNarrative> approveNarrative(String narrativeId, String analystId) {
        return Promise.ofBlocking(executor, () -> {
            updateNarrativeStatus(narrativeId, NarrativeStatus.APPROVED, analystId);
            auditPort.logNarrativeApproval(narrativeId, analystId, LocalDateTime.now());
            narrativeApprovedCounter.increment();
            return loadNarrative(narrativeId);
        });
    }

    public Promise<AlertNarrative> rejectNarrative(String narrativeId, String analystId,
                                                     String rejectionNotes) {
        return Promise.ofBlocking(executor, () -> {
            updateNarrativeStatus(narrativeId, NarrativeStatus.REJECTED, analystId);
            auditPort.logNarrativeApproval(narrativeId, analystId, LocalDateTime.now());
            return loadNarrative(narrativeId);
        });
    }

    // ─── Prompt construction ─────────────────────────────────────────────────

    private String buildPrompt(AlertContext ctx) {
        return "You are a compliance officer drafting a Suspicious Activity Report (SAR) " +
               "in SEBON/NRB regulatory format. Respond in 200-400 words. Use formal language. " +
               "Do not speculate beyond the provided facts.\n\n" +
               "ALERT TYPE: " + ctx.alertType() + "\n" +
               "SUBJECT ACCOUNTS: " + String.join(", ", ctx.accounts()) + "\n" +
               "ANOMALY SCORE: " + String.format("%.4f", ctx.anomalyScore()) + "\n" +
               "KEY INDICATORS (SHAP): " + String.join(", ", ctx.shapFeatures()) + "\n" +
               "TRADE TIMELINE SUMMARY:\n" + ctx.tradeSummary() + "\n" +
               "REGULATORY CONTEXT:\n" + ctx.regulatoryContext() + "\n\n" +
               "Draft the SAR narrative:";
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private AlertNarrative persistNarrative(String narrativeId, String alertId,
                                             String prompt, String draft) throws SQLException {
        String sql = """
                INSERT INTO alert_narratives
                    (narrative_id, alert_id, prompt, draft_text, status, created_at)
                VALUES (?, ?, ?, ?, 'DRAFT', NOW())
                ON CONFLICT (narrative_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, narrativeId);
            ps.setString(2, alertId);
            ps.setString(3, prompt);
            ps.setString(4, draft);
            ps.executeUpdate();
        }
        return loadNarrative(narrativeId);
    }

    private void updateNarrativeStatus(String narrativeId, NarrativeStatus status,
                                        String analystId) throws SQLException {
        String sql = """
                UPDATE alert_narratives
                SET status=?, approved_by=?, reviewed_at=NOW()
                WHERE narrative_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, analystId);
            ps.setString(3, narrativeId);
            ps.executeUpdate();
        }
    }

    private AlertNarrative loadNarrative(String narrativeId) throws SQLException {
        String sql = """
                SELECT narrative_id, alert_id, prompt, draft_text, status, approved_by,
                       created_at, reviewed_at
                FROM alert_narratives WHERE narrative_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, narrativeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Narrative not found: " + narrativeId);
                return new AlertNarrative(rs.getString("narrative_id"), rs.getString("alert_id"),
                        rs.getString("prompt"), rs.getString("draft_text"),
                        NarrativeStatus.valueOf(rs.getString("status")),
                        rs.getString("approved_by"),
                        rs.getObject("created_at",  LocalDateTime.class),
                        rs.getObject("reviewed_at", LocalDateTime.class));
            }
        }
    }
}
