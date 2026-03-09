package com.ghatana.yappc.canvas.ai;

import com.ghatana.contracts.canvas.v1.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Canvas Validation Service
 * 
 * Java/ActiveJ implementation of ValidationAgent for hybrid backend.
 * Provides phase-specific validation logic with performance optimization.
 * 
 * @doc.type class
 * @doc.purpose Canvas validation service implementation
 * @doc.layer platform
 * @doc.pattern Service
 */
public class CanvasValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CanvasValidationService.class);
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();
    private static final int MAX_EXECUTION_TIME_MS = 10000; // 10 seconds
    
    private final MetricsCollector metrics;
    
    public CanvasValidationService(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }
    
    /**
     * Validate canvas design
     */
    public Promise<ValidationReport> validate(ValidateCanvasRequest request) {
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                // Start metrics
                metrics.incrementCounter("canvas.validation.requests");
                
                // Extract request data
                CanvasState canvasState = request.getCanvasState();
                LifecyclePhase phase = request.getLifecyclePhase();
                ValidationOptions options = request.hasOptions() ? request.getOptions() : getDefaultOptions();
                
                logger.info("Validating canvas {} for phase {}", 
                    canvasState.getCanvasId(), phase);
                
                // Build validation report
                ValidationReport.Builder reportBuilder = ValidationReport.newBuilder()
                    .setPhase(phase)
                    .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build())
                    .setValidationId(UUID.randomUUID().toString());
                
                // Run phase-specific validation
                List<ValidationIssue> issues = new ArrayList<>();
                switch (phase) {
                    case LIFECYCLE_PHASE_SHAPE:
                        issues.addAll(validateShapePhase(canvasState));
                        break;
                    case LIFECYCLE_PHASE_LAYOUT:
                        issues.addAll(validateLayoutPhase(canvasState));
                        break;
                    case LIFECYCLE_PHASE_COMPONENT:
                        issues.addAll(validateComponentPhase(canvasState));
                        break;
                    case LIFECYCLE_PHASE_VALIDATE:
                        issues.addAll(validateValidatePhase(canvasState));
                        break;
                    case LIFECYCLE_PHASE_DEPLOY:
                        issues.addAll(validateDeployPhase(canvasState));
                        break;
                    default:
                        logger.warn("Unknown lifecycle phase: {}", phase);
                }
                
                reportBuilder.addAllIssues(issues);
                
                // Identify gaps if requested
                List<String> gaps = new ArrayList<>();
                if (options.getIncludeGaps()) {
                    gaps.addAll(identifyGaps(canvasState));
                }
                reportBuilder.addAllGaps(gaps);
                
                // Assess risks if requested
                List<RiskAssessment> risks = new ArrayList<>();
                if (options.getIncludeRisks()) {
                    risks.addAll(assessRisks(canvasState, issues));
                }
                reportBuilder.addAllRisks(risks);
                
                // Calculate summary
                ValidationSummary summary = calculateSummary(issues, gaps, risks);
                reportBuilder.setSummary(summary);
                
                // Calculate score
                int score = calculateScore(summary);
                reportBuilder.setScore(score);
                
                // Record metrics
                long elapsedTime = System.currentTimeMillis() - startTime;
                metrics.recordTimer("canvas.validation.duration", elapsedTime);
                metrics.incrementCounter("canvas.validation.score", "value", String.valueOf(score));
                metrics.incrementCounter("canvas.validation.success");
                
                logger.info("Validation complete for canvas {} - Score: {}/100, Errors: {}, Warnings: {}", 
                    canvasState.getCanvasId(), score, summary.getErrors(), summary.getWarnings());
                
                return reportBuilder.build();
                
            } catch (Exception e) {
                metrics.incrementCounter("canvas.validation.errors");
                logger.error("Validation failed", e);
                throw new RuntimeException("Validation failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Validate SHAPE phase - Architecture design
     */
    private List<ValidationIssue> validateShapePhase(CanvasState canvasState) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<CanvasElement> elements = canvasState.getElementsList();
        
        // Check for empty canvas
        if (elements.isEmpty()) {
            issues.add(createIssue(
                IssueSeverity.ISSUE_SEVERITY_ERROR,
                "empty_canvas",
                "Empty Canvas",
                "Canvas is empty. Add at least one node to start designing.",
                List.of(),
                "Add nodes to define your architecture",
                false,
                LifecyclePhase.LIFECYCLE_PHASE_SHAPE
            ));
            return issues;
        }
        
        // Check for isolated nodes (nodes without connections)
        for (CanvasElement element : elements) {
            if (element.getKind() == ElementKind.ELEMENT_KIND_NODE && 
                element.getConnectionsCount() == 0) {
                issues.add(createIssue(
                    IssueSeverity.ISSUE_SEVERITY_WARNING,
                    "isolated_node",
                    "Isolated Node",
                    String.format("Node '%s' has no connections. Consider connecting to other layers.", 
                        element.getData().getFieldsOrDefault("label", com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue()),
                    List.of(element.getId()),
                    "Connect this node to data layer, API layer, or frontend components",
                    false,
                    LifecyclePhase.LIFECYCLE_PHASE_SHAPE
                ));
            }
        }
        
        // Check for missing layers
        boolean hasAPI = elements.stream().anyMatch(e -> e.getType() == ElementType.ELEMENT_TYPE_API);
        boolean hasData = elements.stream().anyMatch(e -> e.getType() == ElementType.ELEMENT_TYPE_DATA);
        boolean hasFrontend = elements.stream().anyMatch(e -> 
            e.getType() == ElementType.ELEMENT_TYPE_COMPONENT || 
            e.getType() == ElementType.ELEMENT_TYPE_PAGE);
        
        if (hasAPI && !hasData) {
            issues.add(createIssue(
                IssueSeverity.ISSUE_SEVERITY_ERROR,
                "missing_data_layer",
                "Missing Data Layer",
                "API layer exists but no data layer found. Add database or data source nodes.",
                List.of(),
                "Add data nodes to store and retrieve information",
                false,
                LifecyclePhase.LIFECYCLE_PHASE_SHAPE
            ));
        }
        
        if (hasFrontend && !hasAPI) {
            issues.add(createIssue(
                IssueSeverity.ISSUE_SEVERITY_WARNING,
                "missing_api_layer",
                "Missing API Layer",
                "Frontend components exist but no API layer found. Consider adding API nodes.",
                List.of(),
                "Add API nodes to connect frontend to backend services",
                false,
                LifecyclePhase.LIFECYCLE_PHASE_SHAPE
            ));
        }
        
        return issues;
    }
    
    /**
     * Validate LAYOUT phase - Positioning
     */
    private List<ValidationIssue> validateLayoutPhase(CanvasState canvasState) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<CanvasElement> elements = canvasState.getElementsList();
        
        // Check for overlapping elements
        Map<String, List<CanvasElement>> positionMap = new HashMap<>();
        for (CanvasElement element : elements) {
            if (element.hasPosition()) {
                String posKey = String.format("%.0f,%.0f", 
                    element.getPosition().getX(), 
                    element.getPosition().getY());
                positionMap.computeIfAbsent(posKey, k -> new ArrayList<>()).add(element);
            }
        }
        
        for (Map.Entry<String, List<CanvasElement>> entry : positionMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> elementIds = entry.getValue().stream()
                    .map(CanvasElement::getId)
                    .collect(Collectors.toList());
                    
                String elementNames = entry.getValue().stream()
                    .map(e -> e.getData().getFieldsOrDefault("label", 
                        com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue())
                    .collect(Collectors.joining(", "));
                    
                issues.add(createIssue(
                    IssueSeverity.ISSUE_SEVERITY_ERROR,
                    "overlapping_elements",
                    "Overlapping Elements",
                    String.format("Elements are overlapping: %s", elementNames),
                    elementIds,
                    "Move one or more elements to avoid collision",
                    false,
                    LifecyclePhase.LIFECYCLE_PHASE_LAYOUT
                ));
            }
        }
        
        // Check for out-of-bounds elements
        for (CanvasElement element : elements) {
            if (element.hasPosition()) {
                if (element.getPosition().getX() < 0 || element.getPosition().getY() < 0) {
                    issues.add(createIssue(
                        IssueSeverity.ISSUE_SEVERITY_ERROR,
                        "out_of_bounds",
                        "Out of Bounds",
                        String.format("Element '%s' is out of bounds (negative position)", 
                            element.getData().getFieldsOrDefault("label", 
                                com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue()),
                        List.of(element.getId()),
                        "Reposition element within canvas bounds",
                        true,
                        LifecyclePhase.LIFECYCLE_PHASE_LAYOUT
                    ));
                }
            }
        }
        
        return issues;
    }
    
    /**
     * Validate COMPONENT phase - Configuration
     */
    private List<ValidationIssue> validateComponentPhase(CanvasState canvasState) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<CanvasElement> elements = canvasState.getElementsList();
        
        for (CanvasElement element : elements) {
            // Check for unlabeled elements
            if (!element.getData().containsFields("label") || 
                element.getData().getFieldsOrThrow("label").getStringValue().isEmpty()) {
                issues.add(createIssue(
                    IssueSeverity.ISSUE_SEVERITY_WARNING,
                    "unlabeled_element",
                    "Unlabeled Element",
                    String.format("Element '%s' has no label", element.getId()),
                    List.of(element.getId()),
                    "Add a descriptive label to this element",
                    true,
                    LifecyclePhase.LIFECYCLE_PHASE_COMPONENT
                ));
            }
            
            // Validate API endpoints
            if (element.getType() == ElementType.ELEMENT_TYPE_API) {
                if (!element.getData().containsFields("endpoint") || 
                    !element.getData().containsFields("method")) {
                    issues.add(createIssue(
                        IssueSeverity.ISSUE_SEVERITY_ERROR,
                        "missing_api_config",
                        "Missing API Configuration",
                        String.format("API node '%s' is missing endpoint configuration", 
                            element.getData().getFieldsOrDefault("label", 
                                com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue()),
                        List.of(element.getId()),
                        "Add path and HTTP method to the API endpoint",
                        false,
                        LifecyclePhase.LIFECYCLE_PHASE_COMPONENT
                    ));
                }
            }
            
            // Validate database configuration
            if (element.getType() == ElementType.ELEMENT_TYPE_DATA) {
                if (!element.getData().containsFields("connectionString") || 
                    !element.getData().containsFields("tableName")) {
                    issues.add(createIssue(
                        IssueSeverity.ISSUE_SEVERITY_ERROR,
                        "missing_db_config",
                        "Missing Database Configuration",
                        String.format("Data node '%s' is missing database configuration", 
                            element.getData().getFieldsOrDefault("label", 
                                com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue()),
                        List.of(element.getId()),
                        "Add connection string and table name",
                        false,
                        LifecyclePhase.LIFECYCLE_PHASE_COMPONENT
                    ));
                }
            }
        }
        
        return issues;
    }
    
    /**
     * Validate VALIDATE phase - Comprehensive validation
     */
    private List<ValidationIssue> validateValidatePhase(CanvasState canvasState) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Run all previous validations
        issues.addAll(validateShapePhase(canvasState));
        issues.addAll(validateLayoutPhase(canvasState));
        issues.addAll(validateComponentPhase(canvasState));
        
        // Additional comprehensive checks
        issues.addAll(detectCircularDependencies(canvasState));
        
        return issues;
    }
    
    /**
     * Validate DEPLOY phase - Deployment readiness
     */
    private List<ValidationIssue> validateDeployPhase(CanvasState canvasState) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Run all validations
        issues.addAll(validateValidatePhase(canvasState));
        
        // Check deployment readiness
        long errorCount = issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.ISSUE_SEVERITY_ERROR)
            .count();
            
        if (errorCount > 0) {
            issues.add(createIssue(
                IssueSeverity.ISSUE_SEVERITY_ERROR,
                "not_ready_for_deployment",
                "Not Ready for Deployment",
                String.format("%d errors must be fixed before deployment", errorCount),
                List.of(),
                "Fix all errors before deploying",
                false,
                LifecyclePhase.LIFECYCLE_PHASE_DEPLOY
            ));
        }
        
        return issues;
    }
    
    /**
     * Detect circular dependencies
     */
    private List<ValidationIssue> detectCircularDependencies(CanvasState canvasState) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Build adjacency list
        Map<String, List<String>> graph = new HashMap<>();
        for (CanvasElement element : canvasState.getElementsList()) {
            graph.putIfAbsent(element.getId(), new ArrayList<>());
            graph.get(element.getId()).addAll(element.getConnectionsList());
        }
        
        // DFS to detect cycles
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        List<String> cycle = new ArrayList<>();
        
        for (String node : graph.keySet()) {
            if (hasCycle(node, visited, recStack, graph, cycle)) {
                issues.add(createIssue(
                    IssueSeverity.ISSUE_SEVERITY_ERROR,
                    "circular_dependency",
                    "Circular Dependency Detected",
                    String.format("Circular dependency detected in element connections: %s", 
                        String.join(" → ", cycle)),
                    new ArrayList<>(cycle),
                    "Remove one of the connections to break the cycle",
                    false,
                    LifecyclePhase.LIFECYCLE_PHASE_VALIDATE
                ));
                break;
            }
        }
        
        return issues;
    }
    
    private boolean hasCycle(String node, Set<String> visited, Set<String> recStack, 
                            Map<String, List<String>> graph, List<String> cycle) {
        if (recStack.contains(node)) {
            cycle.add(node);
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        
        visited.add(node);
        recStack.add(node);
        
        for (String neighbor : graph.getOrDefault(node, List.of())) {
            if (hasCycle(neighbor, visited, recStack, graph, cycle)) {
                if (!cycle.isEmpty() && !cycle.get(0).equals(node)) {
                    cycle.add(0, node);
                }
                return true;
            }
        }
        
        recStack.remove(node);
        return false;
    }
    
    /**
     * Identify gaps in architecture
     */
    private List<String> identifyGaps(CanvasState canvasState) {
        List<String> gaps = new ArrayList<>();
        List<CanvasElement> elements = canvasState.getElementsList();
        
        boolean hasAPI = elements.stream().anyMatch(e -> e.getType() == ElementType.ELEMENT_TYPE_API);
        boolean hasData = elements.stream().anyMatch(e -> e.getType() == ElementType.ELEMENT_TYPE_DATA);
        boolean hasFrontend = elements.stream().anyMatch(e -> 
            e.getType() == ElementType.ELEMENT_TYPE_COMPONENT || 
            e.getType() == ElementType.ELEMENT_TYPE_PAGE);
        
        if (!hasAPI) {
            gaps.add("No API layer defined");
        }
        if (!hasData) {
            gaps.add("No data layer defined");
        }
        if (!hasFrontend) {
            gaps.add("No frontend components defined");
        }
        
        return gaps;
    }
    
    /**
     * Assess risks
     */
    private List<RiskAssessment> assessRisks(CanvasState canvasState, List<ValidationIssue> issues) {
        List<RiskAssessment> risks = new ArrayList<>();
        
        // Security risks
        for (CanvasElement element : canvasState.getElementsList()) {
            if (element.getType() == ElementType.ELEMENT_TYPE_API) {
                if (!element.getData().containsFields("auth") || 
                    !element.getData().getFieldsOrThrow("auth").getBoolValue()) {
                    risks.add(createRisk(
                        RiskType.RISK_TYPE_SECURITY,
                        RiskSeverity.RISK_SEVERITY_HIGH,
                        "Exposed API Endpoint",
                        String.format("API endpoint '%s' is exposed without authentication", 
                            element.getData().getFieldsOrDefault("label", 
                                com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue()),
                        "Unauthorized access to sensitive data or operations",
                        "Add authentication and authorization to the API endpoint"
                    ));
                }
            }
        }
        
        // Performance risks
        long dataNodeCount = canvasState.getElementsList().stream()
            .filter(e -> e.getType() == ElementType.ELEMENT_TYPE_DATA)
            .count();
        if (dataNodeCount > 5) {
            risks.add(createRisk(
                RiskType.RISK_TYPE_PERFORMANCE,
                RiskSeverity.RISK_SEVERITY_MEDIUM,
                "Multiple Data Sources",
                String.format("Canvas has %d data sources which may impact performance", dataNodeCount),
                "Slow query performance, increased latency",
                "Consider consolidating data sources or implementing caching"
            ));
        }
        
        return risks;
    }
    
    /**
     * Calculate validation summary
     */
    private ValidationSummary calculateSummary(List<ValidationIssue> issues, 
                                               List<String> gaps, 
                                               List<RiskAssessment> risks) {
        int errors = (int) issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.ISSUE_SEVERITY_ERROR)
            .count();
        int warnings = (int) issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.ISSUE_SEVERITY_WARNING)
            .count();
        int info = (int) issues.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.ISSUE_SEVERITY_INFO)
            .count();
            
        return ValidationSummary.newBuilder()
            .setErrors(errors)
            .setWarnings(warnings)
            .setInfo(info)
            .setGaps(gaps.size())
            .setRisks(risks.size())
            .build();
    }
    
    /**
     * Calculate validation score (0-100)
     */
    private int calculateScore(ValidationSummary summary) {
        int base = 100;
        int score = base 
            - (summary.getErrors() * 15)
            - (summary.getWarnings() * 5)
            - (summary.getInfo() * 1)
            - (summary.getGaps() * 10);
            
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Create validation issue
     */
    private ValidationIssue createIssue(IssueSeverity severity, String category, String title,
                                       String description, List<String> elementIds,
                                       String suggestion, boolean autoFixable,
                                       LifecyclePhase phase) {
        return ValidationIssue.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setSeverity(severity)
            .setCategory(category)
            .setTitle(title)
            .setDescription(description)
            .addAllElementIds(elementIds)
            .setSuggestion(suggestion)
            .setAutoFixable(autoFixable)
            .setPhase(phase)
            .build();
    }
    
    /**
     * Create risk assessment
     */
    private RiskAssessment createRisk(RiskType type, RiskSeverity severity, String title,
                                     String description, String impact, String mitigation) {
        return RiskAssessment.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setType(type)
            .setSeverity(severity)
            .setTitle(title)
            .setDescription(description)
            .setImpact(impact)
            .setMitigation(mitigation)
            .build();
    }
    
    /**
     * Get default validation options
     */
    private ValidationOptions getDefaultOptions() {
        return ValidationOptions.newBuilder()
            .setIncludeRisks(true)
            .setIncludeGaps(true)
            .setIncludeSuggestions(true)
            .build();
    }
}
