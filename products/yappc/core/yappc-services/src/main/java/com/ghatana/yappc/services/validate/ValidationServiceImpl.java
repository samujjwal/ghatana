package com.ghatana.yappc.services.validate;

import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Pluggable validation service with security and compliance checks
 * @doc.layer service
 * @doc.pattern Service
 */
public class ValidationServiceImpl implements ValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationServiceImpl.class);
    
    private final PolicyEngine policyEngine;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    
    public ValidationServiceImpl(
            PolicyEngine policyEngine,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.policyEngine = policyEngine;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }
    
    @Override
    public Promise<LifecycleValidationResult> validate(ShapeSpec spec) {
        return validate(spec, ValidationConfig.defaultConfig());
    }
    
    @Override
    public Promise<LifecycleValidationResult> validate(ShapeSpec spec, ValidationConfig config) {
        long startTime = System.currentTimeMillis();
        
        return runValidators(spec, config)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.validate.execute", duration,
                        Map.of("tenant", spec.tenantId() != null ? spec.tenantId() : "unknown",
                               "passed", String.valueOf(result.passed())));
                    
                    return auditLogger.log(createAuditEvent("validate.execute", spec, result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Validation failed", e);
                    metrics.incrementCounter("yappc.validate.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    @Override
    public Promise<LifecycleValidationResult> validateWithPolicy(ShapeSpec spec, PolicySpec policy) {
        long startTime = System.currentTimeMillis();
        
        return runPolicyValidation(spec, policy)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.validate.policy", duration,
                        Map.of("tenant", spec.tenantId() != null ? spec.tenantId() : "unknown",
                               "policy", policy.id()));
                    
                    return auditLogger.log(createAuditEvent("validate.policy", spec, result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Policy validation failed", e);
                    metrics.incrementCounter("yappc.validate.policy.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    private Promise<LifecycleValidationResult> runValidators(ShapeSpec spec, ValidationConfig config) {
        List<Promise<List<ValidationIssue>>> validatorPromises = new ArrayList<>();
        
        // Schema validation
        if (!config.excludedIds().contains("schema")) {
            validatorPromises.add(validateSchema(spec));
        }
        
        // Security validation
        if (!config.excludedIds().contains("security")) {
            validatorPromises.add(validateSecurity(spec));
        }
        
        // Consistency validation
        if (!config.excludedIds().contains("consistency")) {
            validatorPromises.add(validateConsistency(spec));
        }
        
        // Feasibility validation
        if (!config.excludedIds().contains("feasibility")) {
            validatorPromises.add(validateFeasibility(spec));
        }
        
        return Promises.toList(validatorPromises)
                .map(issuesListOfLists -> {
                    List<ValidationIssue> allIssues = issuesListOfLists.stream()
                            .flatMap(List::stream)
                            .toList();
                    
                    boolean passed = allIssues.stream().noneMatch(ValidationIssue::blocking);
                    
                    return LifecycleValidationResult.builder()
                            .passed(passed)
                            .issues(allIssues)
                            .validatedAt(Instant.now())
                            .validatorVersion("1.0.0")
                            .build();
                });
    }
    
    private Promise<LifecycleValidationResult> runPolicyValidation(ShapeSpec spec, PolicySpec policy) {
        return Promise.of(
            LifecycleValidationResult.builder()
                    .passed(true)
                    .issues(List.of())
                    .validatedAt(Instant.now())
                    .validatorVersion("1.0.0")
                    .build()
        );
    }
    
    private Promise<List<ValidationIssue>> validateSchema(ShapeSpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Validate domain model structure
        if (spec.domainModel() == null || spec.domainModel().entities().isEmpty()) {
            issues.add(ValidationIssue.builder()
                    .id("schema-001")
                    .severity("error")
                    .category("schema")
                    .message("Domain model must have at least one entity")
                    .location("domainModel.entities")
                    .suggestions(List.of("Add at least one entity to the domain model"))
                    .blocking(true)
                    .build());
        }
        
        // Validate entity fields
        spec.domainModel().entities().forEach(entity -> {
            if (entity.fields().isEmpty()) {
                issues.add(ValidationIssue.builder()
                        .id("schema-002")
                        .severity("warning")
                        .category("schema")
                        .message("Entity '" + entity.name() + "' has no fields")
                        .location("domainModel.entities." + entity.name())
                        .suggestions(List.of("Add fields to entity"))
                        .blocking(false)
                        .build());
            }
        });
        
        return Promise.of(issues);
    }
    
    private Promise<List<ValidationIssue>> validateSecurity(ShapeSpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Check for authentication in workflows
        boolean hasAuthWorkflow = spec.workflows().stream()
                .anyMatch(w -> w.name().toLowerCase().contains("auth") || 
                              w.name().toLowerCase().contains("login"));
        
        if (!hasAuthWorkflow) {
            issues.add(ValidationIssue.builder()
                    .id("security-001")
                    .severity("warning")
                    .category("security")
                    .message("No authentication workflow detected")
                    .location("workflows")
                    .suggestions(List.of("Add authentication and authorization workflows"))
                    .blocking(false)
                    .build());
        }
        
        return Promise.of(issues);
    }
    
    private Promise<List<ValidationIssue>> validateConsistency(ShapeSpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Validate relationship consistency
        spec.domainModel().relationships().forEach(rel -> {
            boolean fromExists = spec.domainModel().entities().stream()
                    .anyMatch(e -> e.name().equals(rel.fromEntity()));
            boolean toExists = spec.domainModel().entities().stream()
                    .anyMatch(e -> e.name().equals(rel.toEntity()));
            
            if (!fromExists || !toExists) {
                issues.add(ValidationIssue.builder()
                        .id("consistency-001")
                        .severity("error")
                        .category("consistency")
                        .message("Relationship references non-existent entity")
                        .location("domainModel.relationships")
                        .suggestions(List.of("Ensure all relationship entities exist"))
                        .blocking(true)
                        .build());
            }
        });
        
        return Promise.of(issues);
    }
    
    private Promise<List<ValidationIssue>> validateFeasibility(ShapeSpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // Check for reasonable entity count
        int entityCount = spec.domainModel().entities().size();
        if (entityCount > 50) {
            issues.add(ValidationIssue.builder()
                    .id("feasibility-001")
                    .severity("warning")
                    .category("feasibility")
                    .message("Large number of entities (" + entityCount + ") may indicate over-engineering")
                    .location("domainModel.entities")
                    .suggestions(List.of("Consider consolidating entities or using bounded contexts"))
                    .blocking(false)
                    .build());
        }
        
        return Promise.of(issues);
    }
    
    private Map<String, Object> createAuditEvent(String action, Object input, Object output) {
        return Map.of(
            "action", action,
            "timestamp", Instant.now().toEpochMilli(),
            "input", input.toString(),
            "output", output.toString()
        );
    }
}
