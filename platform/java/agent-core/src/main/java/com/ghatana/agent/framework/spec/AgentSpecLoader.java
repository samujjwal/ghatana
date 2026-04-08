/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.core.template.TemplateContext;
import com.ghatana.core.template.YamlTemplateEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads the <b>complete</b> {@link AgentSpec} from agent-spec YAML files, covering
 * all 18 sections of the canonical {@code agent-spec.md} schema.
 *
 * <h2>Loading Pipeline</h2>
 * <pre>
 *   agent.yaml
 *       │
 *       ▼  YamlTemplateEngine.renderWithInheritance()
 *   resolved YAML (extends merged, {{ vars }} substituted)
 *       │
 *       ▼  Jackson ObjectMapper
 *   AgentSpecDto (mutable DTO capturing all 18 spec sections)
 *       │
 *       ▼  AgentSpecLoader.materialize()
 *   AgentSpec (immutable, fully-typed POJO)
 *       │
 *       ▼  AgentSpecLoader.extractDefinition()
 *   AgentDefinition (runtime-config subset)
 * </pre>
 *
 * <h2>Relationship to AgentDefinitionLoader</h2>
 * <p>{@code AgentSpecLoader} is a superset of
 * {@link com.ghatana.agent.framework.loader.AgentDefinitionLoader}. Use it when
 * you need the full spec (governance, reasoning profile, memory model, evaluation…).
 * Use {@code AgentDefinitionLoader} when only the runtime config subset is needed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentSpecLoader loader = new AgentSpecLoader();
 *
 * // Load full spec from file:
 * AgentSpec spec = loader.load(Path.of("agents/fraud-detector.yaml"));
 *
 * // Extract only the runtime definition:
 * AgentDefinition def = loader.extractDefinition(spec);
 *
 * // Load all specs in a directory:
 * List<AgentSpec> all = loader.loadFromDirectory(Path.of("agents/"));
 * }</pre>
 *
 * @see AgentSpec
 * @see GovernancePolicyRef
 *
 * @doc.type class
 * @doc.purpose Loads the complete AgentSpec POJO from agent-spec YAML (all 18 sections)
 * @doc.layer platform
 * @doc.pattern Factory, Strategy
 * @doc.gaa.lifecycle perceive
 */
public final class AgentSpecLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentSpecLoader.class);

    private final YamlTemplateEngine templateEngine;
    private final ObjectMapper yamlMapper;
    private final TemplateContext context;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates a loader with an explicit {@link TemplateContext} for
     * {@code {{ varName }}} substitution.
     *
     * @param context variable bindings
     */
    public AgentSpecLoader(@NotNull TemplateContext context) {
        this.context        = Objects.requireNonNull(context, "context must not be null");
        this.templateEngine = new YamlTemplateEngine();
        this.yamlMapper     = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Creates a loader with an empty template context (suitable for YAML files
     * with no {@code {{ … }}} placeholders).
     */
    public AgentSpecLoader() {
        this(TemplateContext.empty());
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Loads the complete {@link AgentSpec} from a YAML file.
     *
     * @param yamlFile path to the agent spec YAML file
     * @return fully-materialised {@link AgentSpec}
     * @throws IOException           if the file cannot be read
     * @throws IllegalStateException if required fields are missing
     */
    @NotNull
    public AgentSpec load(@NotNull Path yamlFile) throws IOException {
        Objects.requireNonNull(yamlFile, "yamlFile must not be null");
        log.debug("Loading AgentSpec from {}", yamlFile);
        String rendered = templateEngine.renderWithInheritance(yamlFile, context);
        AgentSpecDto dto = yamlMapper.readValue(rendered, AgentSpecDto.class);
        return materialize(dto, yamlFile.toString());
    }

    /**
     * Loads a complete {@link AgentSpec} from a raw YAML string.
     *
     * @param rawYaml raw agent spec YAML content
     * @return fully-materialised {@link AgentSpec}
     * @throws IOException           if parsing fails
     * @throws IllegalStateException if required fields are missing
     */
    @NotNull
    public AgentSpec loadFromString(@NotNull String rawYaml) throws IOException {
        Objects.requireNonNull(rawYaml, "rawYaml must not be null");
        String rendered = templateEngine.render(rawYaml, context);
        AgentSpecDto dto = yamlMapper.readValue(rendered, AgentSpecDto.class);
        return materialize(dto, "<string>");
    }

    /**
     * Scans a filesystem directory for all {@code *.yaml} / {@code *.yml} files
     * and loads each as an {@link AgentSpec}.
     *
     * <p>Files that fail to parse are logged as warnings and skipped.
     *
     * @param directory directory to scan (non-recursive)
     * @return unmodifiable list of loaded specs
     * @throws IOException if the directory cannot be read
     */
    @NotNull
    public List<AgentSpec> loadFromDirectory(@NotNull Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IOException("Not a directory: " + directory);
        }
        List<AgentSpec> results = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                  .sorted()
                  .forEach(p -> {
                      try {
                          results.add(load(p));
                          log.debug("Loaded AgentSpec from {}", p.getFileName());
                      } catch (Exception e) {
                          log.warn("Skipping agent spec YAML '{}': {}", p.getFileName(), e.getMessage());
                      }
                  });
        }
        log.info("Loaded {} AgentSpec(s) from {}", results.size(), directory);
        return Collections.unmodifiableList(results);
    }

    /**
     * Extracts the runtime {@link AgentDefinition} subset from a complete {@link AgentSpec}.
     *
     * <p>The runtime definition captures the fields consumed by agent base classes at
     * invocation time (type, I/O contracts, tools, constraints). Use the full
     * {@code AgentSpec} for governance, learning, evaluation, and observability.
     *
     * @param spec the full spec
     * @return runtime definition subset
     */
    @NotNull
    public AgentDefinition extractDefinition(@NotNull AgentSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        AgentSpec.SpecMetadata meta = spec.getMetadata();
        AgentSpec.SpecIdentity id   = spec.getIdentity();

        AgentDefinition.Builder b = AgentDefinition.builder()
                .id(meta.id())
                .version(meta.version())
                .name(meta.name())
                .description(meta.description())
                .type(id.agentType())
                .subtype(id.agentSubtype())
                .determinism(id.determinismGuarantee())
                .stateMutability(id.stateMutability())
                .failureMode(id.failureMode());

        // Capabilities from spec capabilities section
        if (spec.getCapabilities() != null) {
            spec.getCapabilities().declaredCapabilities().forEach(cap -> b.addCapability(cap.id()));
        }

        // Reasoning profile → systemPrompt + maxTokens + temperature from LLM reasoner
        if (spec.getReasoningProfile() != null) {
            spec.getReasoningProfile().reasonerPortfolio().stream()
                .filter(r -> "llm".equalsIgnoreCase(r.type()))
                .findFirst()
                .ifPresent(r -> {
                    // engine reference captured as a label
                    if (r.engine() != null) b.label("llm.engine", r.engine());
                });
        }

        // Identity supplemental fields → labels
        b.label("autonomyLevel", id.autonomyLevel())
         .label("criticality", id.criticality());

        // Interfaces → I/O contracts
        // IOContract.format defaults to "JSON" since InterfacePort has no serialization-format field;
        // authors who need PROTOBUF or AVRO should set it explicitly in the AgentDefinition directly.
        if (spec.getInterfaces() != null) {
            spec.getInterfaces().inputs().stream().findFirst().ifPresent(port ->
                b.inputContract(new AgentDefinition.IOContract(
                        port.name(), deriveFormat(port.schemaRef()), port.schemaRef()))
            );
            spec.getInterfaces().outputs().stream().findFirst().ifPresent(port ->
                b.outputContract(new AgentDefinition.IOContract(
                        port.name(), deriveFormat(port.schemaRef()), port.schemaRef()))
            );
        }

        // Tools and resources
        if (spec.getToolsAndResources() != null) {
            spec.getToolsAndResources().tools().forEach(toolMap -> {
                String toolId = toolMap.getOrDefault("id", toolMap.get("name")) != null
                        ? toolMap.getOrDefault("id", toolMap.get("name")).toString() : null;
                String purpose = toolMap.get("purpose") != null ? toolMap.get("purpose").toString() : "";
                if (toolId != null) {
                    b.addTool(new AgentDefinition.ToolDeclaration(toolId, purpose, Map.of()));
                }
            });
        }

        // Execution model → timeout, maxRetries
        if (spec.getExecutionModel() != null) {
            Map<String, Object> tp = spec.getExecutionModel().timeoutPolicy();
            if (tp != null) {
                Object hard = tp.get("hardTimeoutMs");
                if (hard instanceof Number num) {
                    b.timeout(Duration.ofMillis(num.longValue()));
                }
            }
            Map<String, Object> rp = spec.getExecutionModel().retryPolicy();
            if (rp != null) {
                Object max = rp.get("maxAttempts");
                if (max instanceof Number num) {
                    b.maxRetries(num.intValue());
                }
            }
        }

        // Governance → policy labels + cost cap from riskProfile
        if (spec.getGovernance() != null) {
            spec.getGovernance().policyRefs()
                .forEach(ref -> b.label("gov.policy." + ref.id().replace(".", "_"), ref.id()));
            Map<String, Object> riskProfile = spec.getGovernance().riskProfile();
            if (riskProfile != null) {
                Object costCap = riskProfile.get("maxCostPerCall");
                if (costCap instanceof Number num) {
                    b.maxCostPerCall(num.doubleValue());
                }
            }
        }

        // Learning model → label
        if (spec.getLearningModel() != null) {
            b.label("learningLevel", spec.getLearningModel().learningLevel());
        }

        // Meta labels from spec metadata
        b.label("namespace", meta.namespace())
         .label("status", meta.status());

        return b.build();
    }

    // ─── Materialisation ──────────────────────────────────────────────────────

    @NotNull
    private static final Set<String> SUPPORTED_SPEC_VERSIONS = Set.of("1.0.0", "2.0.0");

    private AgentSpec materialize(@NotNull AgentSpecDto dto, @NotNull String source) {
        // ── Spec version guard ────────────────────────────────────────────────
        String rawSpecVersion = dto.agentSpecVersion != null ? dto.agentSpecVersion : "1.0.0";
        if (!SUPPORTED_SPEC_VERSIONS.contains(rawSpecVersion)) {
            throw new UnsupportedSpecVersionException(rawSpecVersion,
                    String.join(", ", SUPPORTED_SPEC_VERSIONS));
        }

        // ── Metadata ─────────────────────────────────────────────────────────
        MetadataDto m = dto.metadata;
        if (m == null) throw new IllegalStateException("AgentSpec from '" + source + "' is missing 'metadata' section");
        if (m.id == null || m.id.isBlank())
            throw new IllegalStateException("AgentSpec from '" + source + "' is missing metadata.id");

        AgentSpec.SpecMetadata metadata = new AgentSpec.SpecMetadata(
                m.id,
                nvl(m.name, m.id),
                nvl(m.namespace, "default"),
                nvl(m.version, "1.0.0"),
                nvl(m.status, "active"),
                m.owners   != null ? m.owners   : List.of(),
                m.tags     != null ? m.tags     : List.of(),
                nvl(m.summary, ""),
                m.description
        );

        // ── Identity ──────────────────────────────────────────────────────────
        IdentityDto iDto = dto.identity;
        if (iDto == null) throw new IllegalStateException(
                "AgentSpec '" + m.id + "' from '" + source + "' is missing 'identity' section");

        AgentType agentType = resolveType(iDto.agentType, m.id, source);

        AgentSpec.SpecIdentity identity = new AgentSpec.SpecIdentity(
                agentType,
                iDto.agentSubtype,
                nvlList(iDto.roles),
                nvlMaps(iDto.personas),
                nvl(iDto.criticality, "medium"),
                nvl(iDto.autonomyLevel, "semi-autonomous"),
                resolveDeterminism(iDto.determinismGuarantee, agentType),
                resolveStateMutability(iDto.stateMutability, agentType),
                resolveFailureMode(iDto.failureMode, agentType)
        );

        // ── Purpose model ─────────────────────────────────────────────────────
        AgentSpec.PurposeModel purposeModel = null;
        if (dto.purposeModel != null) {
            PurposeModelDto p = dto.purposeModel;
            purposeModel = new AgentSpec.PurposeModel(
                    nvl(p.mission, ""),
                    nvlList(p.goals),
                    nvlList(p.nonGoals),
                    nvlMaps(p.successCriteria)
            );
        }

        // ── Scope ─────────────────────────────────────────────────────────────
        AgentSpec.SpecScope scope = null;
        if (dto.scope != null) {
            scope = new AgentSpec.SpecScope(
                    nvlList(dto.scope.domains),
                    nvlList(dto.scope.supportedEntities),
                    nvlList(dto.scope.boundaries)
            );
        }

        // ── Capabilities ──────────────────────────────────────────────────────
        AgentSpec.SpecCapabilities capabilities = null;
        if (dto.capabilities != null) {
            List<AgentSpec.CapabilityDeclaration> decls = new ArrayList<>();
            if (dto.capabilities.declaredCapabilities != null) {
                for (CapDeclDto cap : dto.capabilities.declaredCapabilities) {
                    decls.add(new AgentSpec.CapabilityDeclaration(
                            nvl(cap.id, ""),
                            nvl(cap.name, cap.id != null ? cap.id : ""),
                            cap.description,
                            nvlList(cap.inputTypes),
                            nvlList(cap.outputTypes),
                            cap.determinismLevel,
                            cap.requiresHumanApproval
                    ));
                }
            }
            capabilities = new AgentSpec.SpecCapabilities(
                    decls,
                    nvlList(dto.capabilities.capabilityDependencies),
                    nvlList(dto.capabilities.prohibitedCapabilities)
            );
        }

        // ── Reasoning profile ─────────────────────────────────────────────────
        AgentSpec.ReasoningProfile reasoningProfile = null;
        if (dto.reasoningProfile != null) {
            ReasoningProfileDto rp = dto.reasoningProfile;
            List<AgentSpec.ReasonerDeclaration> portfolio = new ArrayList<>();
            if (rp.reasonerPortfolio != null) {
                for (ReasonerDto r : rp.reasonerPortfolio) {
                    portfolio.add(new AgentSpec.ReasonerDeclaration(
                            nvl(r.id, ""),
                            nvl(r.type, ""),
                            r.purpose,
                            r.engine,
                            nvlList(r.invocationWhen)
                    ));
                }
            }
            AgentSpec.DeterminismProfile detProfile = null;
            if (rp.determinismProfile != null) {
                detProfile = new AgentSpec.DeterminismProfile(
                        nvlList(rp.determinismProfile.fullyDeterministicZones),
                        nvlList(rp.determinismProfile.boundedSpeculativeZones),
                        nvlList(rp.determinismProfile.nonDeterministicZones)
                );
            }
            AgentSpec.ConfidenceModel confModel = null;
            if (rp.confidenceModel != null) {
                confModel = new AgentSpec.ConfidenceModel(
                        nvl(rp.confidenceModel.scoreRange, "0.0-1.0"),
                        nvlList(rp.confidenceModel.sources),
                        rp.confidenceModel.autoApproveThreshold,
                        rp.confidenceModel.humanReviewThreshold,
                        rp.confidenceModel.rejectBelowThreshold
                );
            }
            reasoningProfile = new AgentSpec.ReasoningProfile(
                    nvl(rp.primaryReasoner, "llm"),
                    portfolio,
                    rp.reasoningStrategy,
                    detProfile,
                    confModel
            );
        }

        // ── Execution model ───────────────────────────────────────────────────
        AgentSpec.ExecutionModel executionModel = null;
        if (dto.executionModel != null) {
            ExecModelDto em = dto.executionModel;
            executionModel = new AgentSpec.ExecutionModel(
                    nvlList(em.invocationModes),
                    nvlList(em.lifecycleStates),
                    em.concurrencyModel,
                    em.retryPolicy,
                    em.timeoutPolicy,
                    em.compensationPolicy
            );
        }

        // ── Interfaces ────────────────────────────────────────────────────────
        AgentSpec.SpecInterfaces interfaces = null;
        if (dto.interfaces != null) {
            InterfacesDto ifc = dto.interfaces;
            interfaces = new AgentSpec.SpecInterfaces(
                    matPorts(ifc.inputs),
                    matPorts(ifc.outputs),
                    nvlList(ifc.eventsConsumed),
                    nvlList(ifc.eventsProduced),
                    nvlMaps(ifc.apiContracts),
                    nvlList(ifc.supportedModalities)
            );
        }

        // ── Memory model ──────────────────────────────────────────────────────
        AgentSpec.MemoryModel memoryModel = null;
        if (dto.memoryModel != null) {
            MemoryModelDto mm = dto.memoryModel;
            List<AgentSpec.MemoryBinding> bindings = new ArrayList<>();
            if (mm.memoryBindings != null) {
                for (MemoryBindingDto b2 : mm.memoryBindings) {
                    bindings.add(new AgentSpec.MemoryBinding(
                            nvl(b2.memoryType, ""),
                            nvl(b2.access, "read")));
                }
            }
            Map<String, Object> writePolicies = mm.writePolicies != null ? mm.writePolicies : Map.of();
            memoryModel = new AgentSpec.MemoryModel(
                    bindings,
                    nvlList(mm.memoryTypes),
                    nvlList(mm.readStrategies),
                    writePolicies,
                    nvlList(mm.consolidationRules),
                    mm.retentionPolicy
            );
        }

        // ── Tools and resources ───────────────────────────────────────────────
        AgentSpec.ToolsAndResources toolsAndResources = null;
        if (dto.toolsAndResources != null) {
            ToolsDto t = dto.toolsAndResources;
            toolsAndResources = new AgentSpec.ToolsAndResources(
                    nvl3(t.tools),
                    nvlMaps(t.resources),
                    t.toolSelectionPolicy
            );
        }

        // ── Governance ────────────────────────────────────────────────────────
        AgentSpec.GovernanceSpec governance = null;
        if (dto.governance != null) {
            GovernanceDto g = dto.governance;
            List<GovernancePolicyRef> policyRefs = new ArrayList<>();
            if (g.policyRefs != null) {
                for (Object ref : g.policyRefs) {
                    if (ref instanceof String s) {
                        policyRefs.add(GovernancePolicyRef.of(s));
                    } else if (ref instanceof Map<?, ?> refMap) {
                        Object refId = refMap.get("id");
                        Object refDesc = refMap.get("description");
                        Object refMode = refMap.get("enforcementMode");
                        if (refId != null) {
                            policyRefs.add(new GovernancePolicyRef(
                                    refId.toString(),
                                    refDesc != null ? refDesc.toString() : null,
                                    refMode != null ? refMode.toString() : null
                            ));
                        }
                    }
                }
            }
            governance = new AgentSpec.GovernanceSpec(
                    policyRefs,
                    g.approvals,
                    g.dataHandling != null ? g.dataHandling : Map.of(),
                    g.riskProfile  != null ? g.riskProfile  : Map.of()
            );
        }

        // ── Learning model ────────────────────────────────────────────────────
        AgentSpec.LearningModel learningModel = null;
        if (dto.learningModel != null) {
            LearningModelDto lm = dto.learningModel;
            learningModel = new AgentSpec.LearningModel(
                    nvl(lm.learningLevel, "L0"),
                    nvlList(lm.adaptationTargets),
                    nvlList(lm.learningSources),
                    lm.driftControls,
                    lm.rollbackPolicy,
                    lm.promptVersioning
            );
        }

        // ── Evaluation ────────────────────────────────────────────────────────
        AgentSpec.EvaluationModel evaluation = null;
        if (dto.evaluation != null) {
            EvalDto ev = dto.evaluation;
            evaluation = new AgentSpec.EvaluationModel(
                    nvlList(ev.evaluationSpecRefs),
                    nvlList(ev.onlineMetrics),
                    nvlList(ev.offlineMetrics),
                    ev.releaseGates
            );
        }

        // ── Observability ─────────────────────────────────────────────────────
        AgentSpec.ObservabilitySpec observability = null;
        if (dto.observability != null) {
            ObservabilityDto obs = dto.observability;
            observability = new AgentSpec.ObservabilitySpec(
                    obs.traceEnabled,
                    nvlList(obs.loggedArtifacts),
                    obs.auditMode,
                    nvlList(obs.alerts)
            );
        }

        // ── Interoperability ──────────────────────────────────────────────────
        AgentSpec.InteroperabilitySpec interoperability = null;
        if (dto.interoperability != null) {
            InteropDto interop = dto.interoperability;
            interoperability = new AgentSpec.InteroperabilitySpec(
                    interop.mcp,
                    interop.agentToAgent,
                    interop.compatibility
            );
        }

        // ── Security ──────────────────────────────────────────────────────────
        AgentSpec.SecuritySpec security = null;
        if (dto.security != null) {
            SecurityDto sec = dto.security;
            security = new AgentSpec.SecuritySpec(
                    sec.authn     != null ? sec.authn     : Map.of(),
                    sec.authz     != null ? sec.authz     : Map.of(),
                    sec.secretsHandling,
                    sec.networkPolicy
            );
        }

        // ── Deployment ────────────────────────────────────────────────────────
        AgentSpec.DeploymentSpec deployment = null;
        if (dto.deployment != null) {
            DeploymentDto dep = dto.deployment;
            deployment = new AgentSpec.DeploymentSpec(
                    dep.runtimeClass,
                    dep.scalingModel,
                    dep.localityConstraints,
                    nvlList(dep.dependencies),
                    dep.documentation
            );
        }

        return AgentSpec.builder()
                .agentSpecVersion(nvl(dto.agentSpecVersion, "1.0.0"))
                .metadata(metadata)
                .identity(identity)
                .purposeModel(purposeModel)
                .scope(scope)
                .capabilities(capabilities)
                .reasoningProfile(reasoningProfile)
                .executionModel(executionModel)
                .interfaces(interfaces)
                .memoryModel(memoryModel)
                .toolsAndResources(toolsAndResources)
                .governance(governance)
                .learningModel(learningModel)
                .evaluation(evaluation)
                .observability(observability)
                .interoperability(interoperability)
                .security(security)
                .deployment(deployment)
                .build();
    }

    // ─── extractDefinition helpers ────────────────────────────────────────────

    /**
     * Derives a serialization format hint from a schema reference URI.
     * Defaults to {@code "JSON"} when no recognizable format marker is found.
     */
    @NotNull
    private static String deriveFormat(@Nullable String schemaRef) {
        if (schemaRef != null) {
            String lower = schemaRef.toLowerCase();
            if (lower.contains(".proto") || lower.contains("protobuf")) return "PROTOBUF";
            if (lower.contains("avro") || lower.contains(".avsc")) return "AVRO";
            if (lower.contains("avro"))  return "AVRO";
        }
        return "JSON";
    }

    // ─── Type resolution helpers ──────────────────────────────────────────────

    @NotNull
    private AgentType resolveType(@Nullable String raw, @NotNull String id, @NotNull String source) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "AgentSpec '" + id + "' from '" + source + "' is missing identity.agentType");
        }
        // Normalise YAML aliases from pre-v2.1 YAMLs
        String normalised = switch (raw.toLowerCase()) {
            case "llm" ->
                    AgentType.PROBABILISTIC.name(); // deprecated alias: llm → PROBABILISTIC
            case "rule-based", "rule_based", "policy", "pattern" ->
                    AgentType.DETERMINISTIC.name(); // deprecated aliases for deterministic agents
            case "stream-processor", "stream_processor" ->
                    AgentType.STREAM_PROCESSOR.name();
            default -> raw.toUpperCase();
        };
        try {
            return AgentType.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "AgentSpec '" + id + "' has unknown identity.agentType '" + raw
                    + "'. Valid values: " + List.of(AgentType.values()), e);
        }
    }

    @NotNull
    private DeterminismGuarantee resolveDeterminism(
            @Nullable String raw, @NotNull AgentType type) {
        if (raw != null && !raw.isBlank()) {
            try {
                return DeterminismGuarantee.valueOf(raw.toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ignored) { }
        }
        // Sensible defaults per type
        return switch (type) {
            case DETERMINISTIC, REACTIVE -> DeterminismGuarantee.FULL;
            case STREAM_PROCESSOR       -> DeterminismGuarantee.CONFIG_SCOPED;
            case ADAPTIVE               -> DeterminismGuarantee.EVENTUAL;
            default                     -> DeterminismGuarantee.NONE;
        };
    }

    @NotNull
    private StateMutability resolveStateMutability(
            @Nullable String raw, @NotNull AgentType type) {
        if (raw != null && !raw.isBlank()) {
            try {
                return StateMutability.valueOf(raw.toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ignored) { }
        }
        return switch (type) {
            case DETERMINISTIC, REACTIVE, PROBABILISTIC -> StateMutability.STATELESS;
            case STREAM_PROCESSOR                       -> StateMutability.LOCAL_STATE;
            case PLANNING, ADAPTIVE, COMPOSITE         -> StateMutability.EXTERNAL_STATE;
            default                                    -> StateMutability.STATELESS;
        };
    }

    @NotNull
    private FailureMode resolveFailureMode(
            @Nullable String raw, @NotNull AgentType type) {
        if (raw != null && !raw.isBlank()) {
            try {
                return FailureMode.valueOf(raw.toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ignored) { }
        }
        return switch (type) {
            case DETERMINISTIC, REACTIVE -> FailureMode.FAIL_FAST;
            case PROBABILISTIC, HYBRID   -> FailureMode.FALLBACK;
            case STREAM_PROCESSOR        -> FailureMode.RETRY;
            case PLANNING                -> FailureMode.CIRCUIT_BREAKER;
            default                     -> FailureMode.FAIL_FAST;
        };
    }

    // ─── Null-safe helpers ────────────────────────────────────────────────────

    @NotNull private static String nvl(@Nullable String s, @NotNull String dflt) {
        return (s != null && !s.isBlank()) ? s : dflt;
    }

    @NotNull private static <T> List<T> nvlList(@Nullable List<T> l) {
        return l != null ? l : List.of();
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nvl3(@Nullable List<?> l) {
        if (l == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> typed = new LinkedHashMap<>();
                m.forEach((k, v) -> typed.put(k.toString(), v));
                out.add(typed);
            }
        }
        return out;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> nvlMaps(@Nullable List<?> l) {
        if (l == null) return List.of();
        List<Map<String, String>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                Map<String, String> typed = new LinkedHashMap<>();
                m.forEach((k, v) -> typed.put(k.toString(), v != null ? v.toString() : ""));
                out.add(typed);
            }
        }
        return out;
    }

    @NotNull
    private static List<AgentSpec.InterfacePort> matPorts(@Nullable List<PortDto> ports) {
        if (ports == null) return List.of();
        List<AgentSpec.InterfacePort> out = new ArrayList<>();
        for (PortDto p : ports) {
            out.add(new AgentSpec.InterfacePort(
                    nvl(p.name, ""),
                    p.schemaRef,
                    p.required,
                    p.description));
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Jackson DTOs — one-to-one with agent-spec.md YAML structure
    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class AgentSpecDto {
        @JsonProperty("agentSpecVersion") @Nullable String agentSpecVersion;
        @JsonProperty("metadata")         @Nullable MetadataDto metadata;
        @JsonProperty("identity")         @Nullable IdentityDto identity;
        @JsonProperty("purposeModel")     @Nullable PurposeModelDto purposeModel;
        @JsonProperty("scope")            @Nullable ScopeDto scope;
        @JsonProperty("capabilities")     @Nullable CapabilitiesDto capabilities;
        @JsonProperty("reasoningProfile") @Nullable ReasoningProfileDto reasoningProfile;
        @JsonProperty("executionModel")   @Nullable ExecModelDto executionModel;
        @JsonProperty("interfaces")       @Nullable InterfacesDto interfaces;
        @JsonProperty("memoryModel")      @Nullable MemoryModelDto memoryModel;
        @JsonProperty("toolsAndResources") @Nullable ToolsDto toolsAndResources;
        @JsonProperty("governance")       @Nullable GovernanceDto governance;
        @JsonProperty("learningModel")    @Nullable LearningModelDto learningModel;
        @JsonProperty("evaluation")       @Nullable EvalDto evaluation;
        @JsonProperty("observability")    @Nullable ObservabilityDto observability;
        @JsonProperty("interoperability") @Nullable InteropDto interoperability;
        @JsonProperty("security")         @Nullable SecurityDto security;
        @JsonProperty("deployment")       @Nullable DeploymentDto deployment;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class MetadataDto {
        @JsonProperty("id")          @Nullable String id;
        @JsonProperty("name")        @Nullable String name;
        @JsonProperty("namespace")   @Nullable String namespace;
        @JsonProperty("version")     @Nullable String version;
        @JsonProperty("status")      @Nullable String status;
        @JsonProperty("owners")      @Nullable List<Map<String, String>> owners;
        @JsonProperty("tags")        @Nullable List<String> tags;
        @JsonProperty("summary")     @Nullable String summary;
        @JsonProperty("description") @Nullable String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class IdentityDto {
        @JsonProperty("agentType")           @Nullable String agentType;
        @JsonProperty("agentSubtype")        @Nullable String agentSubtype;
        @JsonProperty("roles")               @Nullable List<String> roles;
        @JsonProperty("personas")            @Nullable List<Map<String, String>> personas;
        @JsonProperty("criticality")         @Nullable String criticality;
        @JsonProperty("autonomyLevel")       @Nullable String autonomyLevel;
        @JsonProperty("determinismGuarantee") @Nullable String determinismGuarantee;
        @JsonProperty("stateMutability")     @Nullable String stateMutability;
        @JsonProperty("failureMode")         @Nullable String failureMode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class PurposeModelDto {
        @JsonProperty("mission")         @Nullable String mission;
        @JsonProperty("goals")           @Nullable List<String> goals;
        @JsonProperty("nonGoals")        @Nullable List<String> nonGoals;
        @JsonProperty("successCriteria") @Nullable List<Map<String, String>> successCriteria;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ScopeDto {
        @JsonProperty("domains")          @Nullable List<String> domains;
        @JsonProperty("supportedEntities") @Nullable List<String> supportedEntities;
        @JsonProperty("boundaries")        @Nullable List<String> boundaries;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CapabilitiesDto {
        @JsonProperty("declaredCapabilities")   @Nullable List<CapDeclDto> declaredCapabilities;
        @JsonProperty("capabilityDependencies") @Nullable List<String> capabilityDependencies;
        @JsonProperty("prohibitedCapabilities") @Nullable List<String> prohibitedCapabilities;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CapDeclDto {
        @JsonProperty("id")                   @Nullable String id;
        @JsonProperty("name")                 @Nullable String name;
        @JsonProperty("description")          @Nullable String description;
        @JsonProperty("inputTypes")           @Nullable List<String> inputTypes;
        @JsonProperty("outputTypes")          @Nullable List<String> outputTypes;
        @JsonProperty("determinismLevel")     @Nullable String determinismLevel;
        @JsonProperty("requiresHumanApproval")         boolean requiresHumanApproval;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ReasoningProfileDto {
        @JsonProperty("primaryReasoner")    @Nullable String primaryReasoner;
        @JsonProperty("reasonerPortfolio")  @Nullable List<ReasonerDto> reasonerPortfolio;
        @JsonProperty("reasoningStrategy")  @Nullable String reasoningStrategy;
        @JsonProperty("determinismProfile") @Nullable DeterminismProfileDto determinismProfile;
        @JsonProperty("confidenceModel")    @Nullable ConfidenceModelDto confidenceModel;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ReasonerDto {
        @JsonProperty("id")             @Nullable String id;
        @JsonProperty("type")           @Nullable String type;
        @JsonProperty("purpose")        @Nullable String purpose;
        @JsonProperty("engine")         @Nullable String engine;
        @JsonProperty("invocationWhen") @Nullable List<String> invocationWhen;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DeterminismProfileDto {
        @JsonProperty("fullyDeterministicZones")   @Nullable List<String> fullyDeterministicZones;
        @JsonProperty("boundedSpeculativeZones")   @Nullable List<String> boundedSpeculativeZones;
        @JsonProperty("nonDeterministicZones")     @Nullable List<String> nonDeterministicZones;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ConfidenceModelDto {
        @JsonProperty("scoreRange")             @Nullable String scoreRange;
        @JsonProperty("sources")                @Nullable List<String> sources;
        @JsonProperty("autoApproveThreshold")            double autoApproveThreshold;
        @JsonProperty("humanReviewThreshold")            double humanReviewThreshold;
        @JsonProperty("rejectBelowThreshold")            double rejectBelowThreshold;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ExecModelDto {
        @JsonProperty("invocationModes")    @Nullable List<String> invocationModes;
        @JsonProperty("lifecycleStates")    @Nullable List<String> lifecycleStates;
        @JsonProperty("concurrencyModel")   @Nullable Map<String, Object> concurrencyModel;
        @JsonProperty("retryPolicy")        @Nullable Map<String, Object> retryPolicy;
        @JsonProperty("timeoutPolicy")      @Nullable Map<String, Object> timeoutPolicy;
        @JsonProperty("compensationPolicy") @Nullable Map<String, Object> compensationPolicy;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class InterfacesDto {
        @JsonProperty("inputs")             @Nullable List<PortDto> inputs;
        @JsonProperty("outputs")            @Nullable List<PortDto> outputs;
        @JsonProperty("eventsConsumed")     @Nullable List<String> eventsConsumed;
        @JsonProperty("eventsProduced")     @Nullable List<String> eventsProduced;
        @JsonProperty("apiContracts")       @Nullable List<Map<String, String>> apiContracts;
        @JsonProperty("supportedModalities") @Nullable List<String> supportedModalities;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class PortDto {
        @JsonProperty("name")        @Nullable String name;
        @JsonProperty("schemaRef")   @Nullable String schemaRef;
        @JsonProperty("required")             boolean required;
        @JsonProperty("description") @Nullable String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class MemoryModelDto {
        @JsonProperty("memoryBindings")    @Nullable List<MemoryBindingDto> memoryBindings;
        @JsonProperty("memoryTypes")       @Nullable List<String> memoryTypes;
        @JsonProperty("readStrategies")    @Nullable List<String> readStrategies;
        @JsonProperty("writePolicies")     @Nullable Map<String, Object> writePolicies;
        @JsonProperty("consolidationRules") @Nullable List<String> consolidationRules;
        @JsonProperty("retentionPolicy")   @Nullable Map<String, String> retentionPolicy;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class MemoryBindingDto {
        @JsonProperty("memoryType") @Nullable String memoryType;
        @JsonProperty("access")     @Nullable String access;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ToolsDto {
        @JsonProperty("tools")              @Nullable List<?> tools;
        @JsonProperty("resources")          @Nullable List<Map<String,String>> resources;
        @JsonProperty("toolSelectionPolicy") @Nullable Map<String, Object> toolSelectionPolicy;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class GovernanceDto {
        @JsonProperty("policyRefs")   @Nullable List<Object> policyRefs;   // String or Map
        @JsonProperty("approvals")    @Nullable Map<String, Object> approvals;
        @JsonProperty("dataHandling") @Nullable Map<String, Object> dataHandling;
        @JsonProperty("riskProfile")  @Nullable Map<String, Object> riskProfile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class LearningModelDto {
        @JsonProperty("learningLevel")     @Nullable String learningLevel;
        @JsonProperty("adaptationTargets") @Nullable List<String> adaptationTargets;
        @JsonProperty("learningSources")   @Nullable List<String> learningSources;
        @JsonProperty("driftControls")     @Nullable Map<String, Object> driftControls;
        @JsonProperty("rollbackPolicy")    @Nullable Map<String, Object> rollbackPolicy;
        @JsonProperty("promptVersioning")  @Nullable Map<String, Object> promptVersioning;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class EvalDto {
        @JsonProperty("evaluationSpecRefs") @Nullable List<String> evaluationSpecRefs;
        @JsonProperty("onlineMetrics")      @Nullable List<String> onlineMetrics;
        @JsonProperty("offlineMetrics")     @Nullable List<String> offlineMetrics;
        @JsonProperty("releaseGates")       @Nullable Map<String, Object> releaseGates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ObservabilityDto {
        @JsonProperty("traceEnabled")    boolean traceEnabled;
        @JsonProperty("loggedArtifacts") @Nullable List<String> loggedArtifacts;
        @JsonProperty("auditMode")       @Nullable String auditMode;
        @JsonProperty("alerts")          @Nullable List<String> alerts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class InteropDto {
        @JsonProperty("mcp")           @Nullable Map<String, Object> mcp;
        @JsonProperty("agentToAgent")  @Nullable Map<String, Object> agentToAgent;
        @JsonProperty("compatibility") @Nullable Map<String, Object> compatibility;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class SecurityDto {
        @JsonProperty("authn")          @Nullable Map<String, Object> authn;
        @JsonProperty("authz")          @Nullable Map<String, Object> authz;
        @JsonProperty("secretsHandling") @Nullable Map<String, Object> secretsHandling;
        @JsonProperty("networkPolicy")  @Nullable Map<String, Object> networkPolicy;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DeploymentDto {
        @JsonProperty("runtimeClass")         @Nullable String runtimeClass;
        @JsonProperty("scalingModel")         @Nullable Map<String, Object> scalingModel;
        @JsonProperty("localityConstraints")  @Nullable Map<String, Object> localityConstraints;
        @JsonProperty("dependencies")         @Nullable List<String> dependencies;
        @JsonProperty("documentation")        @Nullable Map<String, String> documentation;
    }
}
