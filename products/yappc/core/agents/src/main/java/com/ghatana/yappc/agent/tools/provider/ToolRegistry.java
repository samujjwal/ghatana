/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents Module - Tool Providers
 */
package com.ghatana.yappc.agent.tools.provider;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.planner.PlannerAgentFactory;
import com.ghatana.core.activej.promise.PromiseUtils;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool Registry - Maps capability IDs to ToolProvider implementations.
 *
 * <p><b>Purpose</b><br>
 * Central registry that bridges the gap between capability definitions (capabilities.yaml)
 * and actual tool implementations. Enables agents to discover and invoke tools by capability.
 *
 * <p><b>Architecture</b><br>
 * - Maintains capability ID → ToolProvider mappings
 * - Supports multiple providers per capability (fallback chain)
 * - Integrates with PlannerAgentFactory for framework compatibility
 * - Provides capability discovery for agent initialization
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Register providers
 * ToolRegistry registry = new ToolRegistry();
 * registry.register(new CodeGenerationToolProvider());
 * registry.register(new TestGenerationToolProvider());
 *
 * // Execute by capability
 * ToolResult result = registry.execute(ctx, "code-generation", params).getResult();
 *
 * // Check capability availability
 * if (registry.hasCapability("security-analysis")) {
 *   // Route to security agent
 * }
 * }</pre>
 *
 * @see ToolProvider
 * @see PlannerAgentFactory
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type class
 * @doc.purpose Handles tool registry operations
 * @doc.layer core
 * @doc.pattern Registry
*/
public class ToolRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);

  private final Map<String, List<ToolProvider>> providersByCapability;
  private final Map<String, ToolProvider> providersByName;

  /**
   * Creates an empty tool registry.
   */
  public ToolRegistry() {
    this.providersByCapability = new ConcurrentHashMap<>();
    this.providersByName = new ConcurrentHashMap<>();
  }

  /**
   * Registers a tool provider.
   *
   * @param provider the tool provider to register
   * @return this registry for chaining
   * @throws IllegalArgumentException if provider is null or capability ID is blank
   */
  @NotNull
  public ToolRegistry register(@NotNull ToolProvider provider) {
    if (provider == null) {
      throw new IllegalArgumentException("ToolProvider cannot be null");
    }

    String capabilityId = provider.getCapabilityId();
    if (capabilityId == null || capabilityId.isBlank()) {
      throw new IllegalArgumentException("ToolProvider must provide a non-blank capability ID");
    }

    String toolName = provider.getToolName();
    LOG.info("Registering tool provider: {} for capability: {}", toolName, capabilityId);

    providersByCapability
        .computeIfAbsent(capabilityId, k -> new ArrayList<>())
        .add(provider);

    providersByName.put(toolName, provider);

    return this;
  }

  /**
   * Registers multiple providers.
   *
   * @param providers the providers to register
   * @return this registry for chaining
   */
  @NotNull
  public ToolRegistry registerAll(@NotNull ToolProvider... providers) {
    for (ToolProvider provider : providers) {
      register(provider);
    }
    return this;
  }

  /**
   * Checks if a capability is available.
   *
   * @param capabilityId the capability to check
   * @return true if at least one provider is registered
   */
  public boolean hasCapability(@NotNull String capabilityId) {
    List<ToolProvider> providers = providersByCapability.get(capabilityId);
    return providers != null && !providers.isEmpty();
  }

  /**
   * Gets all registered capability IDs.
   *
   * @return set of capability IDs
   */
  @NotNull
  public Set<String> getRegisteredCapabilities() {
    return new HashSet<>(providersByCapability.keySet());
  }

  /**
   * Gets providers for a specific capability.
   *
   * @param capabilityId the capability ID
   * @return list of providers (empty if none registered)
   */
  @NotNull
  public List<ToolProvider> getProvidersForCapability(@NotNull String capabilityId) {
    return Collections.unmodifiableList(
        providersByCapability.getOrDefault(capabilityId, List.of()));
  }

  /**
   * Gets a provider by its tool name.
   *
   * @param toolName the tool name
   * @return the provider, or null if not found
   */
  @Nullable
  public ToolProvider getProviderByName(@NotNull String toolName) {
    return providersByName.get(toolName);
  }

  /**
   * Executes a tool by capability ID.
   * Uses the first available provider.
   *
   * @param ctx the agent context
   * @param capabilityId the capability to execute
   * @param params the execution parameters
   * @return Promise of tool result
   */
  @NotNull
  public Promise<ToolProvider.ToolResult> execute(
      @NotNull AgentContext ctx,
      @NotNull String capabilityId,
      @NotNull Map<String, Object> params) {

    List<ToolProvider> providers = providersByCapability.get(capabilityId);
    if (providers == null || providers.isEmpty()) {
      LOG.error("No provider registered for capability: {}", capabilityId);
      return Promise.of(ToolProvider.ToolResult.failure(
          "No provider for capability: " + capabilityId,
          Map.of("capability", capabilityId, "status", "not_found")));
    }

    // Try providers in order until one succeeds
    return tryExecuteProviders(ctx, capabilityId, params, new ArrayList<>(providers), 0);
  }

  private Promise<ToolProvider.ToolResult> tryExecuteProviders(
      AgentContext ctx,
      String capabilityId,
      Map<String, Object> params,
      List<ToolProvider> providers,
      int index) {

    if (index >= providers.size()) {
      LOG.error("All providers failed for capability: {}", capabilityId);
      return Promise.of(ToolProvider.ToolResult.failure(
          "All providers failed for capability: " + capabilityId,
          Map.of("capability", capabilityId, "status", "all_failed")));
    }

    ToolProvider provider = providers.get(index);
    String toolName = provider.getToolName();

    LOG.debug("Attempting execution with provider: {} for capability: {}",
        toolName, capabilityId);

    // Validate params
    String validationError = provider.validateParams(params);
    if (validationError != null) {
      LOG.warn("Provider {} validation failed: {}", toolName, validationError);
      return tryExecuteProviders(ctx, capabilityId, params, providers, index + 1);
    }

    // Check health
    if (!provider.isHealthy()) {
      LOG.warn("Provider {} is not healthy, trying next", toolName);
      return tryExecuteProviders(ctx, capabilityId, params, providers, index + 1);
    }

    // Execute
    return PromiseUtils.flatMap(provider.execute(ctx, params), result -> {
      if (result.success()) {
        LOG.debug("Provider {} succeeded for capability: {}", toolName, capabilityId);
        return Promise.of(result);
      } else {
        LOG.warn("Provider {} failed for capability {}: {}",
            toolName, capabilityId, result.errorMessage());
        return tryExecuteProviders(ctx, capabilityId, params, providers, index + 1);
      }
    });
  }

  /**
   * Gets the number of registered capabilities.
   *
   * @return capability count
   */
  public int getCapabilityCount() {
    return providersByCapability.size();
  }

  /**
   * Gets the total number of registered providers.
   *
   * @return provider count
   */
  public int getProviderCount() {
    return providersByName.size();
  }

  /**
   * Gets health status for all providers.
   *
   * @return map of tool name -> health status
   */
  @NotNull
  public Map<String, Boolean> getHealthStatus() {
    Map<String, Boolean> status = new HashMap<>();
    for (Map.Entry<String, ToolProvider> entry : providersByName.entrySet()) {
      status.put(entry.getKey(), entry.getValue().isHealthy());
    }
    return status;
  }

  /**
   * Creates a registry pre-populated with all YAPPC core tool providers.
   *
   * @return configured registry
   */
  @NotNull
  public static ToolRegistry createWithDefaults() {
    ToolRegistry registry = new ToolRegistry();

    // Core capabilities from capabilities.yaml
    registry.register(new CodeGenerationToolProvider());
    registry.register(new TestGenerationToolProvider());
    registry.register(new SecurityAnalysisToolProvider());
    registry.register(new DeploymentToolProvider());
    registry.register(new DocumentationToolProvider());

    LOG.info("Created default ToolRegistry with {} capabilities", registry.getCapabilityCount());
    return registry;
  }
}
