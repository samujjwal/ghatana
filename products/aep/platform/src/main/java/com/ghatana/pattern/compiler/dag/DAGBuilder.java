package com.ghatana.pattern.compiler.dag;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorDAG;
import com.ghatana.pattern.compiler.ast.AST;
import com.ghatana.pattern.compiler.ast.ASTNode;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import com.ghatana.pattern.operator.registry.OperatorRegistry;

import io.micrometer.core.instrument.MeterRegistry;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Builder for constructing Directed Acyclic Graphs (DAG) from ASTs.
 * 
 * <p>The DAGBuilder converts the hierarchical AST representation into a graph-based
 * DAG that represents operator dependencies and data flow. The DAG enables parallel
 * execution planning and graph-based optimizations.
 * 
 * @doc.pattern Builder Pattern (DAG construction), Graph Pattern (operator dependencies)
 * @doc.compiler-phase DAG Generation (third phase after AST building)
 * @doc.threading Thread-safe; each build() call creates independent DAG structures
 * @doc.performance O(n) DAG construction where n=operator count; topological ordering
 * @doc.memory O(n + e) where n=nodes, e=edges; adjacency list representation
 * @doc.apiNote Build DAG from validated AST; DAG is optimized by DAGOptimizer before execution
 * @doc.limitation No cycle detection (AST structure guarantees acyclic); no dynamic edge addition
 * @doc.sideEffects Emits metrics for DAG build success/failure and node/edge counts
 * 
 * <h2>AST to DAG Conversion</h2>
 * <pre>
 * AST Tree Structure          DAG Graph Structure
 * ─────────────────           ───────────────────
 *       AND                   ┌─── Filter1 ───┐
 *      /   \                  │                │
 *  Filter1 Filter2     →      │    ┌─── AND ──┤
 *    |       |                │    │          │
 *   Evt1    Evt2              └─→ Evt1   Filter2 ──┘
 *                                           │
 *                                          Evt2
 * </pre>
 * 
 * <p><b>DAG Properties:</b>
 * <ul>
 *   <li><b>Nodes</b>: Operators with unique IDs and metadata</li>
 *   <li><b>Edges</b>: Data flow dependencies (source → target)</li>
 *   <li><b>Root</b>: Final operator (sink) with no outgoing edges</li>
 *   <li><b>Leaves</b>: Event selectors (sources) with no incoming edges</li>
 * </ul>
 */
public class DAGBuilder {
    
    private final OperatorRegistry operatorRegistry;
    private final MetricsCollector metrics;
    
    // Metrics
    
    
    private final Timer dagBuildTimer;
    
    public DAGBuilder(OperatorRegistry operatorRegistry, MeterRegistry meterRegistry) {
        this.operatorRegistry = operatorRegistry;
        this.metrics = MetricsCollectorFactory.create(meterRegistry);
        
        // Initialize metrics
        // Counters migrated to MetricsCollector
        
        // See metrics field for counter operations
        
        this.dagBuildTimer = Timer.builder("pattern.compiler.dag.build.time").register(meterRegistry);
    }
    
    /**
     * Build a DAG from an AST.
     * 
     * @param ast the AST to convert to DAG
     * @return the constructed DAG
     * @throws PatternValidationException if DAG construction fails
     */
    public OperatorDAG build(AST ast) throws PatternValidationException {
        if (ast == null) {
            throw new PatternValidationException("AST cannot be null");
        }
        
        try {
            return dagBuildTimer.recordCallable(() -> {
                try {
                    List<OperatorDAG.OperatorNode> nodes = new ArrayList<>();
                    List<OperatorDAG.OperatorEdge> edges = new ArrayList<>();
                    
                    // Build nodes and edges from AST
                    buildNodesAndEdges(ast.getRoot(), nodes, edges);
                    
                    // Find root node
                    String rootNodeId = findRootNodeId(nodes, edges);
                    
                    // Create DAG
                    OperatorDAG dag = OperatorDAG.builder()
                            .nodes(nodes)
                            .edges(edges)
                            .rootNodeId(rootNodeId)
                        .metadata(createDAGMetadata(ast, nodes, edges))
                        .build();
                
                metrics.incrementCounter("pattern.compiler.dag.build.success");
                return dag;
                
            } catch (Exception e) {
                metrics.incrementCounter("pattern.compiler.dag.build.failure");
                if (e instanceof PatternValidationException) {
                    throw e;
                }
                throw new PatternValidationException("DAG construction failed", e);
            }
            });
        } catch (Exception e) {
            if (e instanceof PatternValidationException) {
                throw (PatternValidationException) e;
            }
            throw new PatternValidationException("DAG construction failed", e);
        }
    }
    
    private void buildNodesAndEdges(ASTNode astNode, List<OperatorDAG.OperatorNode> nodes, List<OperatorDAG.OperatorEdge> edges) {
        if (astNode == null) {
            return;
        }
        
        // Create DAG node from AST node
        OperatorDAG.OperatorNode dagNode = OperatorDAG.OperatorNode.builder()
                .id(astNode.getId())
                .type(astNode.getType())
                .operatorSpec(astNode.getOperatorSpec())
                .metadata(astNode.getMetadata())
                .build();
        
        nodes.add(dagNode);
        
        // Process children and create edges
        for (ASTNode child : astNode.getChildren()) {
            if (child != null) {
                // Recursively build child nodes
                buildNodesAndEdges(child, nodes, edges);
                
                // Create edge from parent to child
                OperatorDAG.OperatorEdge edge = OperatorDAG.OperatorEdge.builder()
                        .fromNodeId(astNode.getId())
                        .toNodeId(child.getId())
                        .edgeType(OperatorDAG.EdgeType.DATA_FLOW)
                        .metadata(Map.of("astDepth", astNode.getDepth()))
                        .build();
                
                edges.add(edge);
            }
        }
    }
    
    private String findRootNodeId(List<OperatorDAG.OperatorNode> nodes, List<OperatorDAG.OperatorEdge> edges) {
        if (nodes.isEmpty()) {
            return null;
        }
        
        // Find nodes that are not targets of any edge (root nodes)
        Map<String, Boolean> isTarget = new HashMap<>();
        
        for (OperatorDAG.OperatorEdge edge : edges) {
            isTarget.put(edge.getToNodeId(), true);
        }
        
        for (OperatorDAG.OperatorNode node : nodes) {
            if (!isTarget.getOrDefault(node.getId(), false)) {
                return node.getId();
            }
        }
        
        // If no root found, return the first node
        return nodes.get(0).getId();
    }
    
    private Map<String, Object> createDAGMetadata(AST ast, List<OperatorDAG.OperatorNode> nodes, List<OperatorDAG.OperatorEdge> edges) {
        return Map.of(
            "nodeCount", nodes.size(),
            "edgeCount", edges.size(),
            "astDepth", ast.getDepth(),
            "astNodeCount", ast.getNodeCount(),
            "buildTime", System.currentTimeMillis()
        );
    }

    /**
     * Generate state keys for stateful operators in the DAG.
     *
     * <p>State keys follow the format: "pattern:{patternId}:operator:{nodeId}"
     * Only stateful operators get state keys.
     *
     * @param patternId the pattern identifier
     * @param dag the operator DAG
     * @return map of node IDs to state keys
     */
    public Map<String, String> generateStateKeys(UUID patternId, OperatorDAG dag) {
        if (patternId == null || dag == null) {
            return Collections.emptyMap();
        }

        Map<String, String> stateKeys = new HashMap<>();

        for (OperatorDAG.OperatorNode node : dag.getNodes()) {
            if (isStatefulOperator(node.getType())) {
                String stateKey = String.format("pattern:%s:operator:%s",
                    patternId.toString(),
                    node.getId());
                stateKeys.put(node.getId(), stateKey);
            }
        }

        return stateKeys;
    }

    /**
     * Check if an operator type is stateful.
     *
     * @param operatorType the operator type
     * @return true if the operator is stateful
     */
    private boolean isStatefulOperator(String operatorType) {
        if (operatorType == null || operatorRegistry == null) {
            return false;
        }

        OperatorMetadata metadata = operatorRegistry.getMetadata(operatorType);
        if (metadata == null) {
            return false;
        }

        return metadata.isSupportsStateful();
    }

    /**
     * Extract required stream names from event types.
     *
     * <p>Converts event type identifiers to Kafka topic names using the convention:
     * "namespace.EventName" -> "namespace-event-name"
     *
     * <p>Example: "com.ghatana.financial.TransactionEvent" -> "financial-transaction-event"
     *
     * @param eventTypes the list of event type identifiers
     * @return list of stream/topic names
     */
    public List<String> extractRequiredStreams(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return Collections.emptyList();
        }

        return eventTypes.stream()
            .map(this::eventTypeToStreamName)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Convert an event type identifier to a stream/topic name.
     *
     * <p>Convention:
     * <ul>
     *   <li>Take the second-to-last part as namespace (e.g., "financial" from "com.ghatana.financial.TransactionEvent")</li>
     *   <li>Convert last part from PascalCase to kebab-case (e.g., "TransactionEvent" -> "transaction-event")</li>
     *   <li>Combine: "{namespace}-{event-name}"</li>
     * </ul>
     *
     * @param eventType the event type identifier
     * @return the stream/topic name
     */
    private String eventTypeToStreamName(String eventType) {
        if (eventType == null || !eventType.contains(".")) {
            // Fallback for invalid format
            return eventType != null ? eventType.toLowerCase() : "unknown";
        }

        String[] parts = eventType.split("\\.");
        if (parts.length < 2) {
            return eventType.toLowerCase();
        }

        // Get namespace (second-to-last part)
        String namespace = parts[parts.length - 2];

        // Get event name (last part)
        String eventName = parts[parts.length - 1];

        // Convert event name to kebab-case
        String kebabCaseName = camelToKebab(eventName);

        // Combine: namespace-event-name
        return namespace.toLowerCase() + "-" + kebabCaseName;
    }

    /**
     * Convert camelCase or PascalCase string to kebab-case.
     *
     * <p>Examples:
     * <ul>
     *   <li>"TransactionEvent" -> "transaction-event"</li>
     *   <li>"AccountCreated" -> "account-created"</li>
     *   <li>"UserLoginEvent" -> "user-login-event"</li>
     * </ul>
     *
     * @param input the camelCase or PascalCase string
     * @return the kebab-case string
     */
    private String camelToKebab(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(input.charAt(0)));

        for (int i = 1; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('-');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}





