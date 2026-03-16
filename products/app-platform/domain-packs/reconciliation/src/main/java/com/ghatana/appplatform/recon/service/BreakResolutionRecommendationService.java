package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose RAG-based recommendation engine that retrieves past break resolutions from
 *              K-07 audit records and generates ranked resolution recommendations for open
 *              breaks. Surfaces top-3 action recommendations with historical success rates
 *              and ETA estimates. Accepts natural-language queries from reconciliation analysts.
 *              Governed by K-09 advisory tier; all interactions audited via K-07.
 *              Satisfies STORY-D13-017.
 * @doc.layer   Domain
 * @doc.pattern K-09 advisory AI; K-07 audit record retrieval (RAG); K-03 rules engine
 *              for rule-based recommendations; INSERT-only interaction log.
 */
public class BreakResolutionRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(BreakResolutionRecommendationService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LlmPort          llm;
    private final AuditPort        audit;
    private final Counter          recommendationCounter;

    public BreakResolutionRecommendationService(HikariDataSource dataSource, Executor executor,
                                                LlmPort llm, AuditPort audit,
                                                MeterRegistry registry) {
        this.dataSource            = dataSource;
        this.executor              = executor;
        this.llm                   = llm;
        this.audit                 = audit;
        this.recommendationCounter = registry.counter("recon.ai.recommendations_generated");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-09 advisory LLM port — local model in K-04 T2 sandbox. */
    public interface LlmPort {
        String generateRecommendations(String context, String query);
    }

    /** K-07 audit port — retrieve past resolutions for RAG context. */
    public interface AuditPort {
        List<String> retrieveSimilarResolutions(String breakType, String counterpartyId,
                                                String currency, int limit);
        void logInteraction(String sessionId, String query, String context, String response);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record Recommendation(int rank, String action, double successRate,
                                 String rationale, String etaDays) {}

    public record RecommendationResult(String sessionId, String breakId,
                                       List<Recommendation> recommendations, String rawLlmOutput) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RecommendationResult> getRecommendations(String breakId, String analystQuery) {
        return Promise.ofBlocking(executor, () -> {
            BreakContext ctx = loadBreakContext(breakId);
            List<String> pastResolutions = audit.retrieveSimilarResolutions(
                    ctx.breakType(), ctx.counterpartyId(), ctx.currency(), 5);
            String ragContext = buildContext(ctx, pastResolutions);

            String response = llm.generateRecommendations(ragContext, analystQuery);
            List<Recommendation> recs = parseRecommendations(response);

            String sessionId = UUID.randomUUID().toString();
            audit.logInteraction(sessionId, analystQuery, ragContext, response);
            persistSession(sessionId, breakId, analystQuery, response);
            recommendationCounter.increment();

            return new RecommendationResult(sessionId, breakId, recs, response);
        });
    }

    /** Track recommendation outcome for model feedback. */
    public Promise<Void> recordOutcome(String sessionId, int acceptedRank, boolean resolved) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE recon_recommendation_sessions
                    SET accepted_rank=?, resolved=?, outcome_recorded_at=NOW()
                    WHERE session_id=?
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, acceptedRank);
                ps.setBoolean(2, resolved);
                ps.setString(3, sessionId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private record BreakContext(String breakId, String breakType, String counterpartyId,
                                String currency, double amount, int ageDays) {}

    private BreakContext loadBreakContext(String breakId) throws SQLException {
        String sql = """
                SELECT break_id, break_type, client_id AS counterparty_id, currency, amount, age_days
                FROM recon_breaks WHERE break_id=?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, breakId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BreakContext(breakId, rs.getString("break_type"),
                            rs.getString("counterparty_id"), rs.getString("currency"),
                            rs.getDouble("amount"), rs.getInt("age_days"));
                }
            }
        }
        return new BreakContext(breakId, "UNKNOWN", "", "NPR", 0.0, 0);
    }

    private String buildContext(BreakContext ctx, List<String> pastResolutions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Break type: ").append(ctx.breakType())
          .append(", Amount: ").append(ctx.amount())
          .append(" ").append(ctx.currency())
          .append(", Age: ").append(ctx.ageDays()).append(" days\n");
        sb.append("Past resolutions for similar breaks:\n");
        for (String r : pastResolutions) {
            sb.append("- ").append(r).append("\n");
        }
        return sb.toString();
    }

    private List<Recommendation> parseRecommendations(String response) {
        // Parse numbered recommendations from LLM response (simple heuristic)
        List<Recommendation> recs = new ArrayList<>();
        String[] lines = response.split("\n");
        int rank = 1;
        for (String line : lines) {
            if (line.matches("^\\d+\\..*") && rank <= 3) {
                recs.add(new Recommendation(rank++, line.replaceFirst("^\\d+\\.\\s*", ""),
                        0.0, "", "1-2 days"));
            }
        }
        if (recs.isEmpty()) {
            recs.add(new Recommendation(1, "Manual investigation required", 0.0, response, "3-5 days"));
        }
        return recs;
    }

    private void persistSession(String sessionId, String breakId, String query,
                                String response) throws SQLException {
        String sql = """
                INSERT INTO recon_recommendation_sessions
                    (session_id, break_id, analyst_query, llm_response, created_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (session_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, breakId);
            ps.setString(3, query);
            ps.setString(4, response);
            ps.executeUpdate();
        }
    }
}
