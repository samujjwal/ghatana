package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Natural Language Query (NLQ) interface for platform operators.
 *              Translates plain-English questions into PromQL/ES queries via a locally-hosted
 *              LLM (K-04 T2 sandbox), executes them against K-06 Prometheus/ELK, and returns
 *              both a raw data table and a human-readable narrative summary.
 *              Examples: "top 5 services by error rate last 4 hours",
 *                        "tenants with P2+ incidents this week".
 *              All queries audited via K-07. Local LLM only — no external API calls.
 *              Governed by K-09 AI advisory.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-013: Natural language platform query engine
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS nlq_query_log (
 *   log_id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   operator_id     TEXT NOT NULL,
 *   natural_language TEXT NOT NULL,
 *   translated_query TEXT NOT NULL,
 *   query_backend   TEXT NOT NULL,   -- PROMETHEUS | ELASTICSEARCH
 *   result_row_count INT,
 *   duration_ms      BIGINT,
 *   recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class NaturalLanguagePlatformQueryService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface LlmTranslatorPort {
        /** Translate a natural language query to a PromQL expression. Returns null if not applicable. */
        String toPromql(String naturalLanguage) throws Exception;
        /** Translate a natural language query to an ES DSL JSON query. Returns null if not applicable. */
        String toEsDsl(String naturalLanguage) throws Exception;
        /** Generate a human-readable narrative summary from structured result rows. */
        String narrativeSummary(String naturalLanguage, List<Map<String, Object>> rows) throws Exception;
    }

    public interface PrometheusPort {
        /** Execute a PromQL instant or range query. Returns labeled result rows. */
        List<Map<String, Object>> query(String promql) throws Exception;
    }

    public interface ElasticsearchPort {
        /** Execute an ES DSL JSON query. Returns hit rows. */
        List<Map<String, Object>> query(String dslJson, String index) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record NlqResult(
        String naturalLanguage,
        String translatedQuery,
        String backend,
        List<Map<String, Object>> rows,
        String narrativeSummary,
        long durationMs
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final LlmTranslatorPort llm;
    private final PrometheusPort prometheus;
    private final ElasticsearchPort elasticsearch;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter queriesCounter;
    private final Timer queryDurationTimer;

    public NaturalLanguagePlatformQueryService(
        javax.sql.DataSource ds,
        LlmTranslatorPort llm,
        PrometheusPort prometheus,
        ElasticsearchPort elasticsearch,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.llm           = llm;
        this.prometheus    = prometheus;
        this.elasticsearch = elasticsearch;
        this.audit         = audit;
        this.executor      = executor;
        this.queriesCounter    = Counter.builder("operator.nlq.queries").register(registry);
        this.queryDurationTimer = Timer.builder("operator.nlq.duration").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Answer a natural-language platform question. Routing:
     *  1. Try PromQL translation (for metrics questions)
     *  2. Fall back to Elasticsearch DSL (for log/incident/event questions)
     * Returns rows + LLM-generated narrative.
     */
    public Promise<NlqResult> query(String naturalLanguage, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            long startMs = System.currentTimeMillis();

            String translatedQuery;
            String backend;
            List<Map<String, Object>> rows;

            // Attempt PromQL first
            String promql = llm.toPromql(naturalLanguage);
            if (promql != null) {
                translatedQuery = promql;
                backend = "PROMETHEUS";
                rows = prometheus.query(promql);
            } else {
                // Fall back to Elasticsearch
                String esDsl = llm.toEsDsl(naturalLanguage);
                if (esDsl == null) {
                    // Cannot translate — return empty with explanation
                    return new NlqResult(naturalLanguage, "", "NONE", List.of(),
                        "I was unable to translate that query. Please try rephrasing with more specific metric or log terms.", 0L);
                }
                translatedQuery = esDsl;
                backend = "ELASTICSEARCH";
                rows = elasticsearch.query(esDsl, "platform-*");
            }

            String narrative = llm.narrativeSummary(naturalLanguage, rows);
            long durationMs = System.currentTimeMillis() - startMs;
            sample.stop(queryDurationTimer);
            queriesCounter.increment();

            persistLog(operatorId, naturalLanguage, translatedQuery, backend, rows.size(), durationMs);
            audit.record(operatorId, "NLQ_QUERY_EXECUTED", "backend=" + backend + " rows=" + rows.size());

            return new NlqResult(naturalLanguage, translatedQuery, backend, rows, narrative, durationMs);
        });
    }

    /** Return recent NLQ history for an operator (last 50 queries). */
    public Promise<List<Map<String, String>>> queryHistory(String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, String>> history = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT log_id, natural_language, query_backend, result_row_count, duration_ms, recorded_at::text " +
                     "FROM nlq_query_log WHERE operator_id=? ORDER BY recorded_at DESC LIMIT 50"
                 )) {
                ps.setString(1, operatorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("logId",        rs.getString("log_id"));
                        row.put("question",     rs.getString("natural_language"));
                        row.put("backend",      rs.getString("query_backend"));
                        row.put("resultRows",   String.valueOf(rs.getInt("result_row_count")));
                        row.put("durationMs",   String.valueOf(rs.getLong("duration_ms")));
                        row.put("recordedAt",   rs.getString("recorded_at"));
                        history.add(row);
                    }
                }
            }
            return history;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void persistLog(String operatorId, String nlq, String translated, String backend,
                             int rowCount, long durationMs) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO nlq_query_log (operator_id, natural_language, translated_query, query_backend, result_row_count, duration_ms) " +
                 "VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, operatorId); ps.setString(2, nlq); ps.setString(3, translated);
            ps.setString(4, backend); ps.setInt(5, rowCount); ps.setLong(6, durationMs);
            ps.executeUpdate();
        }
    }
}
