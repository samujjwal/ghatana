package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Graph Neural Network (GNN) scoring to detect indirect PEP and sanctions
 *                exposure through ownership / beneficial interest networks. Edges represent
 *                ownership, directorship, or known association. High indirect risk scores
 *                trigger enhanced due diligence.
 * @doc.layer     Application
 * @doc.pattern   K-09 advisory GNN; graph traversal + model scoring; SHAP node contributions
 *
 * Story: D14-016
 */
public class GraphNetworkRiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(GraphNetworkRiskScoringService.class);

    /** Indirect risk score threshold that triggers enhanced screening. */
    private static final double HIGH_RISK_THRESHOLD = 0.70;
    /** Maximum graph depth to traverse for neighbour extraction. */
    private static final int MAX_DEPTH = 3;

    private final GraphPort         graphPort;
    private final GnnModelPort      gnnModelPort;
    private final Consumer<Object>  eventPublisher;
    private final Counter           highRiskEntitiesDetected;
    private final Timer             scoringTimer;

    public GraphNetworkRiskScoringService(GraphPort graphPort,
                                           GnnModelPort gnnModelPort,
                                           Consumer<Object> eventPublisher,
                                           MeterRegistry meterRegistry) {
        this.graphPort               = graphPort;
        this.gnnModelPort            = gnnModelPort;
        this.eventPublisher          = eventPublisher;
        this.highRiskEntitiesDetected = meterRegistry.counter("sanctions.graph.high_risk_detected");
        this.scoringTimer            = meterRegistry.timer("sanctions.graph.scoring_latency");
    }

    /**
     * Computes indirect PEP/sanctions network risk for an entity.
     *
     * @param entityId  client or company entity identifier
     * @return risk score with SHAP-style node contributions and flagged neighbours
     */
    public NetworkRiskScore score(String entityId) {
        return scoringTimer.record(() -> {
            EntityGraph graph = graphPort.extractSubgraph(entityId, MAX_DEPTH);
            GnnResult result  = gnnModelPort.score(entityId, graph.nodes(), graph.edges());

            boolean isHighRisk = result.riskScore() >= HIGH_RISK_THRESHOLD;
            log.debug("GraphRisk entityId={} score={} highRisk={}", entityId, result.riskScore(), isHighRisk);

            if (isHighRisk) {
                highRiskEntitiesDetected.increment();
                log.warn("GraphRisk HIGH: entityId={} score={} flaggedNeighbours={}",
                        entityId, result.riskScore(), result.flaggedNeighbours());
                eventPublisher.accept(new HighNetworkRiskEvent(entityId, result.riskScore(),
                        result.flaggedNeighbours(), result.nodeContributions()));
            }

            return new NetworkRiskScore(entityId, result.riskScore(), result.flaggedNeighbours(),
                    result.nodeContributions(), isHighRisk, Instant.now());
        });
    }

    /**
     * Adds or updates an edge in the entity relationship graph.
     * Called by entity resolution and KYC update pipelines.
     *
     * @param fromEntityId  source node
     * @param toEntityId    target node
     * @param edgeType      e.g. OWNS, DIRECTOR_OF, ASSOCIATED_WITH
     * @param weight        edge weight 0–1 (ownership percentage or association strength)
     */
    public void upsertEdge(String fromEntityId, String toEntityId, String edgeType, double weight) {
        graphPort.upsertEdge(fromEntityId, toEntityId, edgeType, weight);
        log.debug("GraphEdge upserted from={} to={} type={} weight={}", fromEntityId, toEntityId, edgeType, weight);
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface GraphPort {
        EntityGraph extractSubgraph(String rootEntityId, int maxDepth);
        void upsertEdge(String from, String to, String edgeType, double weight);
    }

    public interface GnnModelPort {
        GnnResult score(String rootEntityId, List<GraphNode> nodes, List<GraphEdge> edges);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record GraphNode(String entityId, String entityType, double directRiskScore) {}
    public record GraphEdge(String fromEntityId, String toEntityId, String edgeType, double weight) {}
    public record EntityGraph(String rootEntityId, List<GraphNode> nodes, List<GraphEdge> edges) {}

    public record GnnResult(double riskScore, List<String> flaggedNeighbours,
                             Map<String, Double> nodeContributions) {}

    public record NetworkRiskScore(String entityId, double riskScore,
                                    List<String> flaggedNeighbours,
                                    Map<String, Double> nodeContributions,
                                    boolean isHighRisk, Instant scoredAt) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record HighNetworkRiskEvent(String entityId, double riskScore,
                                       List<String> flaggedNeighbours,
                                       Map<String, Double> nodeContributions) {}
}
