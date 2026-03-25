/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Workflow Integration (YAPPC-Ph9)
 */
package com.ghatana.yappc.services.lifecycle.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.platform.workflow.DefaultWorkflowContext;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle workflow integration service for YAPPC-Ph9.
 *
 * <p>Loads canonical workflow templates from {@code lifecycle-workflow-templates.yaml}
 * (classpath resource) and materialises them into {@link DurableWorkflowEngine}
 * step sequences. Provides a simple API for starting and tracking workflow runs.
 *
 * <p><b>Template YAML format:</b>
 * <pre>
 * workflows:
 *   - id: new-feature
 *     steps:
 *       - id: phase-ideation-to-intent
 *         type: lifecycle-phase-advance
 *         config:
 *           fromPhase: IDEATION
 *           toPhase: INTENT
 *       - id: gate-intent-complete
 *         type: lifecycle-gate-check
 *         config:
 *           phase: INTENT
 * </pre>
 *
 * <p><b>Supported step types:</b>
 * <ul>
 *   <li>{@code lifecycle-phase-advance} — updates workflow context with phase transition metadata</li>
 *   <li>{@code lifecycle-gate-check} — records gate-check metadata (always passes in current impl)</li>
 *   <li>Any other type — passthrough: logs and forwards context unchanged</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose YAPPC canonical workflow materializer and execution service (YAPPC-Ph9)
 * @doc.layer product
 * @doc.pattern Service, Bootstrapper
 * @doc.gaa.lifecycle perceive
 */
public class LifecycleWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LifecycleWorkflowService.class);

    /** Classpath resource name for the canonical workflow templates. */
    public static final String TEMPLATES_RESOURCE = "lifecycle-workflow-templates.yaml";

    private final DurableWorkflowEngine engine;
    private final ObjectMapper yamlMapper;

    /** templateId → ordered list of materialized step definitions. */
    private final Map<String, List<DurableWorkflowEngine.StepDefinition>> templateRegistry =
            new ConcurrentHashMap<>();

    /** runId → run state (for status queries). */
    private final Map<String, DurableWorkflowEngine.WorkflowRun> runRegistry =
            new ConcurrentHashMap<>();

    /**
     * Creates a new {@code LifecycleWorkflowService}.
     *
     * @param engine durable workflow engine to submit runs to
     */
    public LifecycleWorkflowService(@NotNull DurableWorkflowEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    // ─── Initialisation ───────────────────────────────────────────────────────

    /**
     * Loads and materialises all workflow templates from the classpath YAML.
     *
     * <p>Idempotent — already-registered templates are skipped silently.
     *
     * @return number of templates newly loaded
     */
    public int initialize() {
        InputStream yaml = getClass().getClassLoader().getResourceAsStream(TEMPLATES_RESOURCE);
        if (yaml == null) {
            log.warn("'{}' not found on classpath — 0 workflow templates loaded", TEMPLATES_RESOURCE);
            return 0;
        }

        Map<?, ?> root;
        try {
            root = yamlMapper.readValue(yaml, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse '{}': {}", TEMPLATES_RESOURCE, e.getMessage(), e);
            return 0;
        }

        Object workflowsObj = root.get("workflows");
        if (!(workflowsObj instanceof List<?> workflowsList)) {
            log.warn("'{}' has no 'workflows' list — 0 templates loaded", TEMPLATES_RESOURCE);
            return 0;
        }

        int loaded = 0;
        for (Object item : workflowsList) {
            if (!(item instanceof Map<?, ?> wf)) continue;
            String id = (String) wf.get("id");
            if (id == null || id.isBlank()) continue;
            if (templateRegistry.containsKey(id)) {
                log.debug("Workflow template '{}' already registered — skipping", id);
                continue;
            }

            List<DurableWorkflowEngine.StepDefinition> steps = materializeSteps(id, wf);
            templateRegistry.put(id, steps);
            log.info("Workflow template '{}' ({} steps) registered", id, steps.size());
            loaded++;
        }

        log.info("LifecycleWorkflowService: {} template(s) loaded from '{}'", loaded, TEMPLATES_RESOURCE);
        return loaded;
    }

    // ─── Execution ────────────────────────────────────────────────────────────

    /**
     * Starts a new workflow run for the given template.
     *
     * @param templateId registered template ID (e.g. {@code "new-feature"})
     * @param tenantId   the tenant initiating the run
     * @param variables  initial context variables (may be empty or {@code null})
     * @return execution handle containing the run ID and result Promise
     * @throws IllegalArgumentException if the template is not registered
     * @throws IllegalStateException    if the template has no steps
     */
    public DurableWorkflowEngine.WorkflowExecution startWorkflow(
            @NotNull String templateId,
            @NotNull String tenantId,
            Map<String, Object> variables) {

        List<DurableWorkflowEngine.StepDefinition> steps = templateRegistry.get(templateId);
        if (steps == null) {
            throw new IllegalArgumentException("Unknown workflow template: " + templateId);
        }
        if (steps.isEmpty()) {
            throw new IllegalStateException("Template '" + templateId + "' has no steps defined");
        }

        String runId = templateId + "-" + UUID.randomUUID();
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(runId, tenantId);
        ctx.setVariable("templateId", templateId);
        ctx.setVariable("tenantId", tenantId);
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }

        log.info("Starting workflow run '{}' for template '{}' (tenant={})", runId, templateId, tenantId);
        DurableWorkflowEngine.WorkflowExecution execution = engine.submit(runId, ctx, steps);
        runRegistry.put(runId, execution.run());
        return execution;
    }

    /**
     * Returns the run state for the given run ID.
     *
     * @param runId run ID returned by {@link #startWorkflow}
     * @return optional run state
     */
    public Optional<DurableWorkflowEngine.WorkflowRun> getRunStatus(@NotNull String runId) {
        return Optional.ofNullable(runRegistry.get(runId));
    }

    /**
     * Returns the set of all registered template IDs.
     *
     * @return immutable set
     */
    public Set<String> registeredTemplates() {
        return Collections.unmodifiableSet(templateRegistry.keySet());
    }

    /**
     * Returns true if the given template ID is registered.
     *
     * @param templateId template to check
     * @return true if registered
     */
    public boolean isRegistered(@NotNull String templateId) {
        return templateRegistry.containsKey(templateId);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<DurableWorkflowEngine.StepDefinition> materializeSteps(
            String templateId, Map<?, ?> template) {

        Object stepsObj = template.get("steps");
        if (!(stepsObj instanceof List<?> stepList)) {
            log.warn("Template '{}' has no steps", templateId);
            return List.of();
        }

        List<DurableWorkflowEngine.StepDefinition> defs = new ArrayList<>();
        for (Object stepItem : stepList) {
            if (!(stepItem instanceof Map<?, ?> rawStep)) continue;
            String stepId   = (String) rawStep.get("id");
            Object rawType  = rawStep.get("type");
            String stepType = rawType instanceof String s ? s : "passthrough";
            Map<String, String> config = rawStep.get("config") instanceof Map<?, ?> cfgMap
                    ? (Map<String, String>) cfgMap
                    : Map.of();

            if (stepId == null || stepId.isBlank()) continue;

            WorkflowStep step = resolveStep(templateId, stepId, stepType, config);
            DurableWorkflowEngine.StepDefinition def =
                    DurableWorkflowEngine.StepDefinition.of(stepId, step)
                            .withRetries(2, Duration.ofSeconds(2))
                            .withTimeout(Duration.ofMinutes(5));
            defs.add(def);
        }
        return defs;
    }

    /**
     * Maps a step type string to a concrete {@link WorkflowStep} implementation.
     *
     * <p>Extend this switch to support additional step types.
     */
    private WorkflowStep resolveStep(
            String templateId, String stepId, String stepType, Map<String, String> config) {
        return switch (stepType) {

            case "lifecycle-phase-advance" -> new WorkflowStep() {
                @Override
                public @NotNull Promise<WorkflowContext> execute(@NotNull WorkflowContext ctx) {
                    String from = config.getOrDefault("fromPhase", "UNKNOWN");
                    String to   = config.getOrDefault("toPhase",   "UNKNOWN");
                    ctx.setVariable("step.id",        stepId);
                    ctx.setVariable("step.type",      stepType);
                    ctx.setVariable("step.fromPhase", from);
                    ctx.setVariable("step.toPhase",   to);
                    log.debug("[{}] lifecycle-phase-advance '{}': {} → {}", templateId, stepId, from, to);
                    return Promise.of(ctx);
                }
            };

            case "lifecycle-gate-check" -> new WorkflowStep() {
                @Override
                public @NotNull Promise<WorkflowContext> execute(@NotNull WorkflowContext ctx) {
                    String phase = config.getOrDefault("phase", "UNKNOWN");
                    ctx.setVariable("step.id",    stepId);
                    ctx.setVariable("step.type",  stepType);
                    ctx.setVariable("gate.phase", phase);
                    log.debug("[{}] lifecycle-gate-check '{}' for phase '{}'", templateId, stepId, phase);
                    return Promise.of(ctx);
                }
            };

            default -> new WorkflowStep() {
                @Override
                public @NotNull Promise<WorkflowContext> execute(@NotNull WorkflowContext ctx) {
                    ctx.setVariable("step.id",   stepId);
                    ctx.setVariable("step.type", stepType);
                    log.debug("[{}] Passthrough step '{}' (type='{}')", templateId, stepId, stepType);
                    return Promise.of(ctx);
                }
            };
        };
    }
}
