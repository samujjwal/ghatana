package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.*;
import com.ghatana.datacloud.entity.PipelineValidationResult.DAGValidationResult;
import com.ghatana.datacloud.entity.PipelineValidationResult.OperatorConfigError;
import com.ghatana.datacloud.entity.PipelineValidationResult.OperatorValidationResult;
import com.ghatana.datacloud.entity.PipelineValidationResult.PerformanceValidationResult;
import com.ghatana.datacloud.entity.PipelineValidationResult.PerformanceViolation;
import com.ghatana.datacloud.entity.PipelineValidationResult.SchemaMismatch;
import com.ghatana.datacloud.entity.PipelineValidationResult.SchemaValidationResult;
import com.ghatana.datacloud.entity.PipelineValidationResult.SecurityValidationResult;
import com.ghatana.datacloud.entity.PipelineValidationResult.SecurityViolation;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates typed DAG pipelines for structural correctness, schema compatibility, and operational constraints.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive validation for typed pipeline definitions including DAG structure validation,
 * schema compatibility checks, operator validation, and performance/security constraints. Ensures
 * pipelines are valid before execution and provides actionable error messages.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineValidator validator = new PipelineValidator();
 * Promise<PipelineValidationResult> promise = validator.validate(pipeline);
 * 
 * promise.then(result -> {
 *     if (result.getValid()) {
 *         // Pipeline is valid, can proceed with execution
 *     } else {
 *         // Handle validation errors
 *         result.getErrors().forEach(error -> log.error(error.message()));
 *     }
 * });
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Validates pipeline domain models
 * - Provides detailed validation feedback
 * - Supports multiple validation categories
 * - Used by PipelineService for pre-execution validation
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All validation logic is pure function.
 *
 * @see PipelineDefinition
 * @see PipelineValidationResult
 * @doc.type class
 * @doc.purpose Validator for typed DAG pipelines
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class PipelineValidator {

    private static final Logger log = LoggerFactory.getLogger(PipelineValidator.class);

    /**
     * Validates a complete pipeline definition.
     *
     * @param pipeline the pipeline to validate (required)
     * @return Promise containing comprehensive validation results
     */
    public Promise<PipelineValidationResult> validate(PipelineDefinition pipeline) {
        if (pipeline == null) {
            return Promise.of(createErrorResult("Pipeline cannot be null"));
        }

        try {
            PipelineValidationResult.Builder resultBuilder = PipelineValidationResult.builder()
                .validationTimestamp(Instant.now());

            // Perform all validation categories
            DAGValidationResult dagResult = validateDAGStructure(pipeline);
            resultBuilder.dagValidation(dagResult);

            SchemaValidationResult schemaResult = validateSchemaCompatibility(pipeline);
            resultBuilder.schemaValidation(schemaResult);

            OperatorValidationResult operatorResult = validateOperators(pipeline);
            resultBuilder.operatorValidation(operatorResult);

            PerformanceValidationResult performanceResult = validatePerformanceConstraints(pipeline);
            resultBuilder.performanceValidation(performanceResult);

            SecurityValidationResult securityResult = validateSecurityConstraints(pipeline);
            resultBuilder.securityValidation(securityResult);

            // Collect errors and warnings
            List<PipelineValidationResult.ValidationError> errors = new ArrayList<>();
            List<PipelineValidationResult.ValidationWarning> warnings = new ArrayList<>();
            List<PipelineValidationResult.ValidationRecommendation> recommendations = new ArrayList<>();

            collectValidationErrors(dagResult, schemaResult, operatorResult, 
                                  performanceResult, securityResult, errors, warnings, recommendations);

            resultBuilder.errors(errors).warnings(warnings).recommendations(recommendations);

            // Determine overall validity
            boolean isValid = errors.isEmpty() && dagResult.hasCycles() == false && 
                            schemaResult.schemasCompatible() && operatorResult.operatorsValid() &&
                            performanceResult.performanceConstraintsMet() && securityResult.securityConstraintsMet();

            resultBuilder.valid(isValid);

            // Add validation metrics
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("validationTimeMs", System.currentTimeMillis());
            metrics.put("nodeCount", pipeline.getNodes().size());
            metrics.put("edgeCount", pipeline.getEdges().size());
            metrics.put("errorCount", errors.size());
            metrics.put("warningCount", warnings.size());
            resultBuilder.validationMetrics(metrics);

            PipelineValidationResult result = resultBuilder.build();
            
            log.debug("Pipeline validation completed: pipelineId={}, valid={}, errors={}, warnings={}",
                pipeline.getId(), result.getValid(), errors.size(), warnings.size());

            return Promise.of(result);

        } catch (Exception e) {
            log.error("Unexpected error during pipeline validation", e);
            return Promise.of(createErrorResult("Validation failed: " + e.getMessage()));
        }
    }

    /**
     * Validates DAG structure for cycles, connectivity, and orphaned nodes.
     */
    private DAGValidationResult validateDAGStructure(PipelineDefinition pipeline) {
        List<String> nodeIds = pipeline.getNodes().stream()
            .map(PipelineNode::getId)
            .toList();

        Set<String> nodeSet = new HashSet<>(nodeIds);
        Map<String, List<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegrees = new HashMap<>();

        // Initialize adjacency list and in-degrees
        for (String nodeId : nodeIds) {
            adjacencyList.put(nodeId, new ArrayList<>());
            inDegrees.put(nodeId, 0);
        }

        // Build graph from edges
        for (PipelineEdge edge : pipeline.getEdges()) {
            if (!nodeSet.contains(edge.getFrom()) || !nodeSet.contains(edge.getTo())) {
                continue; // Will be caught in operator validation
            }
            adjacencyList.get(edge.getFrom()).add(edge.getTo());
            inDegrees.put(edge.getTo(), inDegrees.get(edge.getTo()) + 1);
        }

        // Detect cycles using DFS
        boolean hasCycles = detectCycles(nodeIds, adjacencyList);

        // Find source and sink nodes
        List<String> sourceNodes = nodeIds.stream()
            .filter(id -> inDegrees.get(id) == 0)
            .toList();

        List<String> sinkNodes = nodeIds.stream()
            .filter(id -> adjacencyList.get(id).isEmpty())
            .toList();

        // Find orphaned nodes (no incoming or outgoing edges)
        Set<String> connectedNodes = new HashSet<>();
        for (PipelineEdge edge : pipeline.getEdges()) {
            connectedNodes.add(edge.getFrom());
            connectedNodes.add(edge.getTo());
        }
        List<String> orphanedNodes = nodeIds.stream()
            .filter(id -> !connectedNodes.contains(id))
            .toList();

        // Check if graph is connected (ignoring direction)
        boolean isConnected = checkConnectivity(nodeIds, adjacencyList);

        // Calculate maximum depth
        int maxDepth = calculateMaxDepth(nodeIds, adjacencyList, inDegrees);

        // Find all paths from source to sink
        List<List<String>> paths = findAllPaths(sourceNodes, sinkNodes, adjacencyList);

        return new DAGValidationResult(
            hasCycles,
            isConnected,
            orphanedNodes,
            sourceNodes,
            sinkNodes,
            paths,
            maxDepth
        );
    }

    /**
     * Validates schema compatibility between connected nodes.
     */
    private SchemaValidationResult validateSchemaCompatibility(PipelineDefinition pipeline) {
        List<PipelineValidationResult.SchemaMismatch> incompatibleSchemas = new ArrayList<>();
        List<String> missingSchemas = new ArrayList<>();
        List<String> invalidSchemas = new ArrayList<>();

        Map<String, String> nodeOutputSchemas = new HashMap<>();
        Map<String, String> nodeInputSchemas = new HashMap<>();

        // Collect all node schemas
        for (PipelineNode node : pipeline.getNodes()) {
            if (node.getOutputSchema() != null) {
                nodeOutputSchemas.put(node.getId(), node.getOutputSchema());
            } else {
                missingSchemas.add("Node " + node.getId() + " missing output schema");
            }

            if (node.getInputSchema() != null) {
                nodeInputSchemas.put(node.getId(), node.getInputSchema());
            } else {
                missingSchemas.add("Node " + node.getId() + " missing input schema");
            }
        }

        // Validate edge schema compatibility
        for (PipelineEdge edge : pipeline.getEdges()) {
            String fromNode = edge.getFrom();
            String toNode = edge.getTo();

            String outputSchema = nodeOutputSchemas.get(fromNode);
            String inputSchema = nodeInputSchemas.get(toNode);

            if (outputSchema == null || inputSchema == null) {
                continue; // Already captured as missing schemas
            }

            if (!edge.getSchemaValidation()) {
                continue; // Schema validation disabled for this edge
            }

            // Check schema compatibility (simplified check - in real implementation would use schema registry)
            if (!outputSchema.equals(inputSchema)) {
                incompatibleSchemas.add(new SchemaMismatch(
                    fromNode,
                    toNode,
                    inputSchema,
                    outputSchema,
                    "SCHEMA_MISMATCH",
                    "Output schema of " + fromNode + " does not match input schema of " + toNode
                ));
            }
        }

        // Validate pipeline-level schemas
        if (pipeline.getInputSchema() != null) {
            List<String> sourceNodes = findSourceNodes(pipeline);
            for (String sourceNode : sourceNodes) {
                String nodeInputSchema = nodeInputSchemas.get(sourceNode);
                if (nodeInputSchema != null && !pipeline.getInputSchema().equals(nodeInputSchema)) {
                    incompatibleSchemas.add(new SchemaMismatch(
                        "PIPELINE_INPUT",
                        sourceNode,
                        nodeInputSchema,
                        pipeline.getInputSchema(),
                        "PIPELINE_INPUT_MISMATCH",
                        "Pipeline input schema does not match source node input schema"
                    ));
                }
            }
        }

        if (pipeline.getOutputSchema() != null) {
            List<String> sinkNodes = findSinkNodes(pipeline);
            for (String sinkNode : sinkNodes) {
                String nodeOutputSchema = nodeOutputSchemas.get(sinkNode);
                if (nodeOutputSchema != null && !pipeline.getOutputSchema().equals(nodeOutputSchema)) {
                    incompatibleSchemas.add(new SchemaMismatch(
                        sinkNode,
                        "PIPELINE_OUTPUT",
                        pipeline.getOutputSchema(),
                        nodeOutputSchema,
                        "PIPELINE_OUTPUT_MISMATCH",
                        "Sink node output schema does not match pipeline output schema"
                    ));
                }
            }
        }

        boolean schemasCompatible = incompatibleSchemas.isEmpty() && missingSchemas.isEmpty();

        return new SchemaValidationResult(
            schemasCompatible,
            incompatibleSchemas,
            missingSchemas,
            invalidSchemas
        );
    }

    /**
     * Validates operator classes and configurations.
     */
    private OperatorValidationResult validateOperators(PipelineDefinition pipeline) {
        List<String> missingOperators = new ArrayList<>();
        List<String> invalidOperators = new ArrayList<>();
        List<OperatorConfigError> configErrors = new ArrayList<>();

        for (PipelineNode node : pipeline.getNodes()) {
            String operatorClass = node.getOperator();
            if (operatorClass == null || operatorClass.trim().isEmpty()) {
                missingOperators.add("Node " + node.getId() + " missing operator class");
                continue;
            }

            try {
                // Check if operator class exists and can be loaded
                Class<?> clazz = Class.forName(operatorClass);
                
                // Validate operator implements required interface based on node type
                validateOperatorInterface(node, clazz, configErrors);

                // Validate operator configuration
                validateOperatorConfiguration(node, configErrors);

            } catch (ClassNotFoundException e) {
                missingOperators.add("Operator class not found: " + operatorClass + " for node " + node.getId());
            } catch (Exception e) {
                invalidOperators.add("Invalid operator: " + operatorClass + " for node " + node.getId() + " - " + e.getMessage());
            }
        }

        // Validate edge endpoints reference existing nodes
        Set<String> nodeIds = pipeline.getNodes().stream()
            .map(PipelineNode::getId)
            .collect(Collectors.toSet());

        for (PipelineEdge edge : pipeline.getEdges()) {
            if (!nodeIds.contains(edge.getFrom())) {
                configErrors.add(new OperatorConfigError(
                    edge.getFrom(),
                    "EDGE",
                    "INVALID_SOURCE",
                    "Source node does not exist: " + edge.getFrom(),
                    List.of(edge.getFrom())
                ));
            }
            if (!nodeIds.contains(edge.getTo())) {
                configErrors.add(new OperatorConfigError(
                    edge.getTo(),
                    "EDGE",
                    "INVALID_TARGET",
                    "Target node does not exist: " + edge.getTo(),
                    List.of(edge.getTo())
                ));
            }
        }

        boolean operatorsValid = missingOperators.isEmpty() && invalidOperators.isEmpty() && configErrors.isEmpty();

        return new OperatorValidationResult(
            operatorsValid,
            missingOperators,
            invalidOperators,
            configErrors
        );
    }

    /**
     * Validates performance constraints and resource requirements.
     */
    private PerformanceValidationResult validatePerformanceConstraints(PipelineDefinition pipeline) {
        List<PerformanceViolation> violations = new ArrayList<>();
        Map<String, Object> estimatedResources = new HashMap<>();
        Map<String, Object> bottlenecks = new HashMap<>();

        int totalCpuCores = 0;
        long totalMemoryMB = 0;

        for (PipelineNode node : pipeline.getNodes()) {
            PipelineNode.ResourceRequirements resources = node.getResourceRequirements();
            if (resources != null) {
                totalCpuCores += resources.cpuCores();
                totalMemoryMB += resources.memoryMB();

                // Validate resource constraints
                if (resources.cpuCores() > 16) {
                    violations.add(new PerformanceViolation(
                        node.getId(),
                        "CPU_LIMIT",
                        "CPU cores exceed recommended maximum",
                        resources.cpuCores(),
                        16
                    ));
                }

                if (resources.memoryMB() > 32768) {
                    violations.add(new PerformanceViolation(
                        node.getId(),
                        "MEMORY_LIMIT",
                        "Memory exceeds recommended maximum",
                        resources.memoryMB(),
                        32768
                    ));
                }
            }

            // Validate timeout
            if (node.getTimeoutSeconds() != null && node.getTimeoutSeconds() > 3600) {
                violations.add(new PerformanceViolation(
                    node.getId(),
                    "TIMEOUT_LIMIT",
                    "Timeout exceeds recommended maximum",
                    node.getTimeoutSeconds(),
                    3600
                ));
            }

            // Validate parallelism
            if (node.getParallelism() != null && node.getParallelism() > 100) {
                violations.add(new PerformanceViolation(
                    node.getId(),
                    "PARALLELISM_LIMIT",
                    "Parallelism exceeds recommended maximum",
                    node.getParallelism(),
                    100
                ));
            }
        }

        // Estimate total resources
        estimatedResources.put("totalCpuCores", totalCpuCores);
        estimatedResources.put("totalMemoryMB", totalMemoryMB);
        estimatedResources.put("nodeCount", pipeline.getNodes().size());
        estimatedResources.put("edgeCount", pipeline.getEdges().size());

        // Identify potential bottlenecks
        List<String> highResourceNodes = pipeline.getNodes().stream()
            .filter(node -> {
                PipelineNode.ResourceRequirements resources = node.getResourceRequirements();
                return resources != null && (resources.cpuCores() > 8 || resources.memoryMB() > 8192);
            })
            .map(PipelineNode::getId)
            .toList();

        if (!highResourceNodes.isEmpty()) {
            bottlenecks.put("highResourceNodes", highResourceNodes);
        }

        // Check for deep pipelines (potential performance issues)
        int maxDepth = calculatePipelineDepth(pipeline);
        if (maxDepth > 20) {
            bottlenecks.put("pipelineDepth", maxDepth);
        }

        boolean performanceConstraintsMet = violations.isEmpty();

        return new PerformanceValidationResult(
            performanceConstraintsMet,
            violations,
            estimatedResources,
            bottlenecks
        );
    }

    /**
     * Validates security constraints and access controls.
     */
    private SecurityValidationResult validateSecurityConstraints(PipelineDefinition pipeline) {
        List<SecurityViolation> violations = new ArrayList<>();
        List<String> dataPrivacyIssues = new ArrayList<>();
        List<String> accessControlIssues = new ArrayList<>();

        for (PipelineNode node : pipeline.getNodes()) {
            // Check for hardcoded secrets in configuration
            Map<String, Object> config = node.getConfiguration();
            if (config != null) {
                for (Map.Entry<String, Object> entry : config.entrySet()) {
                    String key = entry.getKey().toLowerCase();
                    Object value = entry.getValue();
                    
                    if (value instanceof String && isCredentialField(key) && 
                        !((String) value).startsWith("${") && !((String) value).startsWith("secret:")) {
                        violations.add(new SecurityViolation(
                            node.getId(),
                            "HARDCODED_CREDENTIAL",
                            "HIGH",
                            "Potential hardcoded credential in configuration: " + key,
                            List.of("Use secret references or environment variables")
                        ));
                    }
                }
            }

            // Validate data access patterns
            if (node.getType() == PipelineNode.NodeType.SOURCE || 
                node.getType() == PipelineNode.NodeType.SINK) {
                // Check for proper data access controls
                if (!hasAccessControlDefined(node)) {
                    accessControlIssues.add("Node " + node.getId() + " missing access control configuration");
                }
            }
        }

        // Validate data flow for privacy concerns
        for (PipelineEdge edge : pipeline.getEdges()) {
            if (hasTransformationRules(edge)) {
                // Check if transformation rules include data anonymization when needed
                if (containsSensitiveData(edge)) {
                    boolean hasAnonymization = edge.getTransformationRules().stream()
                        .anyMatch(rule -> "ANONYMIZE".equals(rule.type()) || "MASK".equals(rule.type()));
                    
                    if (!hasAnonymization) {
                        dataPrivacyIssues.add("Edge " + edge.getFrom() + "->" + edge.getTo() + 
                            " may expose sensitive data without anonymization");
                    }
                }
            }
        }

        boolean securityConstraintsMet = violations.isEmpty() && dataPrivacyIssues.isEmpty() && accessControlIssues.isEmpty();

        return new SecurityValidationResult(
            securityConstraintsMet,
            violations,
            dataPrivacyIssues,
            accessControlIssues
        );
    }

    // ============ Helper Methods ============

    private boolean detectCycles(List<String> nodeIds, Map<String, List<String>> adjacencyList) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String nodeId : nodeIds) {
            if (hasCycleDFS(nodeId, adjacencyList, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleDFS(String nodeId, Map<String, List<String>> adjacencyList, 
                               Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }

        visited.add(nodeId);
        recursionStack.add(nodeId);

        for (String neighbor : adjacencyList.get(nodeId)) {
            if (hasCycleDFS(neighbor, adjacencyList, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    private boolean checkConnectivity(List<String> nodeIds, Map<String, List<String>> adjacencyList) {
        if (nodeIds.isEmpty()) return true;

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(nodeIds.get(0));
        visited.add(nodeIds.get(0));

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adjacencyList.get(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
            // Also check reverse connections for undirected connectivity
            for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
                if (entry.getValue().contains(current) && !visited.contains(entry.getKey())) {
                    visited.add(entry.getKey());
                    queue.add(entry.getKey());
                }
            }
        }

        return visited.size() == nodeIds.size();
    }

    private int calculateMaxDepth(List<String> nodeIds, Map<String, List<String>> adjacencyList, 
                                Map<String, Integer> inDegrees) {
        Map<String, Integer> depths = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // Initialize queue with source nodes
        for (String nodeId : nodeIds) {
            if (inDegrees.get(nodeId) == 0) {
                queue.add(nodeId);
                depths.put(nodeId, 0);
            }
        }

        int maxDepth = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);
            maxDepth = Math.max(maxDepth, currentDepth);

            for (String neighbor : adjacencyList.get(current)) {
                depths.put(neighbor, currentDepth + 1);
                queue.add(neighbor);
            }
        }

        return maxDepth;
    }

    private List<List<String>> findAllPaths(List<String> sourceNodes, List<String> sinkNodes, 
                                           Map<String, List<String>> adjacencyList) {
        List<List<String>> allPaths = new ArrayList<>();
        
        for (String source : sourceNodes) {
            for (String sink : sinkNodes) {
                List<String> currentPath = new ArrayList<>();
                Set<String> visited = new HashSet<>();
                findPathsDFS(source, sink, adjacencyList, currentPath, visited, allPaths);
            }
        }
        
        return allPaths;
    }

    private void findPathsDFS(String current, String target, Map<String, List<String>> adjacencyList,
                             List<String> currentPath, Set<String> visited, List<List<String>> allPaths) {
        currentPath.add(current);
        visited.add(current);

        if (current.equals(target)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (String neighbor : adjacencyList.get(current)) {
                if (!visited.contains(neighbor)) {
                    findPathsDFS(neighbor, target, adjacencyList, currentPath, visited, allPaths);
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }

    private List<String> findSourceNodes(PipelineDefinition pipeline) {
        Set<String> targets = pipeline.getEdges().stream()
            .map(PipelineEdge::getTo)
            .collect(Collectors.toSet());

        return pipeline.getNodes().stream()
            .map(PipelineNode::getId)
            .filter(id -> !targets.contains(id))
            .toList();
    }

    private List<String> findSinkNodes(PipelineDefinition pipeline) {
        Set<String> sources = pipeline.getEdges().stream()
            .map(PipelineEdge::getFrom)
            .collect(Collectors.toSet());

        return pipeline.getNodes().stream()
            .map(PipelineNode::getId)
            .filter(id -> !sources.contains(id))
            .toList();
    }

    private int calculatePipelineDepth(PipelineDefinition pipeline) {
        // Simplified depth calculation
        return calculateMaxDepth(
            pipeline.getNodes().stream().map(PipelineNode::getId).toList(),
            pipeline.getEdges().stream()
                .collect(Collectors.groupingBy(
                    PipelineEdge::getFrom,
                    Collectors.mapping(PipelineEdge::getTo, Collectors.toList())
                )),
            pipeline.getEdges().stream()
                .collect(Collectors.groupingBy(
                    PipelineEdge::getTo,
                    Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ))
        );
    }

    private void validateOperatorInterface(PipelineNode node, Class<?> clazz, List<OperatorConfigError> errors) {
        // Simplified interface validation - in real implementation would check specific interfaces
        String expectedInterface = switch (node.getType()) {
            case SOURCE -> "DataSourceOperator";
            case TRANSFORM -> "DataTransformOperator";
            case SINK -> "DataSinkOperator";
            default -> "PipelineOperator";
        };

        // This is a placeholder - actual implementation would check for interface implementation
        log.debug("Validating operator interface for node {}: {} -> {}", 
            node.getId(), clazz.getSimpleName(), expectedInterface);
    }

    private void validateOperatorConfiguration(PipelineNode node, List<OperatorConfigError> errors) {
        // Simplified configuration validation
        if (node.getConfiguration() == null || node.getConfiguration().isEmpty()) {
            errors.add(new OperatorConfigError(
                node.getId(),
                node.getOperator(),
                "MISSING_CONFIG",
                "Operator configuration is empty",
                List.of("configuration")
            ));
        }
    }

    private void collectValidationErrors(DAGValidationResult dagResult, SchemaValidationResult schemaResult,
                                       OperatorValidationResult operatorResult, PerformanceValidationResult performanceResult,
                                       SecurityValidationResult securityResult,
                                       List<PipelineValidationResult.ValidationError> errors,
                                       List<PipelineValidationResult.ValidationWarning> warnings,
                                       List<PipelineValidationResult.ValidationRecommendation> recommendations) {
        // Collect DAG errors
        if (dagResult.hasCycles()) {
            errors.add(new PipelineValidationResult.ValidationError(
                "CYCLES_DETECTED",
                "Pipeline contains cycles which would cause infinite loops",
                "ERROR",
                "DAG_STRUCTURE",
                dagResult.paths().stream().flatMap(List::stream).distinct().toList(),
                Map.of("cycleCount", dagResult.paths().size())
            ));
        }

        if (!dagResult.orphanedNodes().isEmpty()) {
            warnings.add(new PipelineValidationResult.ValidationWarning(
                "ORPHANED_NODES",
                "Pipeline contains nodes that are not connected to the main flow",
                "DAG_STRUCTURE",
                dagResult.orphanedNodes(),
                Map.of("orphanedCount", dagResult.orphanedNodes().size())
            ));
        }

        // Collect schema errors
        for (SchemaMismatch mismatch : schemaResult.incompatibleSchemas()) {
            errors.add(new PipelineValidationResult.ValidationError(
                "SCHEMA_INCOMPATIBLE",
                mismatch.description(),
                "ERROR",
                "SCHEMA_VALIDATION",
                List.of(mismatch.fromNode(), mismatch.toNode()),
                Map.of("expected", mismatch.expectedSchema(), "actual", mismatch.actualSchema())
            ));
        }

        // Collect operator errors
        for (String missing : operatorResult.missingOperators()) {
            errors.add(new PipelineValidationResult.ValidationError(
                "OPERATOR_MISSING",
                missing,
                "ERROR",
                "OPERATOR_VALIDATION",
                List.of(),
                Map.of()
            ));
        }

        // Collect performance warnings
        for (PerformanceViolation violation : performanceResult.violations()) {
            warnings.add(new PipelineValidationResult.ValidationWarning(
                "PERFORMANCE_VIOLATION",
                violation.description(),
                "PERFORMANCE",
                List.of(violation.nodeId()),
                Map.of("actual", violation.actualValue(), "limit", violation.limitValue())
            ));
        }

        // Collect security violations
        for (SecurityViolation violation : securityResult.violations()) {
            if ("HIGH".equals(violation.severity())) {
                errors.add(new PipelineValidationResult.ValidationError(
                    "SECURITY_VIOLATION",
                    violation.description(),
                    "ERROR",
                    "SECURITY",
                    List.of(violation.nodeId()),
                    Map.of("type", violation.violationType(), "recommendations", violation.recommendations())
                ));
            } else {
                warnings.add(new PipelineValidationResult.ValidationWarning(
                    "SECURITY_WARNING",
                    violation.description(),
                    "SECURITY",
                    List.of(violation.nodeId()),
                    Map.of("type", violation.violationType())
                ));
            }
        }
    }

    private PipelineValidationResult createErrorResult(String message) {
        return PipelineValidationResult.builder()
            .valid(false)
            .validationTimestamp(Instant.now())
            .errors(List.of(new PipelineValidationResult.ValidationError(
                "VALIDATION_ERROR",
                message,
                "ERROR",
                "SYSTEM",
                List.of(),
                Map.of()
            )))
            .build();
    }

    private boolean isCredentialField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("password") || lower.contains("secret") || 
               lower.contains("key") || lower.contains("token") ||
               lower.contains("credential") || lower.contains("auth");
    }

    private boolean hasAccessControlDefined(PipelineNode node) {
        // Simplified check - in real implementation would validate access control configuration
        return node.getMetadata() != null && node.getMetadata().containsKey("accessControl");
    }

    private boolean hasTransformationRules(PipelineEdge edge) {
        return edge.getTransformationRules() != null && !edge.getTransformationRules().isEmpty();
    }

    private boolean containsSensitiveData(PipelineEdge edge) {
        // Simplified check - in real implementation would analyze data schemas
        return edge.getMetadata() != null && 
               Boolean.TRUE.equals(edge.getMetadata().get("containsSensitiveData"));
    }
}
