package com.ghatana.appplatform.governance;

import com.ghatana.appplatform.governance.port.DataLineageStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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

    private final DataLineageStore lineageStore;
    private final Executor         executor;
    private final Counter          edgesAddedCounter;

    public DataLineageService(DataLineageStore lineageStore, Executor executor, MeterRegistry registry) {
        this.lineageStore      = lineageStore;
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
            LineageEdge edge = lineageStore.insertEdge(UUID.randomUUID().toString(),
                    sourceAssetId, targetAssetId, transformationDesc);
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
            List<LineageNode> nodes = lineageStore.fetchConnectedNodes(assetId);
            List<LineageEdge> edges = lineageStore.fetchConnectedEdges(assetId);
            return new LineageGraph(nodes, edges);
        });
    }

    // ─── Cycle detection (DFS) ────────────────────────────────────────────────

    private boolean wouldCreateCycle(String source, String target) throws Exception {
        // If target can reach source, adding source→target creates a cycle
        return bfsDownstream(target).stream().anyMatch(n -> n.assetId().equals(source));
    }

    private List<LineageNode> bfsDownstream(String startAssetId) throws Exception {
        List<LineageNode> frontier = new ArrayList<>();
        List<String> visited = new ArrayList<>();
        visited.add(startAssetId);
        frontier.addAll(lineageStore.fetchDirectDownstream(startAssetId));
        int i = 0;
        while (i < frontier.size()) {
            LineageNode node = frontier.get(i++);
            if (!visited.contains(node.assetId())) {
                visited.add(node.assetId());
                frontier.addAll(lineageStore.fetchDirectDownstream(node.assetId()));
            }
        }
        return frontier;
    }
}
                        rs.getString("source_asset_id"), rs.getString("target_asset_id"),
                        rs.getString("transformation_desc"),
                        rs.getObject("captured_at", LocalDateTime.class)));
            }
        }
        return edges;
    }
}
