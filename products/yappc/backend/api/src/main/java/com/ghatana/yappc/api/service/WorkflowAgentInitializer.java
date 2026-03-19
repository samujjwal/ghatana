/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.workflow.WorkflowAgentRegistry;
import com.ghatana.agent.workflow.WorkflowAgentRole;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.core.activej.promise.PromiseUtils;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to initialize and register workflow agents on application startup.
 *
 * <p><b>Purpose</b><br>
 * Registers default workflow agents for all DevSecOps roles with the WorkflowAgentRegistry,
 * ensuring agents are available for execution.
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
    List<Promise<Void>> registrationPromises =
        roleToAgentId.entrySet().stream()
            .map(entry -> registerAgent(entry.getValue(), entry.getKey()))
            .toList();

    // Wait for all registrations to complete
    return PromiseUtils.all(registrationPromises)
        .map(
            list -> {
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

    TypedAgent<Map<String, Object>, String> agent = createLlmBackedAgent(agentId, role);
    return registry
        .register(agentId, role, agent)
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                logger.error("Failed to register agent: {}", agentId, error);
              } else {
                logger.debug(
                    "Registered agent: {} ({}) — {}", agentId, role, agent.descriptor().getName());
              }
            });
  }

  /**
   * Create an LLM-backed agent implementation for runtime.
   *
   * @param agentId The agent identifier
   * @param role The agent role
   * @return TypedAgent implementation using LLM completion
   */
  private TypedAgent<Map<String, Object>, String> createLlmBackedAgent(
      String agentId, WorkflowAgentRole role) {
    return new TypedAgent<>() {

      @NotNull
      @Override
      public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId(agentId)
            .name(role.getDisplayName())
            .description(role.getDescription())
            .type(AgentType.PROBABILISTIC)
            .build();
      }

      @NotNull
      @Override
      public Promise<Void> initialize(@NotNull AgentConfig config) {
        return Promise.complete();
      }

      @NotNull
      @Override
      public Promise<Void> shutdown() {
        return Promise.complete();
      }

      @NotNull
      @Override
      public Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.HEALTHY);
      }

      @NotNull
      @Override
      public Promise<AgentResult<String>> process(
          @NotNull AgentContext context, @NotNull Map<String, Object> input) {
        Instant start = Instant.now();
        String inputText = String.valueOf(input);
        String prompt =
            "You are the "
                + role.getDisplayName()
                + " agent ("
                + role.getCode()
                + ").\n"
                + "Role description: "
                + role.getDescription()
                + "\n"
                + "Tenant: "
                + context.getTenantId()
                + "\n"
                + "Timestamp: "
                + Instant.now()
                + "\n\n"
                + "Task input:\n"
                + inputText
                + "\n\n"
                + "Provide a concise, actionable response.";

        CompletionRequest request =
            CompletionRequest.builder().prompt(prompt).maxTokens(1024).temperature(0.3).build();

        logger.debug("Agent {} executing with input length: {}", agentId, inputText.length());
        return llmGateway
            .complete(request)
            .map(
                result -> {
                  String text =
                      result != null && result.getText() != null ? result.getText().trim() : "";
                  String output = text.isEmpty() ? ("No response generated by " + agentId) : text;
                  return AgentResult.success(
                      output, agentId, Duration.between(start, Instant.now()));
                });
      }
    };
  }
}
