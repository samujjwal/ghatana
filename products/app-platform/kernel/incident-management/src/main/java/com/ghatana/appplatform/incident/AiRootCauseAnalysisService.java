package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose AI-assisted root cause analysis.
 *              Triggered on incident creation; generates RCA hypothesis within 3 minutes.
 *              Context sources: K-06 correlated alerts, K-07 recent deployments, K-08 data lineage.
 *              RCA output is attached to the PIR; rejected hypotheses are fed back to
 *              MlIncidentPatternDetectionService for cluster improvement.
 *              Local LLM only — no external API calls.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer; RAG + local LLM
 *
 * STORY-R02-012: AI root cause analysis — LLM hypothesis + K-06/K-07/K-08 context + PIR attachment
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS incident_rca_hypotheses (
 *   hypothesis_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   incident_id      TEXT NOT NULL,
 *   pir_id           TEXT,           -- set when attached to PIR
 *   hypothesis       TEXT NOT NULL,
 *   confidence_score NUMERIC(4,3),
 *   context_alerts   JSONB,
 *   context_deploys  JSONB,
 *   context_lineage  JSONB,
 *   status           TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | REJECTED
 *   rejection_reason TEXT,
 *   generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   duration_ms      BIGINT
 * );
 * </pre>
 */
public class AiRootCauseAnalysisService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface K06AlertContextPort {
        /** Returns correlated alerts for an incident within the last 2 hours. */
        List<Map<String, String>> getCorrelatedAlerts(String incidentId) throws Exception;
    }

    public interface K07DeploymentContextPort {
        /** Returns deployments within the last 4 hours at time of incident. */
        List<Map<String, String>> getRecentDeployments(String incidentId) throws Exception;
    }

    public interface K08LineagePort {
        /** Returns upstream/downstream lineage for the incident's affected service. */
        List<Map<String, String>> getLineage(String service) throws Exception;
    }

    public interface LocalLlmPort {
        /** Generate RCA hypothesis from system + user prompt. Local model only. */
        LlmResponse generate(String systemPrompt, String userPrompt) throws Exception;
    }

    public interface MlFeedbackPort {
        /** Feed rejected hypothesis back to improve ML clustering. */
        void feedbackRejected(String incidentId, String hypothesis, String rejectionReason) throws Exception;
    }

    public record LlmResponse(String hypothesis, double confidence, int durationMs) {}

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT =
        "You are a Senior Site Reliability Engineer performing root cause analysis. " +
        "Given the incident description and the correlated alert signals, recent deployments, " +
        "and data lineage context provided below, generate a concise root cause hypothesis. " +
        "Also provide a confidence score between 0.0 and 1.0. " +
        "Format: RCA: <hypothesis>\\nConfidence: <score>";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final K06AlertContextPort k06Alerts;
    private final K07DeploymentContextPort k07Deploys;
    private final K08LineagePort k08Lineage;
    private final LocalLlmPort llm;
    private final MlFeedbackPort mlFeedback;
    private final Executor executor;
    private final Counter hypothesesGenerated;
    private final Counter hypothesesAccepted;
    private final Counter hypothesesRejected;
    private final Timer rcaDuration;

    public AiRootCauseAnalysisService(
        javax.sql.DataSource ds,
        K06AlertContextPort k06Alerts,
        K07DeploymentContextPort k07Deploys,
        K08LineagePort k08Lineage,
        LocalLlmPort llm,
        MlFeedbackPort mlFeedback,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                   = ds;
        this.k06Alerts            = k06Alerts;
        this.k07Deploys           = k07Deploys;
        this.k08Lineage           = k08Lineage;
        this.llm                  = llm;
        this.mlFeedback           = mlFeedback;
        this.executor             = executor;
        this.hypothesesGenerated  = Counter.builder("incident.rca.hypotheses_generated").register(registry);
        this.hypothesesAccepted   = Counter.builder("incident.rca.hypotheses_accepted").register(registry);
        this.hypothesesRejected   = Counter.builder("incident.rca.hypotheses_rejected").register(registry);
        this.rcaDuration          = Timer.builder("incident.rca.duration").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generate a root cause hypothesis for an incident.
     * Returns hypothesisId. Should be called within 3 minutes of incident creation.
     */
    public Promise<String> generateHypothesis(String incidentId, String incidentSummary, String affectedService) {
        return Promise.ofBlocking(executor, () -> {
            long start = System.currentTimeMillis();

            // Gather context from K-06, K-07, K-08
            List<Map<String, String>> alerts  = k06Alerts.getCorrelatedAlerts(incidentId);
            List<Map<String, String>> deploys = k07Deploys.getRecentDeployments(incidentId);
            List<Map<String, String>> lineage = k08Lineage.getLineage(affectedService);

            // Build augmented prompt
            String userPrompt = buildPrompt(incidentSummary, affectedService, alerts, deploys, lineage);

            // Generate hypothesis via local LLM
            LlmResponse response = llm.generate(SYSTEM_PROMPT, userPrompt);
            long elapsed = System.currentTimeMillis() - start;
            rcaDuration.record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);

            String hypothesisId = persistHypothesis(incidentId, response.hypothesis(),
                response.confidence(), mapsToJson(alerts), mapsToJson(deploys), mapsToJson(lineage), elapsed);

            hypothesesGenerated.increment();
            return hypothesisId;
        });
    }

    /** Attach an accepted hypothesis to a PIR. */
    public Promise<Void> accept(String hypothesisId, String pirId, String acceptedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_rca_hypotheses SET status='ACCEPTED', pir_id=? WHERE hypothesis_id=?"
                 )) { ps.setString(1, pirId); ps.setString(2, hypothesisId); ps.executeUpdate(); }
            hypothesesAccepted.increment();
            return null;
        });
    }

    /** Reject a hypothesis — feeds back to ML pattern service. */
    public Promise<Void> reject(String hypothesisId, String rejectionReason, String rejectedBy) {
        return Promise.ofBlocking(executor, () -> {
            String[] incAndHyp = getIncidentAndHypothesis(hypothesisId);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_rca_hypotheses SET status='REJECTED', rejection_reason=? WHERE hypothesis_id=?"
                 )) { ps.setString(1, rejectionReason); ps.setString(2, hypothesisId); ps.executeUpdate(); }
            mlFeedback.feedbackRejected(incAndHyp[0], incAndHyp[1], rejectionReason);
            hypothesesRejected.increment();
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildPrompt(String summary, String service,
                                List<Map<String, String>> alerts,
                                List<Map<String, String>> deploys,
                                List<Map<String, String>> lineage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Incident: ").append(summary).append("\nAffected Service: ").append(service).append("\n\n");
        sb.append("=== Correlated Alerts ===\n");
        for (Map<String, String> a : alerts) sb.append(a).append("\n");
        sb.append("\n=== Recent Deployments (last 4h) ===\n");
        for (Map<String, String> d : deploys) sb.append(d).append("\n");
        sb.append("\n=== Data Lineage ===\n");
        for (Map<String, String> l : lineage) sb.append(l).append("\n");
        return sb.toString();
    }

    private String persistHypothesis(String incidentId, String hypothesis, double confidence,
                                      String alertsJson, String deploysJson, String lineageJson,
                                      long durationMs) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO incident_rca_hypotheses " +
                 "(incident_id, hypothesis, confidence_score, context_alerts, context_deploys, context_lineage, duration_ms) " +
                 "VALUES (?,?,?,?::jsonb,?::jsonb,?::jsonb,?) RETURNING hypothesis_id"
             )) {
            ps.setString(1, incidentId); ps.setString(2, hypothesis); ps.setDouble(3, confidence);
            ps.setString(4, alertsJson); ps.setString(5, deploysJson); ps.setString(6, lineageJson);
            ps.setLong(7, durationMs);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private String[] getIncidentAndHypothesis(String hypothesisId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT incident_id, hypothesis FROM incident_rca_hypotheses WHERE hypothesis_id=?"
             )) {
            ps.setString(1, hypothesisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new String[]{rs.getString("incident_id"), rs.getString("hypothesis")}
                                 : new String[]{"", ""};
            }
        }
    }

    private String mapsToJson(List<Map<String, String>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (Map<String, String> m : list) {
            sb.append("{");
            m.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
            if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
            sb.append("},");
        }
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append("]").toString();
    }
}
