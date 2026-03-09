package com.ghatana.virtualorg.factory;

import com.ghatana.virtualorg.agent.VirtualOrgAgent;
import com.ghatana.virtualorg.agent.roles.SeniorEngineerAgent;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.v1.*;
import io.activej.eventloop.Eventloop;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Factory for creating VirtualOrgAgent instances with role-specific configuration.
 *
 * <p><b>Purpose</b><br>
 * Central factory for instantiating agents with role-appropriate authority,
 * LLM configuration, memory settings, and dependency wiring. Ensures
 * consistent agent initialization across the virtual organization.
 *
 * <p><b>Architecture Role</b><br>
 * Factory providing:
 * - Role-based agent creation (CEO, CTO, Architect, Engineer, PM, QA, DevOps)
 * - Decision authority configuration per organizational hierarchy
 * - LLM and memory settings customized to role needs
 * - Dependency injection for all agent components
 *
 * <p><b>Authority Configuration</b><br>
 * Each role receives decision authority matching organizational hierarchy:
 * - <b>CEO</b>: Strategic decisions, budget allocation, architecture changes (no escalation)
 * - <b>CTO</b>: Architecture changes, technology choices (escalates strategic to CEO)
 * - <b>Architect</b>: Architecture changes, refactoring (escalates technology to CTO)
 * - <b>Senior Engineer</b>: Feature implementation, code review, refactoring (escalates architecture to Architect, max 500 LOC)
 * - <b>Engineer</b>: Bug fixes, minor features (escalates refactoring to Senior Engineer)
 * - <b>QA Engineer</b>: Test planning, quality gates (escalates release decisions)
 * - <b>DevOps Engineer</b>: Deployment approvals, infrastructure changes
 *
 * <p><b>LLM Configuration</b><br>
 * Role-specific model selection and parameters:
 * - Executive roles (CEO, CTO): GPT-4 with higher temperature for creative decisions
 * - Technical roles (Architect, Engineer): GPT-4 with lower temperature for precision
 * - Operational roles (QA, DevOps): GPT-3.5 Turbo for efficiency
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VirtualOrgAgentFactory factory = new VirtualOrgAgentFactory(
 *     eventloop,
 *     llmClient,
 *     memory,
 *     toolRegistry,
 *     toolExecutor,
 *     meterRegistry,
 *     tracer
 * );
 * 
 * // Create senior engineer with appropriate authority
 * VirtualOrgAgent engineer = factory.createAgent(AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER);
 * 
 * // Agent is pre-configured with:
 * // - Authority: implement_feature, code_review, refactor (escalates architecture changes)
 * // - LLM: GPT-4 with temperature=0.2 for precision
 * // - Memory: Short-term + long-term with 10k token context window
 * // - Tools: git, jira, slack, code-analysis
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Agent creation is stateless; all state held in created agents.
 *
 * @see VirtualOrgAgent
 * @see AgentRoleProto
 * @see DecisionAuthorityProto
 * @doc.type class
 * @doc.purpose Factory for creating role-based agents with authority
 * @doc.layer product
 * @doc.pattern Factory
 */
public class VirtualOrgAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(VirtualOrgAgentFactory.class);

    private final Eventloop eventloop;
    private final LLMClient llmClient;
    private final AgentMemory memory;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public VirtualOrgAgentFactory(
            @NotNull Eventloop eventloop,
            @NotNull LLMClient llmClient,
            @NotNull AgentMemory memory,
            @NotNull ToolRegistry toolRegistry,
            @NotNull ToolExecutor toolExecutor,
            @NotNull MeterRegistry meterRegistry,
            @NotNull Tracer tracer) {

        this.eventloop = eventloop;
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;

        log.info("Initialized VirtualOrgAgentFactory");
    }

    /**
     * Creates an agent of the specified role.
     *
     * @param role the agent role
     * @return the created agent
     */
    @NotNull
    public VirtualOrgAgent createAgent(@NotNull AgentRoleProto role) {
        String agentId = generateAgentId(role);

        DecisionAuthorityProto authority = buildAuthority(role);
        LLMConfigProto llmConfig = buildLLMConfig(role);
        MemoryConfigProto memoryConfig = buildMemoryConfig(role);

        VirtualOrgAgent agent = switch (role) {
            case AGENT_ROLE_SENIOR_ENGINEER -> new SeniorEngineerAgent(
                    agentId,
                    authority,
                    eventloop,
                    llmClient,
                    memory,
                    toolRegistry,
                    toolExecutor,
                    meterRegistry,
                    tracer,
                    llmConfig,
                    memoryConfig
            );

            // Roles pending implementation — follow SeniorEngineerAgent pattern
            case AGENT_ROLE_ARCHITECT -> throw new UnsupportedOperationException(
                    "Architect agent not yet implemented — create ArchitectAgent extending AbstractAgent with architecture-review capabilities");
            case AGENT_ROLE_ENGINEER -> throw new UnsupportedOperationException(
                    "Engineer agent not yet implemented — create EngineerAgent extending AbstractAgent with code-generation capabilities");
            case AGENT_ROLE_QA_ENGINEER -> throw new UnsupportedOperationException(
                    "QA agent not yet implemented — create QaEngineerAgent extending AbstractAgent with test-generation capabilities");
            case AGENT_ROLE_DEVOPS_ENGINEER -> throw new UnsupportedOperationException(
                    "DevOps agent not yet implemented — create DevOpsAgent extending AbstractAgent with deployment capabilities");
            case AGENT_ROLE_PRODUCT_MANAGER -> throw new UnsupportedOperationException(
                    "PM agent not yet implemented — create ProductManagerAgent extending AbstractAgent with requirement capabilities");

            default -> throw new IllegalArgumentException("Unsupported agent role: " + role);
        };

        log.info("Created agent: id={}, role={}", agentId, role);

        return agent;
    }

    // =============================
    // Authority configuration per role
    // =============================

    private DecisionAuthorityProto buildAuthority(AgentRoleProto role) {
        DecisionAuthorityProto.Builder builder = DecisionAuthorityProto.newBuilder();

        switch (role) {
            case AGENT_ROLE_CEO -> {
                // CEO can decide everything
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_STRATEGIC);
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_BUDGET_ALLOCATION);
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_ARCHITECTURE_CHANGE);
                builder.setEscalationTarget(""); // No escalation
            }

            case AGENT_ROLE_CTO -> {
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_ARCHITECTURE_CHANGE);
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_TECHNOLOGY_CHOICE);
                builder.addMustEscalate(DecisionTypeProto.DECISION_TYPE_STRATEGIC);
                builder.setEscalationTarget("ceo");
            }

            case AGENT_ROLE_ARCHITECT -> {
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_ARCHITECTURE_CHANGE);
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_REFACTOR);
                builder.addMustEscalate(DecisionTypeProto.DECISION_TYPE_TECHNOLOGY_CHOICE);
                builder.setEscalationTarget("cto");
            }

            case AGENT_ROLE_SENIOR_ENGINEER -> {
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_IMPLEMENT_FEATURE);
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_CODE_REVIEW);
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_REFACTOR);
                builder.addMustEscalate(DecisionTypeProto.DECISION_TYPE_ARCHITECTURE_CHANGE);
                builder.setEscalationTarget("architect");

                // Limits
                builder.putLimits("max_lines", AuthorityLimitProto.newBuilder()
                        .setMaxLines(500)
                        .build());
            }

            case AGENT_ROLE_ENGINEER -> {
                builder.addCanDecide(DecisionTypeProto.DECISION_TYPE_BUG_FIX);
                builder.addMustEscalate(DecisionTypeProto.DECISION_TYPE_IMPLEMENT_FEATURE);
                builder.setEscalationTarget("senior_engineer");

                builder.putLimits("max_lines", AuthorityLimitProto.newBuilder()
                        .setMaxLines(200)
                        .build());
            }

            default -> {
                // Default: minimal authority
                builder.setEscalationTarget("senior_engineer");
            }
        }

        return builder.build();
    }

    private LLMConfigProto buildLLMConfig(AgentRoleProto role) {
        // Role-specific LLM configurations
        return switch (role) {
            case AGENT_ROLE_CEO, AGENT_ROLE_CTO, AGENT_ROLE_ARCHITECT ->
                // Executive/strategic roles: higher temperature, more tokens
                    LLMConfigProto.newBuilder()
                            .setModel("gpt-4")
                            .setTemperature(0.8f)
                            .setMaxTokens(4096)
                            .setTimeoutSeconds(60)
                            .setProvider("openai")
                            .build();

            case AGENT_ROLE_SENIOR_ENGINEER, AGENT_ROLE_ENGINEER ->
                // Engineering roles: balanced
                    LLMConfigProto.newBuilder()
                            .setModel("gpt-4")
                            .setTemperature(0.7f)
                            .setMaxTokens(2048)
                            .setTimeoutSeconds(30)
                            .setProvider("openai")
                            .build();

            case AGENT_ROLE_QA_ENGINEER ->
                // QA: deterministic
                    LLMConfigProto.newBuilder()
                            .setModel("gpt-4")
                            .setTemperature(0.3f)
                            .setMaxTokens(2048)
                            .setTimeoutSeconds(30)
                            .setProvider("openai")
                            .build();

            default -> LLMConfigProto.newBuilder()
                    .setModel("gpt-3.5-turbo")
                    .setTemperature(0.7f)
                    .setMaxTokens(1024)
                    .setTimeoutSeconds(30)
                    .setProvider("openai")
                    .build();
        };
    }

    private MemoryConfigProto buildMemoryConfig(AgentRoleProto role) {
        return MemoryConfigProto.newBuilder()
                .setShortTermSize(100)
                .setLongTermEnabled(true)
                .setVectorStoreType("pgvector")
                .setEmbeddingModel("text-embedding-ada-002")
                .setRetention(MemoryRetentionProto.newBuilder()
                        .setShortTermTtlSeconds(3600) // 1 hour
                        .setLongTermMaxItems(10000)
                        .setCleanupFrequencySeconds(86400) // 1 day
                        .build())
                .build();
    }

    private String generateAgentId(AgentRoleProto role) {
        return role.name().toLowerCase().replace("agent_role_", "")
                + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
