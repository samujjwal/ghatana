/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.core.template.TemplateContext;
import com.ghatana.core.template.YamlTemplateEngine;
import com.ghatana.platform.workflow.DefaultWorkflowContext;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import io.activej.promise.Promise;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Materializes canonical YAPPC workflow templates from YAML into
 * {@link DurableWorkflowEngine} step registrations.
 *
 * <p><b>Startup flow</b>
 * <ol>
 *   <li>Reads {@code lifecycle-workflow-templates.yaml} from the classpath.</li>
 *   <li>For each {@link WorkflowTemplate}, converts its {@link WorkflowStepTemplate}s into
 *       {@link DurableWorkflowEngine.StepDefinition}s.</li>
 *   <li>Stores the definitions in an in-memory registry keyed by template ID.</li>
 *   <li>Callers invoke {@link #startWorkflow(String, String, Map)} to initiate a new run.</li>
 * </ol>
 *
 * <p><b>Step type resolution</b><br>
 * Step types are resolved at materialization time:
 * <ul>
 *   <li>{@code lifecycle-phase-advance} — updates context with phase transition metadata</li>
 *   <li>{@code lifecycle-gate-check} — records gate-check metadata in context</li>
 *   <li>Any other type — passthrough step (logs + forwards context unchanged)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Materializes YAML workflow templates into DurableWorkflowEngine instances
 * @doc.layer product
 * @doc.pattern Service, Bootstrapper
 * @doc.gaa.lifecycle perceive
 */
public class WorkflowMaterializer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMaterializer.class);

    /** Classpath location of the canonical workflow template definitions. */
    static final String TEMPLATES_RESOURCE = "lifecycle-workflow-templates.yaml";

    private final DurableWorkflowEngine engine;
    private final ObjectMapper yamlMapper;
    private final YamlTemplateEngine templateEngine;

    /** Registry: templateId → list of materialized step definitions. */
    private final Map<String, List<DurableWorkflowEngine.StepDefinition>> templateRegistry =
            new ConcurrentHashMap<>();

    /** Registry: runId → WorkflowRun for status queries. */
    private final Map<String, DurableWorkflowEngine.WorkflowRun> runRegistry =
            new ConcurrentHashMap<>();

    /**
     * Creates a new {@code WorkflowMaterializer}.
     *
     * @param engine         the durable workflow engine to submit runs to
     * @param templateEngine the YAML template engine for variable substitution
     */
    public WorkflowMaterializer(DurableWorkflowEngine engine, YamlTemplateEngine templateEngine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine");
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    // -------------------------------------------------------------------------
    // Materialization
    // -------------------------------------------------------------------------

    /**
     * Reads {@code lifecycle-workflow-templates.yaml} from the classpath and materializes
     * all workflow templates into the registry.
     *
     * <p>Idempotent — subsequent calls for already-registered templates are skipped.
     *
     * @return number of templates loaded
     */
    public int materializeAll() {
        InputStream yaml = getClass().getClassLoader().getResourceAsStream(TEMPLATES_RESOURCE);
        if (yaml == null) {
            log.warn("No '{}' found on classpath — 0 workflow templates loaded", TEMPLATES_RESOURCE);
            return 0;
        }

        CanonicalWorkflowsManifest manifest;
        try {
            String rawYaml = new String(yaml.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String rendered = templateEngine.render(rawYaml, TemplateContext.empty());
            manifest = yamlMapper.readValue(rendered, CanonicalWorkflowsManifest.class);
        } catch (Exception e) {
            log.error("Failed to parse '{}': {}", TEMPLATES_RESOURCE, e.getMessage(), e);
            return 0;
        }

        int loaded = 0;
        for (WorkflowTemplate template : manifest.getWorkflows()) {
            if (templateRegistry.containsKey(template.getId())) {
                log.debug("Workflow template '{}' already registered — skipping", template.getId());
                continue;
            }
            List<DurableWorkflowEngine.StepDefinition> steps = materializeSteps(template);
            templateRegistry.put(template.getId(), steps);
            log.info("Workflow template '{}' ({} steps) registered", template.getId(), steps.size());
            loaded++;
        }

        log.info("WorkflowMaterializer: {} template(s) loaded from '{}'", loaded, TEMPLATES_RESOURCE);
        return loaded;
    }

    private List<DurableWorkflowEngine.StepDefinition> materializeSteps(WorkflowTemplate template) {
        List<DurableWorkflowEngine.StepDefinition> defs = new ArrayList<>();
        for (WorkflowStepTemplate stepTemplate : template.getSteps()) {
            WorkflowStep step = resolveStep(stepTemplate);
            DurableWorkflowEngine.StepDefinition def =
                    DurableWorkflowEngine.StepDefinition.of(stepTemplate.getId(), step)
                            .withRetries(3, Duration.ofSeconds(2))
                            .withTimeout(Duration.ofMinutes(5));
            defs.add(def);
        }
        return defs;
    }

    /**
     * Maps a step template type to a {@link WorkflowStep} implementation.
     *
     * <p>Extend this method to register new step type handlers.
     *
     * @param stepTemplate the step template to resolve
     * @return a {@link WorkflowStep} for the given type
     */
    private WorkflowStep resolveStep(WorkflowStepTemplate stepTemplate) {
        return switch (stepTemplate.getType()) {
            case "lifecycle-phase-advance" -> new WorkflowStep() {
                @Override
                public @org.jetbrains.annotations.NotNull Promise<WorkflowContext> execute(@org.jetbrains.annotations.NotNull WorkflowContext ctx) {
                    Map<String, String> cfg = stepTemplate.getConfig();
                    ctx.setVariable("step.id", stepTemplate.getId());
                    ctx.setVariable("step.type", stepTemplate.getType());
                    ctx.setVariable("step.fromPhase", cfg.getOrDefault("fromPhase", "UNKNOWN"));
                    ctx.setVariable("step.toPhase",   cfg.getOrDefault("toPhase",   "UNKNOWN"));
                    log.debug("lifecycle-phase-advance step '{}': {} → {}",
                            stepTemplate.getId(),
                            cfg.getOrDefault("fromPhase", "?"),
                            cfg.getOrDefault("toPhase", "?"));
                    return Promise.of(ctx);
                }
            };
            case "lifecycle-gate-check" -> new WorkflowStep() {
                @Override
                public @org.jetbrains.annotations.NotNull Promise<WorkflowContext> execute(@org.jetbrains.annotations.NotNull WorkflowContext ctx) {
                    Map<String, String> cfg = stepTemplate.getConfig();
                    ctx.setVariable("step.id",    stepTemplate.getId());
                    ctx.setVariable("step.type",  stepTemplate.getType());
                    ctx.setVariable("gate.phase", cfg.getOrDefault("phase", "UNKNOWN"));
                    log.debug("lifecycle-gate-check step '{}' for phase '{}'",
                            stepTemplate.getId(), cfg.getOrDefault("phase", "?"));
                    return Promise.of(ctx);
                }
            };
            default -> new WorkflowStep() {
                @Override
                public @org.jetbrains.annotations.NotNull Promise<WorkflowContext> execute(@org.jetbrains.annotations.NotNull WorkflowContext ctx) {
                    ctx.setVariable("step.id",   stepTemplate.getId());
                    ctx.setVariable("step.type", stepTemplate.getType());
                    log.debug("Passthrough step '{}' (type='{}')",
                            stepTemplate.getId(), stepTemplate.getType());
                    return Promise.of(ctx);
                }
            };
        };
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    /**
     * Starts a new workflow run for the given template.
     *
     * @param templateId the registered template ID
     * @param tenantId   the tenant initiating the run
     * @param variables  initial context variables (may be empty)
     * @return the {@link DurableWorkflowEngine.WorkflowExecution} for the new run
     * @throws IllegalArgumentException if the template is not registered
     */
    public DurableWorkflowEngine.WorkflowExecution startWorkflow(
            String templateId,
            String tenantId,
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
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }

        log.info("Starting workflow run '{}' for template '{}' (tenant={})",
                runId, templateId, tenantId);
        DurableWorkflowEngine.WorkflowExecution execution = engine.submit(runId, ctx, steps);
        runRegistry.put(runId, execution.run());
        return execution;
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link DurableWorkflowEngine.WorkflowRun} for the given run ID, if known.
     *
     * @param runId the run ID returned by {@link #startWorkflow}
     * @return the run, or empty if not found
     */
    public Optional<DurableWorkflowEngine.WorkflowRun> getRunStatus(String runId) {
        return Optional.ofNullable(runRegistry.get(runId));
    }

    /**
     * Returns all registered template IDs.
     *
     * @return immutable set of template IDs
     */
    public java.util.Set<String> registeredTemplates() {
        return Collections.unmodifiableSet(templateRegistry.keySet());
    }
}
