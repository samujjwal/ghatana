/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.agent.Agent;
import com.ghatana.agent.AgentCapabilities;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.workflow.WorkflowAgentRegistry;
import com.ghatana.agent.workflow.WorkflowAgentRole;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.core.activej.promise.PromiseUtils;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to initialize and register workflow agents on application startup.
 *
 * <p><b>Purpose</b><br>
 * Registers default workflow agents for all DevSecOps roles with the
 * WorkflowAgentRegistry, ensuring agents are available for execution.
 *
 * <p><b>Architecture Notes</b><br>
 *
 * <ul>
 *   <li>Creates one agent per WorkflowAgentRole
 *   <li>Creates LLM-backed runtime agents for each workflow role
 *   <li>Agents generate contextual role-specific responses via the platform LLM gateway
 *   <li>Health checks always return healthy status
 * </ul>
 *
 * <p><b>Usage</b><br>
 *
 * <pre>
 * WorkflowAgentInitializer initializer = new WorkflowAgentInitializer(registry);
 * initializer.initialize().whenComplete((count, error) -> {
 *   if (error == null) {
 *     logger.info("Registered {} agents", count);
 *   }
 * });
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Initialize workflow agents on startup
 * @doc.layer product
 * @doc.pattern Service
 */
public class WorkflowAgentInitializer {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowAgentInitializer.class);

    private final WorkflowAgentRegistry registry;
    private final LLMGateway llmGateway;

    /**
     * Constructs WorkflowAgentInitializer with registry dependency.
     *
     * @param registry The workflow agent registry
     * @param llmGateway LLM gateway used by registered agents
     */
    public WorkflowAgentInitializer(WorkflowAgentRegistry registry, LLMGateway llmGateway) {
        this.registry = registry;
        this.llmGateway = llmGateway;
    }

    /**
     * Initialize and register all workflow agents.
     *
     * @return Promise resolving to count of registered agents
     */
    public Promise<Integer> initialize() {
        logger.info("Initializing workflow agents...");

        Map<WorkflowAgentRole, String> roleToAgentId = new HashMap<>();
        roleToAgentId.put(WorkflowAgentRole.TASK_MANAGER, "task-manager-001");
        roleToAgentId.put(WorkflowAgentRole.CODE_REVIEWER, "code-reviewer-001");
        roleToAgentId.put(WorkflowAgentRole.TEST_WRITER, "test-writer-001");
        roleToAgentId.put(WorkflowAgentRole.DOCUMENTATION_AGENT, "documentation-001");
        roleToAgentId.put(WorkflowAgentRole.SECURITY_SCANNER, "security-scanner-001");
        roleToAgentId.put(WorkflowAgentRole.PERFORMANCE_OPTIMIZER, "performance-optimizer-001");
        roleToAgentId.put(WorkflowAgentRole.RELEASE_MANAGER, "release-manager-001");
        roleToAgentId.put(WorkflowAgentRole.INCIDENT_RESPONDER, "incident-responder-001");
        roleToAgentId.put(WorkflowAgentRole.INFRASTRUCTURE_AGENT, "infrastructure-001");
        roleToAgentId.put(WorkflowAgentRole.COMPLIANCE_AUDITOR, "compliance-auditor-001");
        roleToAgentId.put(WorkflowAgentRole.GENERAL, "general-001");

        // Register each agent and collect promises
        List<Promise<Void>> registrationPromises = roleToAgentId.entrySet().stream()
                .map(entry -> registerAgent(entry.getValue(), entry.getKey()))
                .toList();

        // Wait for all registrations to complete
        return PromiseUtils.all(registrationPromises)
                .map(list -> {
                    int count = roleToAgentId.size();
                    logger.info("Successfully initialized {} workflow agents", count);
                    return count;
                });
    }

    /**
     * Register a single agent with the registry.
     *
     * @param agentId The agent identifier
     * @param role The agent role
     * @return Promise that completes when registration is done
     */
    private Promise<Void> registerAgent(String agentId, WorkflowAgentRole role) {
        logger.debug("Registering agent: {} with role: {}", agentId, role);

        Agent agent = createLlmBackedAgent(agentId, role);
        return registry.register(agentId, role, agent)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Failed to register agent: {}", agentId, error);
                    } else {
                        logger.debug("Registered agent: {} ({}) with capabilities: {}",
                                agentId, role, agent.getCapabilities());
                    }
                });
    }

    /**
     * Create an LLM-backed agent implementation for runtime.
     *
     * @param agentId The agent identifier
     * @param role The agent role
     * @return Agent implementation using LLM completion
     */
    private Agent createLlmBackedAgent(String agentId, WorkflowAgentRole role) {
        return new Agent() {
            @NotNull
            @Override
            public String getId() {
                return agentId;
            }

            @NotNull
            @Override
            public AgentCapabilities getCapabilities() {
                return new AgentCapabilities(
                    role.getDisplayName(),
                    role.getCode(),
                    role.getDescription(),
                    Set.of("workflow-automation", "devsecops", role.getCode()),
                    Set.of()
                );
            }

            @NotNull
            @Override
            public Promise<Void> initialize(@NotNull AgentContext context) {
                return Promise.complete();
            }

            @NotNull
            @Override
            public Promise<Void> start() {
                return Promise.complete();
            }

            @NotNull
            @Override
            public <T, R> Promise<R> process(@NotNull T task, @NotNull AgentContext context) {
                String input = String.valueOf(task);
                String prompt =
                    "You are the " + role.getDisplayName() + " agent (" + role.getCode() + ").\n"
                        + "Role description: " + role.getDescription() + "\n"
                        + "Tenant: " + context.getTenantId() + "\n"
                        + "Timestamp: " + Instant.now() + "\n\n"
                        + "Task input:\n" + input + "\n\n"
                        + "Provide a concise, actionable response.";

                CompletionRequest request =
                    CompletionRequest.builder().prompt(prompt).maxTokens(1024).temperature(0.3).build();

                logger.debug("Agent {} executing with input length: {}", agentId, input.length());
                return llmGateway
                    .complete(request)
                    .map(result -> {
                        String text = result != null && result.getText() != null
                            ? result.getText().trim()
                            : "";
                        @SuppressWarnings("unchecked")
                        R response = (R) (text.isEmpty()
                            ? ("No response generated by " + agentId)
                            : text);
                        return response;
                    });
            }

            @NotNull
            @Override
            public Promise<Void> shutdown() {
                return Promise.complete();
            }
        };
    }
}
