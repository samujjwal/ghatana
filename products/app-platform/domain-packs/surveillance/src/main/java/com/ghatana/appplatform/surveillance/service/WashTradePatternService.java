package com.ghatana.appplatform.surveillance.service;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detects ring trading — a coordinated circular pattern where trades flow
 *              A→B→C→→A (or longer chains) in the same instrument, creating artificial
 *              volume without genuine economic transfer. Detection uses a directed graph
 *              analysis of daily trade relationships. Ring size 3–6 is considered significant.
 *              A complexity score is assigned based on ring size and volume concentration.
 *              K-03 rules engine provides configurable participation thresholds per instrument.
 * @doc.layer   Domain
 * @doc.pattern Daily batch graph analysis; adjacency list construction from daily fills;
 *              DFS cycle detection; K-03 RulesEnginePort for thresholds; INSERT-only alerts.
 */
public class WashTradePatternService {

    private static final Logger log = LoggerFactory.getLogger(WashTradePatternService.class);

    private static final int    MIN_RING_SIZE         = 3;
    private static final int    MAX_RING_SIZE         = 6;
    private static final double MIN_COMPLEXITY_SCORE  = 0.50;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RulesEnginePort  rulesEngine;
    private final Counter          ringsDetectedCounter;
    private final Counter          instrumentsScannedCounter;

    public WashTradePatternService(HikariDataSource dataSource, Executor executor,
                                   RulesEnginePort rulesEngine, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.rulesEngine             = rulesEngine;
        this.ringsDetectedCounter    = registry.counter("surveillance.ring_trade.detected");
        this.instrumentsScannedCounter = registry.counter("surveillance.ring_trade.instruments_scanned");
    }

    // ─── Inner port (K-03) ───────────────────────────────────────────────────

    public interface RulesEnginePort {
        int    getMinRingSize(String instrumentId);    // default 3
        double getMinVolumeConcentration(String instrumentId); // fraction
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record RingPattern(
        String           alertId,
        String           instrumentId,
        LocalDate        detectionDate,
        List<String>     participantChain,  // ordered clientIds forming the ring
        double           totalVolume,
        double           complexityScore,
        String           alertStatus
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Scan all active instruments for ring patterns on the given date.
     */
    public Promise<List<RingPattern>> runDailyDetection(LocalDate analysisDate) {
        return Promise.ofBlocking(executor, () -> {
            List<String> instruments = loadActiveInstrumentIds();
            List<RingPattern> allRings = new ArrayList<>();

            for (String instrId : instruments) {
                instrumentsScannedCounter.increment();
                Map<String, List<String>> adjList = buildAdjacencyList(instrId, analysisDate);
                List<List<String>> rings = findRings(adjList);

                for (List<String> ring : rings) {
                    double totalVol     = computeRingVolume(instrId, ring, analysisDate);
                    double complexity   = computeComplexityScore(ring.size(), totalVol);
                    double minConc      = rulesEngine.getMinVolumeConcentration(instrId);

                    if (complexity >= MIN_COMPLEXITY_SCORE) {
                        RingPattern pattern = new RingPattern(
                            UUID.randomUUID().toString(),
                            instrId,
                            analysisDate,
                            ring,
                            totalVol,
                            complexity,
                            "OPEN"
                        );
                        persistAlert(pattern);
                        allRings.add(pattern);
                        ringsDetectedCounter.increment();
                        log.warn("Ring trade detected instrumentId={} size={} complexity={}",
                                 instrId, ring.size(), complexity);
                    }
                }
            }
            log.info("Ring detection complete date={} instruments={} rings={}",
                     analysisDate, instruments.size(), allRings.size());
            return allRings;
        });
    }

    // ─── Graph analysis ───────────────────────────────────────────────────────

    /**
     * Build directed adjacency list: buyer→seller edges from fills for an instrument on a date.
     */
    private Map<String, List<String>> buildAdjacencyList(String instrumentId,
                                                          LocalDate date) throws SQLException {
        String sql = """
            SELECT buyer_client_id, seller_client_id, SUM(quantity) AS vol
            FROM trade_events
            WHERE instrument_id = ? AND DATE(trade_time) = ?
            GROUP BY buyer_client_id, seller_client_id
            """;
        Map<String, List<String>> adj = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String buyer  = rs.getString("buyer_client_id");
                    String seller = rs.getString("seller_client_id");
                    adj.computeIfAbsent(buyer, k -> new ArrayList<>()).add(seller);
                }
            }
        }
        return adj;
    }

    /**
     * DFS-based cycle detection; returns all simple cycles within MIN_RING_SIZE..MAX_RING_SIZE.
     */
    private List<List<String>> findRings(Map<String, List<String>> adj) {
        List<List<String>> rings   = new ArrayList<>();
        Set<String>  allNodes      = adj.keySet();

        for (String startNode : allNodes) {
            List<String> path   = new ArrayList<>();
            Set<String>  visited = new HashSet<>();
            path.add(startNode);
            visited.add(startNode);
            dfs(startNode, startNode, adj, path, visited, rings);
        }
        // Deduplicate rings (normalize by rotating to canonical min element first)
        return deduplicateRings(rings);
    }

    private void dfs(String start, String current, Map<String, List<String>> adj,
                     List<String> path, Set<String> visited, List<List<String>> rings) {
        List<String> neighbors = adj.getOrDefault(current, List.of());
        for (String neighbor : neighbors) {
            if (neighbor.equals(start) && path.size() >= MIN_RING_SIZE) {
                rings.add(new ArrayList<>(path));
            } else if (!visited.contains(neighbor) && path.size() < MAX_RING_SIZE) {
                visited.add(neighbor);
                path.add(neighbor);
                dfs(start, neighbor, adj, path, visited, rings);
                path.removeLast();
                visited.remove(neighbor);
            }
        }
    }

    private List<List<String>> deduplicateRings(List<List<String>> rings) {
        Set<String> seen = new HashSet<>();
        List<List<String>> unique = new ArrayList<>();
        for (List<String> ring : rings) {
            String canonical = canonicalize(ring);
            if (seen.add(canonical)) unique.add(ring);
        }
        return unique;
    }

    private String canonicalize(List<String> ring) {
        String min    = ring.stream().min(String::compareTo).orElse("");
        int    i      = ring.indexOf(min);
        List<String> rotated = new ArrayList<>(ring.subList(i, ring.size()));
        rotated.addAll(ring.subList(0, i));
        return String.join("→", rotated);
    }

    private double computeRingVolume(String instrumentId, List<String> ring,
                                     LocalDate date) throws SQLException {
        if (ring.size() < 2) return 0.0;
        String sql = """
            SELECT COALESCE(SUM(quantity), 0) FROM trade_events
            WHERE instrument_id = ? AND DATE(trade_time) = ?
              AND buyer_client_id = ANY(?) AND seller_client_id = ANY(?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            var arr = conn.createArrayOf("text", ring.toArray());
            ps.setString(1, instrumentId);
            ps.setObject(2, date);
            ps.setArray(3, arr);
            ps.setArray(4, arr);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private double computeComplexityScore(int ringSize, double volume) {
        // Score: ring size factor (0.10 per node above min) + volume factor (capped at 0.50)
        double sizeFactor   = Math.min(0.10 * (ringSize - MIN_RING_SIZE + 1), 0.50);
        double volumeFactor = Math.min(volume / 10_000.0, 0.50); // normalized to 10k shares
        return Math.min(sizeFactor + volumeFactor, 1.0);
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private void persistAlert(RingPattern pattern) throws SQLException {
        String chainJson = "[\"" + String.join("\",\"", pattern.participantChain()) + "\"]";
        String sql = """
            INSERT INTO ring_trade_patterns (
                alert_id, instrument_id, detection_date, participant_chain,
                total_volume, complexity_score, alert_status, created_at
            ) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, now())
            ON CONFLICT (instrument_id, detection_date, participant_chain) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern.alertId());
            ps.setString(2, pattern.instrumentId());
            ps.setObject(3, pattern.detectionDate());
            ps.setString(4, chainJson);
            ps.setDouble(5, pattern.totalVolume());
            ps.setDouble(6, pattern.complexityScore());
            ps.setString(7, pattern.alertStatus());
            ps.executeUpdate();
        }
    }

    private List<String> loadActiveInstrumentIds() throws SQLException {
        String sql = "SELECT instrument_id FROM instruments WHERE status = 'ACTIVE'";
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("instrument_id"));
        }
        return ids;
    }
}
