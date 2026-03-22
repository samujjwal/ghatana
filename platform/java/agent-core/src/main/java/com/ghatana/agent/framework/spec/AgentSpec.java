/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.config.AgentDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Full declarative specification for a Ghatana agent, mapping 1:1 to the
 * {@code agent-spec.md} YAML schema (spec version 1.0.0).
 *
 * <p>An {@code AgentSpec} is the complete "author-facing" blueprint. It covers
 * every section of the canonical spec: metadata, identity, purposeModel, scope,
 * capabilities, reasoningProfile, executionModel, interfaces, memoryModel,
 * toolsAndResources, governance, learningModel, evaluation, observability,
 * interoperability, security, and deployment.
 *
 * <p>The runtime subset is extracted into an {@link AgentDefinition} via
 * {@link AgentSpecLoader#extractDefinition(AgentSpec)}.
 *
 * @see AgentSpecLoader
 * @see AgentDefinition
 *
 * @doc.type class
 * @doc.purpose Full declarative agent specification (complete agent-spec.md mapping)
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class AgentSpec {

    // ─────────────────────────────────────────────────────────────────────────
    // Top-level envelope
    // ─────────────────────────────────────────────────────────────────────────

    private final String agentSpecVersion;
    private final SpecMetadata metadata;
    private final SpecIdentity identity;
    private final PurposeModel purposeModel;
    private final SpecScope scope;
    private final SpecCapabilities capabilities;
    private final ReasoningProfile reasoningProfile;
    private final ExecutionModel executionModel;
    private final SpecInterfaces interfaces;
    private final MemoryModel memoryModel;
    private final ToolsAndResources toolsAndResources;
    private final GovernanceSpec governance;
    private final LearningModel learningModel;
    private final EvaluationModel evaluation;
    private final ObservabilitySpec observability;
    private final InteroperabilitySpec interoperability;
    private final SecuritySpec security;
    private final DeploymentSpec deployment;

    private AgentSpec(Builder b) {
        this.agentSpecVersion  = b.agentSpecVersion;
        this.metadata          = b.metadata;
        this.identity          = b.identity;
        this.purposeModel      = b.purposeModel;
        this.scope             = b.scope;
        this.capabilities      = b.capabilities;
        this.reasoningProfile  = b.reasoningProfile;
        this.executionModel    = b.executionModel;
        this.interfaces        = b.interfaces;
        this.memoryModel       = b.memoryModel;
        this.toolsAndResources = b.toolsAndResources;
        this.governance        = b.governance;
        this.learningModel     = b.learningModel;
        this.evaluation        = b.evaluation;
        this.observability     = b.observability;
        this.interoperability  = b.interoperability;
        this.security          = b.security;
        this.deployment        = b.deployment;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public String getAgentSpecVersion()             { return agentSpecVersion; }
    public SpecMetadata getMetadata()               { return metadata; }
    public SpecIdentity getIdentity()               { return identity; }
    public @Nullable PurposeModel getPurposeModel() { return purposeModel; }
    public @Nullable SpecScope getScope()           { return scope; }
    public @Nullable SpecCapabilities getCapabilities() { return capabilities; }
    public @Nullable ReasoningProfile getReasoningProfile() { return reasoningProfile; }
    public @Nullable ExecutionModel getExecutionModel() { return executionModel; }
    public @Nullable SpecInterfaces getInterfaces() { return interfaces; }
    public @Nullable MemoryModel getMemoryModel()   { return memoryModel; }
    public @Nullable ToolsAndResources getToolsAndResources() { return toolsAndResources; }
    public @Nullable GovernanceSpec getGovernance() { return governance; }
    public @Nullable LearningModel getLearningModel() { return learningModel; }
    public @Nullable EvaluationModel getEvaluation() { return evaluation; }
    public @Nullable ObservabilitySpec getObservability() { return observability; }
    public @Nullable InteroperabilitySpec getInteroperability() { return interoperability; }
    public @Nullable SecuritySpec getSecurity()     { return security; }
    public @Nullable DeploymentSpec getDeployment() { return deployment; }

    public static Builder builder() { return new Builder(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested value objects
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Agent metadata: identity, ownership, lifecycle state.
     *
     * @doc.type record
     * @doc.purpose Top-level metadata block (id, name, namespace, version, status, owners, tags, summary)
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record SpecMetadata(
            @NotNull String id,
            @NotNull String name,
            @NotNull String namespace,
            @NotNull String version,
            @NotNull String status,
            @NotNull List<Map<String, String>> owners,
            @NotNull List<String> tags,
            @NotNull String summary,
            @Nullable String description
    ) {
        public SpecMetadata {
            owners = List.copyOf(owners);
            tags   = List.copyOf(tags);
        }
    }

    /**
     * Agent identity: type classification and behavioral guarantees.
     *
     * @doc.type record
     * @doc.purpose Identity block — agentType, subtype, criticality, autonomyLevel, determinismGuarantee, stateMutability, failureMode
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record SpecIdentity(
            @NotNull AgentType agentType,
            @Nullable String agentSubtype,
            @NotNull List<String> roles,
            @NotNull List<Map<String, String>> personas,
            @NotNull String criticality,
            @NotNull String autonomyLevel,
            @NotNull DeterminismGuarantee determinismGuarantee,
            @NotNull StateMutability stateMutability,
            @NotNull FailureMode failureMode
    ) {
        public SpecIdentity {
            roles    = List.copyOf(roles);
            personas = List.copyOf(personas);
        }
    }

    /**
     * Purpose model: mission, goals, non-goals, success criteria.
     *
     * @doc.type record
     * @doc.purpose Declares what the agent is trying to achieve and what success looks like
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record PurposeModel(
            @NotNull String mission,
            @NotNull List<String> goals,
            @NotNull List<String> nonGoals,
            @NotNull List<Map<String, String>> successCriteria
    ) {
        public PurposeModel {
            goals           = List.copyOf(goals);
            nonGoals        = List.copyOf(nonGoals);
            successCriteria = List.copyOf(successCriteria);
        }
    }

    /**
     * Operational scope: domains, supported entities, boundaries.
     *
     * @doc.type record
     * @doc.purpose Declares where the agent may operate and explicit operational boundaries
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record SpecScope(
            @NotNull List<String> domains,
            @NotNull List<String> supportedEntities,
            @NotNull List<String> boundaries
    ) {
        public SpecScope {
            domains          = List.copyOf(domains);
            supportedEntities = List.copyOf(supportedEntities);
            boundaries       = List.copyOf(boundaries);
        }
    }

    /**
     * Declared agent capabilities.
     *
     * @doc.type record
     * @doc.purpose Rich capability declarations (vs simple string lists)
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record SpecCapabilities(
            @NotNull List<CapabilityDeclaration> declaredCapabilities,
            @NotNull List<String> capabilityDependencies,
            @NotNull List<String> prohibitedCapabilities
    ) {
        public SpecCapabilities {
            declaredCapabilities  = List.copyOf(declaredCapabilities);
            capabilityDependencies = List.copyOf(capabilityDependencies);
            prohibitedCapabilities = List.copyOf(prohibitedCapabilities);
        }
    }

    /**
     * A single declared capability with its I/O types and determinism classification.
     *
     * @doc.type record
     * @doc.purpose Rich capability declaration for inter-agent routing and cataloging
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record CapabilityDeclaration(
            @NotNull String id,
            @NotNull String name,
            @Nullable String description,
            @NotNull List<String> inputTypes,
            @NotNull List<String> outputTypes,
            @Nullable String determinismLevel,
            boolean requiresHumanApproval
    ) {
        public CapabilityDeclaration {
            inputTypes  = List.copyOf(inputTypes);
            outputTypes = List.copyOf(outputTypes);
        }
    }

    /**
     * Reasoning profile: reasoners, strategy, determinism zones, confidence model.
     *
     * @doc.type record
     * @doc.purpose Declares how the agent reasons — what engines it uses and how they are combined
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record ReasoningProfile(
            @NotNull String primaryReasoner,
            @NotNull List<ReasonerDeclaration> reasonerPortfolio,
            @Nullable String reasoningStrategy,
            @Nullable DeterminismProfile determinismProfile,
            @Nullable ConfidenceModel confidenceModel
    ) {
        public ReasoningProfile {
            reasonerPortfolio = List.copyOf(reasonerPortfolio);
        }
    }

    /**
     * A single reasoner in the portfolio (e.g., rule-engine, llm, pattern).
     *
     * @doc.type record
     * @doc.purpose Declares a single reasoning engine with its purpose and invocation conditions
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record ReasonerDeclaration(
            @NotNull String id,
            @NotNull String type,
            @Nullable String purpose,
            @Nullable String engine,
            @NotNull List<String> invocationWhen
    ) {
        public ReasonerDeclaration {
            invocationWhen = List.copyOf(invocationWhen);
        }
    }

    /**
     * Determinism zone classification for test strategy design.
     *
     * @doc.type record
     * @doc.purpose Declares which parts of the agent are deterministic, speculative, or non-deterministic
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record DeterminismProfile(
            @NotNull List<String> fullyDeterministicZones,
            @NotNull List<String> boundedSpeculativeZones,
            @NotNull List<String> nonDeterministicZones
    ) {
        public DeterminismProfile {
            fullyDeterministicZones   = List.copyOf(fullyDeterministicZones);
            boundedSpeculativeZones   = List.copyOf(boundedSpeculativeZones);
            nonDeterministicZones     = List.copyOf(nonDeterministicZones);
        }
    }

    /**
     * Confidence scoring model for risk-aware routing and escalation.
     *
     * @doc.type record
     * @doc.purpose Defines how the agent computes and uses confidence thresholds
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record ConfidenceModel(
            @NotNull String scoreRange,
            @NotNull List<String> sources,
            double autoApproveThreshold,
            double humanReviewThreshold,
            double rejectBelowThreshold
    ) {
        public ConfidenceModel {
            sources = List.copyOf(sources);
        }
    }

    /**
     * Execution model: invocation modes, lifecycle states, concurrency, retry, timeout, compensation.
     *
     * @doc.type record
     * @doc.purpose Declares how the agent is invoked, its lifecycle, and failure semantics
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record ExecutionModel(
            @NotNull List<String> invocationModes,
            @NotNull List<String> lifecycleStates,
            @Nullable Map<String, Object> concurrencyModel,
            @Nullable Map<String, Object> retryPolicy,
            @Nullable Map<String, Object> timeoutPolicy,
            @Nullable Map<String, Object> compensationPolicy
    ) {
        public ExecutionModel {
            invocationModes = List.copyOf(invocationModes);
            lifecycleStates = List.copyOf(lifecycleStates);
        }
    }

    /**
     * Interface contracts: typed inputs, outputs, events, API contracts, modalities.
     *
     * @doc.type record
     * @doc.purpose Declares typed I/O contracts, event bindings, and supported modalities
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record SpecInterfaces(
            @NotNull List<InterfacePort> inputs,
            @NotNull List<InterfacePort> outputs,
            @NotNull List<String> eventsConsumed,
            @NotNull List<String> eventsProduced,
            @NotNull List<Map<String, String>> apiContracts,
            @NotNull List<String> supportedModalities
    ) {
        public SpecInterfaces {
            inputs             = List.copyOf(inputs);
            outputs            = List.copyOf(outputs);
            eventsConsumed     = List.copyOf(eventsConsumed);
            eventsProduced     = List.copyOf(eventsProduced);
            apiContracts       = List.copyOf(apiContracts);
            supportedModalities = List.copyOf(supportedModalities);
        }
    }

    /**
     * A single typed input or output port.
     *
     * @doc.type record
     * @doc.purpose Typed I/O port declaration with schema reference
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record InterfacePort(
            @NotNull String name,
            @Nullable String schemaRef,
            boolean required,
            @Nullable String description
    ) {}

    /**
     * Memory model: bindings, types, read strategies, write policies, retention.
     *
     * @doc.type record
     * @doc.purpose Declares how the agent accesses and manages memory across invocations
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record MemoryModel(
            @NotNull List<MemoryBinding> memoryBindings,
            @NotNull List<String> memoryTypes,
            @NotNull List<String> readStrategies,
            @NotNull Map<String, Object> writePolicies,
            @NotNull List<String> consolidationRules,
            @Nullable Map<String, String> retentionPolicy
    ) {
        public MemoryModel {
            memoryBindings     = List.copyOf(memoryBindings);
            memoryTypes        = List.copyOf(memoryTypes);
            readStrategies     = List.copyOf(readStrategies);
            consolidationRules = List.copyOf(consolidationRules);
        }
    }

    /**
     * A single memory system binding with its access mode.
     *
     * @doc.type record
     * @doc.purpose Declares least-privilege memory access (read, write, read-write, append-only)
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record MemoryBinding(
            @NotNull String memoryType,
            @NotNull String access
    ) {}

    /**
     * Tools and resources: tool declarations, resources, tool selection policy.
     *
     * @doc.type record
     * @doc.purpose Declares tools the agent can invoke and resources it can access
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record ToolsAndResources(
            @NotNull List<Map<String, Object>> tools,
            @NotNull List<Map<String, String>> resources,
            @Nullable Map<String, Object> toolSelectionPolicy
    ) {
        public ToolsAndResources {
            tools     = List.copyOf(tools);
            resources = List.copyOf(resources);
        }
    }

    /**
     * Governance specification: policy references, approvals, data handling, risk profile.
     *
     * @doc.type record
     * @doc.purpose Declares governance policy links, approval requirements, data classification, and risk assessment
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record GovernanceSpec(
            @NotNull List<GovernancePolicyRef> policyRefs,
            @Nullable Map<String, Object> approvals,
            @NotNull Map<String, Object> dataHandling,
            @NotNull Map<String, Object> riskProfile
    ) {
        public GovernanceSpec {
            policyRefs = List.copyOf(policyRefs);
        }
    }

    /**
     * Learning model: level, adaptation targets, sources, drift controls, rollback, prompt versioning.
     *
     * @doc.type record
     * @doc.purpose Declares how the agent may adapt over time (L0–L5 learning levels)
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record LearningModel(
            @NotNull String learningLevel,
            @NotNull List<String> adaptationTargets,
            @NotNull List<String> learningSources,
            @Nullable Map<String, Object> driftControls,
            @Nullable Map<String, Object> rollbackPolicy,
            @Nullable Map<String, Object> promptVersioning
    ) {
        public LearningModel {
            adaptationTargets = List.copyOf(adaptationTargets);
            learningSources   = List.copyOf(learningSources);
        }
    }

    /**
     * Evaluation model: spec refs, online/offline metrics, release gates.
     *
     * @doc.type record
     * @doc.purpose Declares evaluation packs, quality metrics, and deployment release gates
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record EvaluationModel(
            @NotNull List<String> evaluationSpecRefs,
            @NotNull List<String> onlineMetrics,
            @NotNull List<String> offlineMetrics,
            @Nullable Map<String, Object> releaseGates
    ) {
        public EvaluationModel {
            evaluationSpecRefs = List.copyOf(evaluationSpecRefs);
            onlineMetrics      = List.copyOf(onlineMetrics);
            offlineMetrics     = List.copyOf(offlineMetrics);
        }
    }

    /**
     * Observability specification: traces, logged artifacts, audit mode, alerts.
     *
     * @doc.type record
     * @doc.purpose Declares observability requirements for production deployment
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record ObservabilitySpec(
            boolean traceEnabled,
            @NotNull List<String> loggedArtifacts,
            @Nullable String auditMode,
            @NotNull List<String> alerts
    ) {
        public ObservabilitySpec {
            loggedArtifacts = List.copyOf(loggedArtifacts);
            alerts          = List.copyOf(alerts);
        }
    }

    /**
     * Interoperability specification: MCP, agent-to-agent, compatibility.
     *
     * @doc.type record
     * @doc.purpose Declares MCP tool/resource support and A2A protocol capabilities
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record InteroperabilitySpec(
            @Nullable Map<String, Object> mcp,
            @Nullable Map<String, Object> agentToAgent,
            @Nullable Map<String, Object> compatibility
    ) {}

    /**
     * Security specification: authn, authz, secrets handling, network policy.
     *
     * @doc.type record
     * @doc.purpose Declares authentication, authorization, and network security requirements
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record SecuritySpec(
            @NotNull Map<String, Object> authn,
            @NotNull Map<String, Object> authz,
            @Nullable Map<String, Object> secretsHandling,
            @Nullable Map<String, Object> networkPolicy
    ) {}

    /**
     * Deployment specification: runtime class, scaling model, locality constraints, dependencies.
     *
     * @doc.type record
     * @doc.purpose Declares deployment and scaling requirements
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public record DeploymentSpec(
            @Nullable String runtimeClass,
            @Nullable Map<String, Object> scalingModel,
            @Nullable Map<String, Object> localityConstraints,
            @NotNull List<String> dependencies,
            @Nullable Map<String, String> documentation
    ) {
        public DeploymentSpec {
            dependencies = List.copyOf(dependencies);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @doc.type class
     * @doc.purpose Builder for constructing immutable AgentSpec instances
     * @doc.layer platform
     * @doc.pattern Builder
     */
    public static final class Builder {
        private String agentSpecVersion = "1.0.0";
        private SpecMetadata metadata;
        private SpecIdentity identity;
        private PurposeModel purposeModel;
        private SpecScope scope;
        private SpecCapabilities capabilities;
        private ReasoningProfile reasoningProfile;
        private ExecutionModel executionModel;
        private SpecInterfaces interfaces;
        private MemoryModel memoryModel;
        private ToolsAndResources toolsAndResources;
        private GovernanceSpec governance;
        private LearningModel learningModel;
        private EvaluationModel evaluation;
        private ObservabilitySpec observability;
        private InteroperabilitySpec interoperability;
        private SecuritySpec security;
        private DeploymentSpec deployment;

        public Builder agentSpecVersion(String v)               { this.agentSpecVersion  = v;  return this; }
        public Builder metadata(SpecMetadata m)                 { this.metadata          = m;  return this; }
        public Builder identity(SpecIdentity i)                 { this.identity          = i;  return this; }
        public Builder purposeModel(PurposeModel p)             { this.purposeModel      = p;  return this; }
        public Builder scope(SpecScope s)                       { this.scope             = s;  return this; }
        public Builder capabilities(SpecCapabilities c)         { this.capabilities      = c;  return this; }
        public Builder reasoningProfile(ReasoningProfile r)     { this.reasoningProfile  = r;  return this; }
        public Builder executionModel(ExecutionModel e)         { this.executionModel    = e;  return this; }
        public Builder interfaces(SpecInterfaces i)             { this.interfaces        = i;  return this; }
        public Builder memoryModel(MemoryModel m)               { this.memoryModel       = m;  return this; }
        public Builder toolsAndResources(ToolsAndResources t)   { this.toolsAndResources = t;  return this; }
        public Builder governance(GovernanceSpec g)             { this.governance        = g;  return this; }
        public Builder learningModel(LearningModel l)           { this.learningModel     = l;  return this; }
        public Builder evaluation(EvaluationModel e)            { this.evaluation        = e;  return this; }
        public Builder observability(ObservabilitySpec o)       { this.observability     = o;  return this; }
        public Builder interoperability(InteroperabilitySpec i) { this.interoperability  = i;  return this; }
        public Builder security(SecuritySpec s)                 { this.security          = s;  return this; }
        public Builder deployment(DeploymentSpec d)             { this.deployment        = d;  return this; }

        public AgentSpec build() {
            if (metadata == null) throw new IllegalStateException("metadata is required");
            if (identity == null) throw new IllegalStateException("identity is required");
            return new AgentSpec(this);
        }
    }
}
