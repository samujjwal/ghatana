package com.ghatana.pattern.compiler.ast;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.operator.registry.OperatorRegistry;

import io.micrometer.core.instrument.MeterRegistry;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for constructing Abstract Syntax Trees (AST) from operator specifications.
 * 
 * <p>The ASTBuilder transforms a flat operator specification tree into a hierarchical
 * Abstract Syntax Tree that captures the semantic structure of the pattern. This
 * intermediate representation enables easier analysis, transformation, and optimization.
 * 
 * @doc.pattern Builder Pattern (AST construction), Visitor Pattern (tree traversal)
 * @doc.compiler-phase AST Building (second phase after validation)
 * @doc.threading Thread-safe; each build() call operates on independent data structures
 * @doc.performance O(n) construction where n=operator count; recursive tree building
 * @doc.memory O(n) for AST storage; each node contains operator metadata
 * @doc.apiNote Build AST after successful validation; AST is immutable once constructed
 * @doc.limitation No AST rewriting or transformation; use DAGOptimizer for graph transformations
 * @doc.sideEffects Emits metrics for AST build success/failure
 * 
 * <h2>AST Structure</h2>
 * <pre>
 * AST
 *  ├── Root Node (top-level operator)
 *  │   ├── Children (operator inputs)
 *  │   │   ├── Child 1
 *  │   │   └── Child 2
 *  │   └── Metadata (operator config, depth, type)
 *  └── Metadata (pattern-level info)
 * </pre>
 * 
 * <p><b>Node Types:</b>
 * <ul>
 *   <li><b>Leaf Nodes</b>: Event selectors (no children)</li>
 *   <li><b>Unary Nodes</b>: Filters, transformations (1 child)</li>
 *   <li><b>Binary Nodes</b>: Joins, correlations (2 children)</li>
 *   <li><b>N-ary Nodes</b>: Aggregations, sequences (n children)</li>
 * </ul>
 */
public class ASTBuilder {
    
    private final OperatorRegistry operatorRegistry;
    private final MetricsCollector metrics;
    
    // Metrics
    
    
    private final Timer astBuildTimer;
    
    public ASTBuilder(OperatorRegistry operatorRegistry, MeterRegistry meterRegistry) {
        this.operatorRegistry = operatorRegistry;
        this.metrics = MetricsCollectorFactory.create(meterRegistry);
        
        // Initialize metrics
        // Counters migrated to MetricsCollector
        
        // See metrics field for counter operations
        
        this.astBuildTimer = Timer.builder("pattern.compiler.ast.build.time").register(meterRegistry);
    }
    
    /**
     * Build an AST from an operator specification.
     *
     * @param operatorSpec the root operator specification
     * @return the constructed AST
     * @throws PatternValidationException if AST construction fails
     */
    public AST build(OperatorSpec operatorSpec) throws PatternValidationException {
        if (operatorSpec == null) {
            throw new PatternValidationException("OperatorSpec cannot be null");
        }

        try {
            return astBuildTimer.recordCallable(() -> {
                try {
                    ASTNode root = buildNode(operatorSpec, 0);
                    AST ast = AST.builder()
                            .root(root)
                            .metadata(createASTMetadata(operatorSpec))
                            .build();

                    metrics.incrementCounter("pattern.compiler.ast.build.success");
                    return ast;

                } catch (Exception e) {
                    metrics.incrementCounter("pattern.compiler.ast.build.failure");
                    if (e instanceof PatternValidationException) {
                        throw e;
                    }
                    throw new PatternValidationException("AST construction failed", e);
                }
            });
        } catch (Exception e) {
            if (e instanceof PatternValidationException) {
                throw (PatternValidationException) e;
            }
            throw new PatternValidationException("AST construction failed", e);
        }
    }

    /**
     * Build an AST with PRIMARY_EVENT operator injected as root.
     *
     * <p>This method wraps the original operator tree with a PRIMARY_EVENT operator
     * that filters events by the declared event types.
     *
     * @param operatorSpec the original root operator specification
     * @param eventTypes the list of event types to filter by
     * @return the constructed AST with PRIMARY_EVENT as root
     * @throws PatternValidationException if AST construction fails
     */
    public AST buildWithPrimaryEvent(OperatorSpec operatorSpec, List<String> eventTypes)
            throws PatternValidationException {
        if (operatorSpec == null) {
            throw new PatternValidationException("OperatorSpec cannot be null");
        }

        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new PatternValidationException("Event types cannot be null or empty");
        }

        try {
            return astBuildTimer.recordCallable(() -> {
                try {
                    // Create PRIMARY_EVENT operator wrapping the original tree
                    OperatorSpec primaryEventSpec = OperatorSpec.builder()
                            .type("PRIMARY_EVENT")
                            .id("primary-event-filter")
                            .parameter("eventTypes", new ArrayList<>(eventTypes))
                            .operand(operatorSpec) // Original tree becomes operand
                            .build();

                    // Build AST with PRIMARY_EVENT as root
                    ASTNode root = buildNode(primaryEventSpec, 0);
                    AST ast = AST.builder()
                            .root(root)
                            .metadata(createASTMetadataWithPrimaryEvent(operatorSpec, eventTypes))
                            .build();

                    metrics.incrementCounter("pattern.compiler.ast.build.success");
                    return ast;

                } catch (Exception e) {
                    metrics.incrementCounter("pattern.compiler.ast.build.failure");
                    if (e instanceof PatternValidationException) {
                        throw e;
                    }
                    throw new PatternValidationException("AST construction with PRIMARY_EVENT failed", e);
                }
            });
        } catch (Exception e) {
            if (e instanceof PatternValidationException) {
                throw (PatternValidationException) e;
            }
            throw new PatternValidationException("AST construction with PRIMARY_EVENT failed", e);
        }
    }
    
    private ASTNode buildNode(OperatorSpec operatorSpec, int depth) throws PatternValidationException {
        if (operatorSpec == null) {
            throw new PatternValidationException("OperatorSpec cannot be null");
        }
        
        String type = operatorSpec.getType();
        if (type == null || type.trim().isEmpty()) {
            throw new PatternValidationException("Operator type cannot be null or empty");
        }
        
        // Generate unique ID if not provided
        String id = operatorSpec.getId();
        if (id == null || id.trim().isEmpty()) {
            id = generateNodeId(type, depth);
        }
        
        // Build children nodes
        List<ASTNode> children = new ArrayList<>();
        if (operatorSpec.getOperands() != null) {
            for (OperatorSpec operand : operatorSpec.getOperands()) {
                if (operand == null) {
                    throw new PatternValidationException("Operator operand cannot be null");
                }
                
                ASTNode childNode = buildNode(operand, depth + 1);
                children.add(childNode);
            }
        }
        
        // Create metadata for the node
        Map<String, Object> metadata = createNodeMetadata(operatorSpec, depth);
        
        return ASTNode.builder()
                .type(type)
                .id(id)
                .operatorSpec(operatorSpec)
                .children(children)
                .metadata(metadata)
                .depth(depth)
                .build();
    }
    
    private String generateNodeId(String type, int depth) {
        return String.format("%s_%d_%s", type, depth, UUID.randomUUID().toString().substring(0, 8));
    }
    
    private Map<String, Object> createNodeMetadata(OperatorSpec operatorSpec, int depth) {
        return Map.of(
            "depth", depth,
            "operandCount", operatorSpec.getOperandCount(),
            "hasParameters", operatorSpec.getParameters() != null && !operatorSpec.getParameters().isEmpty(),
            "hasMetadata", operatorSpec.getMetadata() != null && !operatorSpec.getMetadata().isEmpty()
        );
    }
    
    private Map<String, Object> createASTMetadata(OperatorSpec operatorSpec) {
        return Map.of(
            "rootType", operatorSpec.getType(),
            "rootId", operatorSpec.getId(),
            "operandCount", operatorSpec.getOperandCount(),
            "buildTime", System.currentTimeMillis()
        );
    }

    private Map<String, Object> createASTMetadataWithPrimaryEvent(OperatorSpec operatorSpec, List<String> eventTypes) {
        return Map.of(
            "rootType", "PRIMARY_EVENT",
            "rootId", "primary-event-filter",
            "originalRootType", operatorSpec.getType(),
            "originalRootId", operatorSpec.getId() != null ? operatorSpec.getId() : "unknown",
            "eventTypes", eventTypes,
            "eventTypeCount", eventTypes.size(),
            "operandCount", 1,
            "buildTime", System.currentTimeMillis()
        );
    }
}





