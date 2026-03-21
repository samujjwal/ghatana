package com.ghatana.pattern.compiler;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.*;
import com.ghatana.pattern.compiler.ast.AST;
import com.ghatana.pattern.compiler.ast.ASTBuilder;
import com.ghatana.pattern.compiler.dag.DAGBuilder;
import com.ghatana.pattern.compiler.dag.DAGOptimizer;
import com.ghatana.pattern.operator.registry.OperatorRegistry;
import com.ghatana.pattern.operator.spi.ValidationContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main pattern compiler that transforms PatternSpecification into DetectionPlan.
 * 
 * <p>This compiler handles the complete compilation pipeline:
 * <ol>
 *   <li>Validation of pattern specification</li>
 *   <li>AST construction from operator tree</li>
 *   <li>DAG generation and optimization</li>
 *   <li>Detection plan creation</li>
 * </ol>
 * 
 * @doc.type class
 * @doc.purpose Transforms PatternSpecification into optimized DetectionPlan via multi-phase compilation
 * @doc.layer product
 * @doc.pattern Compiler Pattern (multi-phase compilation), Pipeline Pattern (chained transformations)
 * @since 2.0.0
 * @doc.compiler-phase Complete pipeline: Validation → AST Building → DAG Generation → Optimization → Plan Creation
 * @doc.threading Single-threaded compilation per pattern; parallel compilation of multiple patterns supported
 * @doc.performance O(n) for AST building where n=operator count; O(n log n) for DAG optimization
 * @doc.memory O(n) for AST and DAG storage; temporary structures released after compilation
 * @doc.apiNote Compile patterns before deployment; compiled plans are immutable and cacheable
 * @doc.limitation No incremental compilation; full recompile required on pattern changes
 * @doc.sideEffects Emits metrics for compilation time, validation time, and optimization steps
 * 
 * <h2>Compilation Phases</h2>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>Phase</th>
 *     <th>Component</th>
 *     <th>Purpose</th>
 *     <th>Complexity</th>
 *   </tr>
 *   <tr>
 *     <td>1. Validation</td>
 *     <td>ValidationEngine</td>
 *     <td>Verify schema, operators, permissions</td>
 *     <td>O(n)</td>
 *   </tr>
 *   <tr>
 *     <td>2. AST Building</td>
 *     <td>ASTBuilder</td>
 *     <td>Create abstract syntax tree</td>
 *     <td>O(n)</td>
 *   </tr>
 *   <tr>
 *     <td>3. DAG Generation</td>
 *     <td>DAGBuilder</td>
 *     <td>Convert AST to directed acyclic graph</td>
 *     <td>O(n)</td>
 *   </tr>
 *   <tr>
 *     <td>4. Optimization</td>
 *     <td>DAGOptimizer</td>
 *     <td>Prune redundant operators, fuse adjacent</td>
 *     <td>O(n log n)</td>
 *   </tr>
 *   <tr>
 *     <td>5. Plan Creation</td>
 *     <td>PatternCompiler</td>
 *     <td>Package optimized DAG as detection plan</td>
 *     <td>O(1)</td>
 *   </tr>
 * </table>
 * 
 * <p><b>Design Reference:</b>
 * This compiler implements the Pattern Compilation pipeline from WORLD_CLASS_DESIGN_MASTER.md.
 * See .github/copilot-instructions.md "Unified Operator Model" for operator integration.
 */
public class PatternCompiler {
    
    private final OperatorRegistry operatorRegistry;
    private final ValidationEngine validationEngine;
    private final ASTBuilder astBuilder;
    private final DAGBuilder dagBuilder;
    private final DAGOptimizer dagOptimizer;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Timer compilationTimer;
    private final Timer validationTimer;
    private final Timer astBuildTimer;
    private final Timer dagBuildTimer;
    private final Timer optimizationTimer;
    
    public PatternCompiler(OperatorRegistry operatorRegistry, MeterRegistry meterRegistry) {
        this.operatorRegistry = operatorRegistry;
        this.meterRegistry = meterRegistry;
        this.validationEngine = new ValidationEngine(operatorRegistry, meterRegistry);
        this.astBuilder = new ASTBuilder(operatorRegistry, meterRegistry);
        this.dagBuilder = new DAGBuilder(operatorRegistry, meterRegistry);
        this.dagOptimizer = new DAGOptimizer(operatorRegistry, meterRegistry);
        
        // Initialize metrics
        this.compilationTimer = Timer.builder("pattern.compiler.compilation.time")
                .description("Time taken to compile a pattern")
                .register(meterRegistry);
        
        this.validationTimer = Timer.builder("pattern.compiler.validation.time")
                .description("Time taken to validate a pattern")
                .register(meterRegistry);
        
        this.astBuildTimer = Timer.builder("pattern.compiler.ast.build.time")
                .description("Time taken to build AST")
                .register(meterRegistry);
        
        this.dagBuildTimer = Timer.builder("pattern.compiler.dag.build.time")
                .description("Time taken to build DAG")
                .register(meterRegistry);
        
        this.optimizationTimer = Timer.builder("pattern.compiler.optimization.time")
                .description("Time taken to optimize DAG")
                .register(meterRegistry);
    }
    
    /**
     * Compile a pattern specification into a detection plan.
     *
     * @param spec the pattern specification to compile
     * @return the compiled detection plan
     * @throws PatternValidationException if compilation fails
     */
    public DetectionPlan compile(PatternSpecification spec) throws PatternValidationException {
        if (spec == null) {
            throw new PatternValidationException("PatternSpecification cannot be null");
        }

        try {
            return compilationTimer.recordCallable(() -> {
                try {
                    // Step 1: Validate pattern specification (including eventTypes)
                    validationEngine.validate(spec);

                    // Step 2: Build AST from operator tree with PRIMARY_EVENT injection
                    AST ast;
                    if (spec.getEventTypes() != null && !spec.getEventTypes().isEmpty()) {
                        // Inject PRIMARY_EVENT operator as root
                        ast = astBuilder.buildWithPrimaryEvent(spec.getOperator(), spec.getEventTypes());
                    } else {
                        // Fallback for patterns without eventTypes (backward compatibility)
                        ast = astBuilder.build(spec.getOperator());
                    }

                    // Step 3: Generate DAG from AST
                    OperatorDAG dag = dagBuilder.build(ast);

                    // Step 4: Optimize DAG
                    dagOptimizer.optimize(dag);

                    // Step 5: Create detection plan
                    return createDetectionPlan(spec, dag);

                } catch (Exception e) {
                    if (e instanceof PatternValidationException) {
                        throw e;
                    }
                    throw new PatternValidationException("Pattern compilation failed", e);
                }
            });
        } catch (Exception e) {
            if (e instanceof PatternValidationException) {
                throw (PatternValidationException) e;
            }
            throw new PatternValidationException("Pattern compilation failed", e);
        }
    }
    
    /**
     * Validate a pattern specification without compilation.
     * 
     * @param spec the pattern specification to validate
     * @throws PatternValidationException if validation fails
     */
    public void validate(PatternSpecification spec) throws PatternValidationException {
        if (spec == null) {
            throw new PatternValidationException("PatternSpecification cannot be null");
        }
        
        try {
            validationTimer.recordCallable(() -> {
                validationEngine.validate(spec);
                return null;
            });
        } catch (Exception e) {
            if (e instanceof PatternValidationException) {
                throw (PatternValidationException) e;
            }
            throw new PatternValidationException("Validation failed", e);
        }
    }
    
    private DetectionPlan createDetectionPlan(PatternSpecification spec, OperatorDAG dag) {
        // Generate state keys for stateful operators
        Map<String, String> stateKeys = dagBuilder.generateStateKeys(spec.getId(), dag);

        // Extract required streams from event types
        List<String> requiredStreams = spec.getEventTypes() != null
            ? dagBuilder.extractRequiredStreams(spec.getEventTypes())
            : List.of();

        return DetectionPlan.builder()
                .patternId(spec.getId())
                .operatorGraph(dag)
                .eventTypes(spec.getEventTypes())  // Copy event types
                .runtimeConfig(createRuntimeConfig(spec, dag))
                .requiredStreams(requiredStreams)
                .window(spec.getWindow())
                .stateKeys(stateKeys)  // Add state keys
                .compiledAt(Instant.now())
                .version("1.0")
                .metadata(createCompilationMetadata(spec, dag))
                .build();
    }

    private Map<String, Object> createRuntimeConfig(PatternSpecification spec, OperatorDAG dag) {
        // Calculate buffer size based on DAG complexity
        int nodeCount = dag.getNodes() != null ? dag.getNodes().size() : 1;
        int estimatedComplexity = nodeCount * 2;
        int bufferSize = Math.max(1000, estimatedComplexity * 100);

        Map<String, Object> config = new java.util.HashMap<>();
        config.put("tenantId", spec.getTenantId());
        config.put("priority", spec.getPriority());
        config.put("selection", spec.getSelection().getValue());
        config.put("selectionMode", spec.getSelection().name());
        config.put("activation", spec.isActivation());

        // Buffer and performance tuning
        config.put("bufferSize", bufferSize);
        config.put("watermarkInterval", 5000L);
        config.put("checkpointInterval", 60000L);

        // Event type filter
        if (spec.getEventTypes() != null && !spec.getEventTypes().isEmpty()) {
            config.put("eventTypeFilter", spec.getEventTypes());
        }

        // Late arrival handling (from window spec)
        if (spec.getWindow() != null && spec.getWindow().getAllowedLateness() != null) {
            config.put("allowedLateness", spec.getWindow().getAllowedLateness().toMillis());
        }

        config.put("compiledAt", Instant.now().toString());

        return config;
    }
    
    private Map<String, Object> createCompilationMetadata(PatternSpecification spec, OperatorDAG dag) {
        return Map.of(
            "compilerVersion", "1.0",
            "operatorCount", dag.getNodes() != null ? dag.getNodes().size() : 0,
            "edgeCount", dag.getEdges() != null ? dag.getEdges().size() : 0,
            "compilationTime", Instant.now().toString()
        );
    }
    
    /**
     * Get the operator registry used by this compiler.
     * 
     * @return the operator registry
     */
    public OperatorRegistry getOperatorRegistry() {
        return operatorRegistry;
    }
    
    /**
     * Get the validation engine used by this compiler.
     * 
     * @return the validation engine
     */
    public ValidationEngine getValidationEngine() {
        return validationEngine;
    }
    
    /**
     * Get the AST builder used by this compiler.
     * 
     * @return the AST builder
     */
    public ASTBuilder getAstBuilder() {
        return astBuilder;
    }
    
    /**
     * Get the DAG builder used by this compiler.
     * 
     * @return the DAG builder
     */
    public DAGBuilder getDagBuilder() {
        return dagBuilder;
    }
    
    /**
     * Get the DAG optimizer used by this compiler.
     * 
     * @return the DAG optimizer
     */
    public DAGOptimizer getDagOptimizer() {
        return dagOptimizer;
    }
}





