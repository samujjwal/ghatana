package com.ghatana.yappc.services.validate;

import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        Objects.requireNonNull(spec, "spec must not be null");
        ValidationConfig effectiveConfig = config == null ? ValidationConfig.defaultConfig() : config;
        long startTime = System.currentTimeMillis();

        return runValidators(spec, effectiveConfig)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = Map.of(
                        "tenant", spec.tenantId() != null ? spec.tenantId() : "unknown",
                        "passed", String.valueOf(result.passed()));
                    metrics.recordTimer("yappc.validate.execute", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.validate.execute", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("validate.execute", spec, result))
                            .then(v -> Promise.of(result), auditErr -> {
                                log.warn("Audit logging failed for validate.execute, continuing", auditErr);
                                return Promise.of(result);
                            });
                })
                .whenException(e -> {
                    log.error("Validation failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.validate.execute",
                        e,
                        ServiceObservability.tenantTag(spec.tenantId()));
                });
    }

    @Override
    public Promise<LifecycleValidationResult> validateWithPolicy(ShapeSpec spec, PolicySpec policy) {
        long startTime = System.currentTimeMillis();

        return runPolicyValidation(spec, policy)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = Map.of(
                        "tenant", spec.tenantId() != null ? spec.tenantId() : "unknown",
                        "policy", policy.id());
                    metrics.recordTimer("yappc.validate.policy", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.validate.policy", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("validate.policy", spec, result))
                            .then(v -> Promise.of(result), auditErr -> {
                                log.warn("Audit logging failed for validate.policy, continuing", auditErr);
                                return Promise.of(result);
                            });
                })
                .whenException(e -> {
                    log.error("Policy validation failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.validate.policy",
                        e,
                        Map.of("tenant", spec.tenantId() != null ? spec.tenantId() : "unknown",
                               "policy", policy.id()));
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
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("specId", spec.id() != null ? spec.id() : "unknown");
        context.put("tenantId", spec.tenantId() != null ? spec.tenantId() : "unknown");
        context.put("entityCount", spec.domainModel() != null ? spec.domainModel().entities().size() : 0);
        context.put("workflowCount", spec.workflows() != null ? spec.workflows().size() : 0);
        context.put("policyId", policy.id());

        return policyEngine.evaluate(policy.id(), context)
                .map(passed -> {
                    List<ValidationIssue> issues = new ArrayList<>();
                    if (!passed) {
                        issues.add(ValidationIssue.builder()
                                .id("policy-001")
                                .severity("error")
                                .category("policy")
                                .message("Policy '" + policy.name() + "' validation failed")
                                .location("spec")
                                .suggestions(List.of("Review requirements for policy: " + policy.id()))
                                .blocking(true)
                                .build());
                    }
                    return LifecycleValidationResult.builder()
                            .passed(passed)
                            .issues(issues)
                            .validatedAt(Instant.now())
                            .validatorVersion("1.0.0")
                            .build();
                });
    }

    private Promise<List<ValidationIssue>> validateSchema(ShapeSpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        DomainModel model = spec.domainModel();

        // Validate domain model structure
        if (model == null || entities(model).isEmpty()) {
            issues.add(ValidationIssue.builder()
                    .id("schema-001")
                    .severity("error")
                    .category("schema")
                    .message("Domain model must have at least one entity")
                    .location("domainModel.entities")
                    .suggestions(List.of("Add at least one entity to the domain model"))
                    .blocking(true)
                    .build());
            return Promise.of(issues);
        }

        // Validate entity fields
        entities(model).forEach(entity -> {
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
        boolean hasAuthWorkflow = workflows(spec).stream()
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
        DomainModel model = spec.domainModel();
        if (model == null) {
            return Promise.of(issues);
        }

        // Validate relationship consistency
        model.relationships().forEach(rel -> {
            boolean fromExists = entities(model).stream()
                    .anyMatch(e -> e.name().equals(rel.fromEntity()));
            boolean toExists = entities(model).stream()
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
        int entityCount = spec.domainModel() == null ? 0 : entities(spec.domainModel()).size();
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

    private static List<EntitySpec> entities(DomainModel model) {
        return model.entities() == null ? Collections.emptyList() : model.entities();
    }

    private static List<com.ghatana.yappc.domain.shape.WorkflowSpec> workflows(ShapeSpec spec) {
        return spec.workflows() == null ? Collections.emptyList() : spec.workflows();
    }
}
