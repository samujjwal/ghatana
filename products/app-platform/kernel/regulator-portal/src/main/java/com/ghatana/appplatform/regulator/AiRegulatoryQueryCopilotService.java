package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose RAG-based AI copilot for regulators to query platform data in natural language.
 *              Only a local LLM (no external API calls) is used — K-04 T2 constraint.
 *              RBAC enforced: only questions within the regulator's assigned jurisdictions
 *              are answered; cross-jurisdiction queries are rejected.
 *              All queries and responses are logged to K-07 immutable audit trail.
 *              RAG pipeline: embed query → retrieve relevant regulatory docs →
 *              augment prompt → generate response via local LLM.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer; RAG
 *
 * STORY-R01-011: AI regulatory query copilot — RAG + local LLM + RBAC
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulator_copilot_sessions (
 *   session_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   regulator_id    TEXT NOT NULL,
 *   jurisdiction    TEXT NOT NULL,
 *   question        TEXT NOT NULL,
 *   retrieved_docs  JSONB,
 *   response        TEXT,
 *   input_tokens    INT,
 *   output_tokens   INT,
 *   duration_ms     BIGINT,
 *   asked_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class AiRegulatoryQueryCopilotService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface JurisdictionRbacPort {
        /** Returns true if regulatorId is licensed for this jurisdiction. */
        boolean isAuthorized(String regulatorId, String jurisdiction) throws Exception;
    }

    public interface LocalEmbeddingPort {
        float[] embed(String text) throws Exception;
    }

    public interface RegulatoryDocStorePort {
        /** Retrieve top-k relevant doc chunks for the query embedding from the jurisdiction's doc store. */
        List<DocChunk> retrieve(float[] queryEmbedding, String jurisdiction, int topK) throws Exception;
    }

    public interface LocalLlmPort {
        /** Generate answer with RAG context. Must run local model only — no internet calls. */
        LlmResponse generate(String systemPrompt, String userPrompt) throws Exception;
    }

    public interface K07AuditPort {
        void record(String regulatorId, String action, String detail) throws Exception;
    }

    public record DocChunk(String docId, String title, String snippet) {}
    public record LlmResponse(String text, int inputTokens, int outputTokens) {}

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int TOP_K_DOCS    = 5;
    private static final String SYSTEM_PROMPT =
        "You are a compliance assistant for financial regulators. " +
        "Answer using only the provided regulatory documents. " +
        "If the answer is not in the documents, say you do not have enough information. " +
        "Never speculate or provide legal advice.";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final JurisdictionRbacPort rbac;
    private final LocalEmbeddingPort embedding;
    private final RegulatoryDocStorePort docStore;
    private final LocalLlmPort llm;
    private final K07AuditPort audit;
    private final Executor executor;
    private final Counter queries;
    private final Counter rbacRejections;
    private final Timer queryDuration;

    public AiRegulatoryQueryCopilotService(
        javax.sql.DataSource ds,
        JurisdictionRbacPort rbac,
        LocalEmbeddingPort embedding,
        RegulatoryDocStorePort docStore,
        LocalLlmPort llm,
        K07AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.rbac            = rbac;
        this.embedding       = embedding;
        this.docStore        = docStore;
        this.llm             = llm;
        this.audit           = audit;
        this.executor        = executor;
        this.queries         = Counter.builder("regulator.copilot.queries").register(registry);
        this.rbacRejections  = Counter.builder("regulator.copilot.rbac_rejections").register(registry);
        this.queryDuration   = Timer.builder("regulator.copilot.duration").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Ask the copilot a question within a specific jurisdiction. */
    public Promise<Map<String, Object>> ask(String regulatorId, String jurisdiction, String question) {
        return Promise.ofBlocking(executor, () -> {
            // RBAC gate
            if (!rbac.isAuthorized(regulatorId, jurisdiction)) {
                rbacRejections.increment();
                audit.record(regulatorId, "COPILOT_RBAC_REJECTED",
                    "jurisdiction=" + jurisdiction + " question=" + truncate(question, 80));
                throw new SecurityException("Regulator not authorized for jurisdiction: " + jurisdiction);
            }

            long start = System.currentTimeMillis();

            // Embed the question
            float[] queryVec = embedding.embed(question);

            // Retrieve relevant doc chunks
            List<DocChunk> chunks = docStore.retrieve(queryVec, jurisdiction, TOP_K_DOCS);

            // Build augmented user prompt
            StringBuilder ctx = new StringBuilder("Regulatory documents:\n");
            for (int i = 0; i < chunks.size(); i++) {
                DocChunk c = chunks.get(i);
                ctx.append("[").append(i + 1).append("] ").append(c.title()).append(":\n").append(c.snippet()).append("\n\n");
            }
            ctx.append("Question: ").append(question);

            // Generate with local LLM
            LlmResponse response = llm.generate(SYSTEM_PROMPT, ctx.toString());

            long elapsed = System.currentTimeMillis() - start;
            queryDuration.record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);

            // Serialise retrieved doc refs for storage
            String docsJson = buildDocsJson(chunks);

            String sessionId = persistSession(regulatorId, jurisdiction, question, docsJson,
                response.text(), response.inputTokens(), response.outputTokens(), elapsed);

            audit.record(regulatorId, "COPILOT_QUERY",
                "sessionId=" + sessionId + " jurisdiction=" + jurisdiction + " durationMs=" + elapsed);
            queries.increment();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId",     sessionId);
            result.put("response",      response.text());
            result.put("sourceDocuments", chunks.stream().map(c ->
                Map.of("docId", c.docId(), "title", c.title())).toList());
            result.put("durationMs",    elapsed);
            return result;
        });
    }

    /** Return session history for a regulator (last 20). */
    public Promise<List<Map<String, Object>>> history(String regulatorId) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT session_id, jurisdiction, question, response, asked_at " +
                     "FROM regulator_copilot_sessions WHERE regulator_id=? ORDER BY asked_at DESC LIMIT 20"
                 )) {
                ps.setString(1, regulatorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sessionId",   rs.getString("session_id"));
                        m.put("jurisdiction", rs.getString("jurisdiction"));
                        m.put("question",    rs.getString("question"));
                        m.put("response",    rs.getString("response"));
                        m.put("askedAt",     rs.getTimestamp("asked_at").toInstant().toString());
                        list.add(m);
                    }
                }
            }
            return list;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String persistSession(String regulatorId, String jurisdiction, String question,
                                   String docsJson, String response, int inputTokens,
                                   int outputTokens, long durationMs) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulator_copilot_sessions " +
                 "(regulator_id, jurisdiction, question, retrieved_docs, response, input_tokens, output_tokens, duration_ms) " +
                 "VALUES (?,?,?,?::jsonb,?,?,?,?) RETURNING session_id"
             )) {
            ps.setString(1, regulatorId); ps.setString(2, jurisdiction);
            ps.setString(3, question);    ps.setString(4, docsJson);
            ps.setString(5, response);    ps.setInt(6, inputTokens);
            ps.setInt(7, outputTokens);   ps.setLong(8, durationMs);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private String buildDocsJson(List<DocChunk> chunks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) sb.append(',');
            DocChunk c = chunks.get(i);
            sb.append("{\"docId\":\"").append(escape(c.docId()))
              .append("\",\"title\":\"").append(escape(c.title())).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
