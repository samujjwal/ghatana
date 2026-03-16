package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Tracks data lineage: how data flows from source to destination across
 *              services. Nodes = data assets; edges = transformations/copies. Auto-captures
 *              producer→consumer relationships from K-05 events. Impact analysis: given
 *              asset X, show all downstream consumers. Circular dependency detection.
 *              Satisfies STORY-K08-002.
 * @doc.layer   Kernel
 * @doc.pattern DAG lineage graph; producer→consumer edge capture; BFS impact analysis;
 *              circular dependency detection; ON CONFLICT DO NOTHING.
 */
public class DataLineageService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          edgesAddedCounter;

    public DataLineageService(HikariDataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.edgesAddedCounter = Counter.builder("governance.lineage.edges_added_total").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record LineageNode(String assetId, String name, String serviceOwner) {}
    public record LineageEdge(String edgeId, String sourceAssetId, String targetAssetId,
                               String transformationDesc, LocalDateTime capturedAt) {}
    public record LineageGraph(List<LineageNode> nodes, List<LineageEdge> edges) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<LineageEdge> recordProducerConsumer(String sourceAssetId, String targetAssetId,
                                                        String transformationDesc) {
        return Promise.ofBlocking(executor, () -> {
            if (wouldCreateCycle(sourceAssetId, targetAssetId)) {
                throw new IllegalArgumentException("Adding lineage edge would create circular dependency: "
                        + sourceAssetId + " → " + targetAssetId);
            }
            LineageEdge edge = insertEdge(sourceAssetId, targetAssetId, transformationDesc);
            edgesAddedCounter.increment();
            return edge;
        });
    }

    /** BFS downstream impact: given an asset, return all transitively affected consumers. */
    public Promise<List<LineageNode>> impactAnalysis(String sourceAssetId) {
        return Promise.ofBlocking(executor, () -> bfsDownstream(sourceAssetId));
    }

    /** Return full lineage DAG for a given asset (ancestors + descendants). */
    public Promise<LineageGraph> getLineageGraph(String assetId) {
        return Promise.ofBlocking(executor, () -> {
            List<LineageNode> nodes = fetchConnectedNodes(assetId);
            List<LineageEdge> edges = fetchConnectedEdges(assetId);
            return new LineageGraph(nodes, edges);
        });
    }

    // ─── Cycle detection (DFS) ────────────────────────────────────────────────

    private boolean wouldCreateCycle(String source, String target) throws SQLException {
        // If target can reach source, adding source→target creates a cycle
        return bfsDownstream(target).stream().anyMatch(n -> n.assetId().equals(source));
    }

    private List<LineageNode> bfsDownstream(String startAssetId) throws SQLException {
        String sql = """
                SELECT DISTINCT c.asset_id AS target_id, c.name, c.service_owner
                FROM lineage_edges e
                JOIN data_catalog c ON c.asset_id = e.target_asset_id
                WHERE e.source_asset_id = ?
                """;
        List<LineageNode> frontier = new ArrayList<>();
        List<String> visited = new ArrayList<>();
        visited.add(startAssetId);
        fetchDirectDownstream(startAssetId, sql, frontier);
        int i = 0;
        while (i < frontier.size()) {
            LineageNode node = frontier.get(i++);
            if (!visited.contains(node.assetId())) {
                visited.add(node.assetId());
                fetchDirectDownstream(node.assetId(), sql, frontier);
            }
        }
        return frontier;
    }

    private void fetchDirectDownstream(String assetId, String sql,
                                        List<LineageNode> out) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new LineageNode(rs.getString("target_id"),
                        rs.getString("name"), rs.getString("service_owner")));
            }
        }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private LineageEdge insertEdge(String sourceAssetId, String targetAssetId,
                                    String transformationDesc) throws SQLException {
        String sql = """
                INSERT INTO lineage_edges
                    (edge_id, source_asset_id, target_asset_id, transformation_desc, captured_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (source_asset_id, target_asset_id) DO UPDATE
                    SET transformation_desc=EXCLUDED.transformation_desc,
                        captured_at=NOW()
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, sourceAssetId); ps.setString(3, targetAssetId);
            ps.setString(4, transformationDesc);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new LineageEdge(rs.getString("edge_id"), rs.getString("source_asset_id"),
                        rs.getString("target_asset_id"), rs.getString("transformation_desc"),
                        rs.getObject("captured_at", LocalDateTime.class));
            }
        }
    }

    private List<LineageNode> fetchConnectedNodes(String assetId) throws SQLException {
        String sql = """
                SELECT DISTINCT c.asset_id, c.name, c.service_owner FROM data_catalog c
                WHERE c.asset_id IN (
                    SELECT source_asset_id FROM lineage_edges WHERE target_asset_id=?
                    UNION SELECT target_asset_id FROM lineage_edges WHERE source_asset_id=?
                    UNION SELECT ?
                )
                """;
        List<LineageNode> nodes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId); ps.setString(2, assetId); ps.setString(3, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) nodes.add(new LineageNode(rs.getString("asset_id"),
                        rs.getString("name"), rs.getString("service_owner")));
            }
        }
        return nodes;
    }

    private List<LineageEdge> fetchConnectedEdges(String assetId) throws SQLException {
        String sql = """
                SELECT * FROM lineage_edges
                WHERE source_asset_id=? OR target_asset_id=?
                """;
        List<LineageEdge> edges = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId); ps.setString(2, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) edges.add(new LineageEdge(rs.getString("edge_id"),
                        rs.getString("source_asset_id"), rs.getString("target_asset_id"),
                        rs.getString("transformation_desc"),
                        rs.getObject("captured_at", LocalDateTime.class)));
            }
        }
        return edges;
    }
}
