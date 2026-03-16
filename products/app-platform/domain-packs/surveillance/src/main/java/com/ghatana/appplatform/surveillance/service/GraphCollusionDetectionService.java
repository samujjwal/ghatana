package com.ghatana.appplatform.surveillance.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose K-09 TIER_3 GNN (GraphSAGE) detection of coordinated trading collusion.
 *              Graph: nodes=accounts, edges=co-trading in same 30-minute window.
 *              Beneficial ownership linkage from D-07 enriches the graph. Nightly run on
 *              a 7-day rolling window produces cluster-level suspicion scores with
 *              SHAP-over-GNN feature attribution. Fires CollaborativeAbuseDetected event
 *              for clusters crossing the score threshold.
 *              Satisfies STORY-D08-015.
 * @doc.layer   Domain
 * @doc.pattern K-09 TIER_3; GraphSAGE embedding; GNN cluster scoring; SHAP attribution.
 */
public class GraphCollusionDetectionService {

    private static final double HIGH_COLLUSION_THRESHOLD = 0.75;
    private static final int    CO_TRADE_WINDOW_MINUTES  = 30;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final GnnModelPort     gnnModelPort;
    private final EventPort        eventPort;
    private final AuditPort        auditPort;
    private final Counter          clusterDetectedCounter;
    private final Counter          collusionAlertCounter;

    public GraphCollusionDetectionService(HikariDataSource dataSource, Executor executor,
                                           GnnModelPort gnnModelPort, EventPort eventPort,
                                           AuditPort auditPort, MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.gnnModelPort           = gnnModelPort;
        this.eventPort              = eventPort;
        this.auditPort              = auditPort;
        this.clusterDetectedCounter = Counter.builder("surveillance.gnn.clusters_detected_total").register(registry);
        this.collusionAlertCounter  = Counter.builder("surveillance.gnn.collusion_alerts_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-09 TIER_3 GNN inference port. */
    public interface GnnModelPort {
        List<ClusterScore> inferClusters(List<GraphNode> nodes, List<GraphEdge> edges);
        List<String> shapTopFeatures(String clusterId, int topK);
    }

    public interface EventPort {
        void publish(String topic, Object payload);
    }

    public interface AuditPort {
        void logCollusionRun(LocalDate runDate, int clusters, int alerts, LocalDateTime at);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record GraphNode(String accountId, String clientId, boolean hasLinkedOwner,
                            String ownershipGroupId) {}

    public record GraphEdge(String fromAccount, String toAccount, String instrumentId,
                            int coTradeCount, int windowCount) {}

    public record ClusterScore(String clusterId, List<String> memberAccounts,
                               double collusionScore, String topShapFeature) {}

    public record CollusionAlert(String alertId, String clusterId, List<String> memberAccounts,
                                  double collusionScore, List<String> shapFeatures,
                                  LocalDate runDate, LocalDateTime detectedAt) {}

    public record RunResult(LocalDate runDate, int graphNodes, int graphEdges,
                             int totalClusters, int alertsFired) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RunResult> runNightly(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            LocalDate windowStart = runDate.minusDays(7);
            List<GraphNode> nodes = buildNodes(windowStart, runDate);
            List<GraphEdge> edges = buildEdges(windowStart, runDate);

            List<ClusterScore> clusters = gnnModelPort.inferClusters(nodes, edges);
            clusterDetectedCounter.increment(clusters.size());

            List<CollusionAlert> alerts = new ArrayList<>();
            for (ClusterScore cs : clusters) {
                if (cs.collusionScore() >= HIGH_COLLUSION_THRESHOLD) {
                    List<String> shap = gnnModelPort.shapTopFeatures(cs.clusterId(), 5);
                    CollusionAlert alert = persistAlert(cs, shap, runDate);
                    eventPort.publish("surveillance.collusion.detected", alert);
                    alerts.add(alert);
                    collusionAlertCounter.increment();
                }
            }
            auditPort.logCollusionRun(runDate, clusters.size(), alerts.size(), LocalDateTime.now());
            return new RunResult(runDate, nodes.size(), edges.size(), clusters.size(), alerts.size());
        });
    }

    // ─── Graph construction ──────────────────────────────────────────────────

    private List<GraphNode> buildNodes(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT DISTINCT t.account_id, t.client_id,
                       bo.owner_group_id IS NOT NULL AS has_linked_owner,
                       COALESCE(bo.owner_group_id, 'NONE') AS ownership_group_id
                FROM trades t
                LEFT JOIN beneficial_owner_groups bo ON bo.client_id = t.client_id
                WHERE t.trade_date BETWEEN ? AND ?
                """;
        List<GraphNode> nodes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new GraphNode(rs.getString("account_id"), rs.getString("client_id"),
                            rs.getBoolean("has_linked_owner"), rs.getString("ownership_group_id")));
                }
            }
        }
        return nodes;
    }

    private List<GraphEdge> buildEdges(LocalDate from, LocalDate to) throws SQLException {
        // Two accounts are co-trading if they both traded the same instrument within CO_TRADE_WINDOW_MINUTES
        String sql = """
                SELECT a.account_id AS from_account, b.account_id AS to_account,
                       a.instrument_id,
                       COUNT(*)                                          AS co_trade_count,
                       COUNT(DISTINCT DATE_TRUNC('hour', a.trade_time)) AS window_count
                FROM trades a
                JOIN trades b ON b.instrument_id      = a.instrument_id
                              AND b.account_id        != a.account_id
                              AND ABS(EXTRACT(EPOCH FROM (b.trade_time - a.trade_time))/60) <= ?
                WHERE a.trade_date BETWEEN ? AND ?
                GROUP BY a.account_id, b.account_id, a.instrument_id
                HAVING COUNT(*) >= 2
                """;
        List<GraphEdge> edges = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, CO_TRADE_WINDOW_MINUTES);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    edges.add(new GraphEdge(rs.getString("from_account"), rs.getString("to_account"),
                            rs.getString("instrument_id"), rs.getInt("co_trade_count"),
                            rs.getInt("window_count")));
                }
            }
        }
        return edges;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private CollusionAlert persistAlert(ClusterScore cs, List<String> shap, LocalDate runDate)
            throws SQLException {
        String alertId = UUID.randomUUID().toString();
        String members = String.join(",", cs.memberAccounts());
        String shapStr = String.join(",", shap);
        String sql = """
                INSERT INTO collusion_alerts
                    (alert_id, cluster_id, member_accounts, collusion_score, shap_features, run_date, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (cluster_id, run_date) DO UPDATE
                SET collusion_score = EXCLUDED.collusion_score,
                    shap_features   = EXCLUDED.shap_features,
                    detected_at     = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alertId);
            ps.setString(2, cs.clusterId());
            ps.setString(3, members);
            ps.setDouble(4, cs.collusionScore());
            ps.setString(5, shapStr);
            ps.setObject(6, runDate);
            ps.executeUpdate();
        }
        return new CollusionAlert(alertId, cs.clusterId(), cs.memberAccounts(), cs.collusionScore(),
                shap, runDate, LocalDateTime.now());
    }
}
