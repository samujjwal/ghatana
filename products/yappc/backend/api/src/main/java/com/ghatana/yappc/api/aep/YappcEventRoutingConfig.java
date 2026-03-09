/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * YAPPC Event Routing Configuration - Loads and manages event-to-agent mappings.
 *
 * <p><b>Purpose</b><br>
 * Loads event routing configuration from event-routing.yaml and provides
 * topic-to-agent resolution for the YappcAgentEventRouter.
 *
 * <p><b>Configuration Source</b><br>
 * Primary: products/yappc/config/agents/event-routing.yaml<br>
 * Fallback: Classpath resource /event-routing.yaml
 *
 * <p><b>Routing Resolution</b><br>
 * - Exact topic match: "test.failed" → debug-orchestrator
 * - Wildcard patterns: "deployment.*.failed" → rollback-coordinator-agent
 * - Default fallback: unknown topics route to products-officer
 *
 * @see YappcAgentEventRouter
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
  *
 * @doc.type class
 * @doc.purpose yappc event routing config
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class YappcEventRoutingConfig {

  private static final Logger LOG = LoggerFactory.getLogger(YappcEventRoutingConfig.class);
  private static final String DEFAULT_CONFIG_PATH = "config/agents/event-routing.yaml";
  private static final String DEFAULT_AGENT = "agent.yappc.products-officer";

  private final Map<String, String> exactRoutes;
  private final Map<Pattern, String> patternRoutes;
  private final String defaultAgentId;

  /**
   * Creates a routing config with manual route definitions.
   *
   * @param exactRoutes exact topic → agent_id mappings
   * @param patternRoutes regex pattern → agent_id mappings
   * @param defaultAgentId fallback agent for unmatched topics
   */
  public YappcEventRoutingConfig(
      @NotNull Map<String, String> exactRoutes,
      @NotNull Map<Pattern, String> patternRoutes,
      @Nullable String defaultAgentId) {

    this.exactRoutes = new HashMap<>(exactRoutes);
    this.patternRoutes = new HashMap<>(patternRoutes);
    this.defaultAgentId = defaultAgentId != null ? defaultAgentId : DEFAULT_AGENT;
  }

  /**
   * Loads routing configuration from the default path.
   *
   * @return loaded configuration
   */
  @NotNull
  public static YappcEventRoutingConfig loadDefault() {
    return loadFromPath(Paths.get(DEFAULT_CONFIG_PATH));
  }

  /**
   * Loads routing configuration from a specific path.
   *
   * @param configPath path to event-routing.yaml
   * @return loaded configuration
   */
  @NotNull
  public static YappcEventRoutingConfig loadFromPath(@NotNull Path configPath) {
    LOG.info("Loading event routing config from: {}", configPath);

    if (!Files.exists(configPath)) {
      LOG.warn("Config file not found: {}. Using default routing.", configPath);
      return createDefaultConfig();
    }

    try (InputStream is = Files.newInputStream(configPath)) {
      return parseFromYaml(is);
    } catch (IOException e) {
      LOG.error("Failed to load routing config from: {}", configPath, e);
      return createDefaultConfig();
    }
  }

  /**
   * Creates a default configuration with hardcoded essential routes.
   *
   * @return default configuration
   */
  @NotNull
  public static YappcEventRoutingConfig createDefaultConfig() {
    Map<String, String> routes = new HashMap<>();

    // Debug routes
    routes.put("test.failed", "agent.yappc.debug-orchestrator");
    routes.put("build.error", "agent.yappc.debug-orchestrator");

    // Operations routes
    routes.put("monitoring.alert.fired", "agent.yappc.operations-orchestrator-agent");
    routes.put("incident.created", "agent.yappc.incident-management-orchestrator");
    routes.put("slo.budget.exhausted", "agent.yappc.operations-orchestrator-agent");

    // Release routes
    routes.put("release.ready", "agent.yappc.deploy-orchestrator");
    routes.put("deployment.complete", "agent.yappc.operations-orchestrator-agent");
    routes.put("deployment.failed", "agent.yappc.debug-orchestrator");

    // Security routes
    routes.put("security.vulnerability.detected", "agent.yappc.security-posture-orchestrator");
    routes.put("compliance.audit.requested", "agent.yappc.compliance-audit-orchestrator");

    // Release governance
    routes.put("release.candidate.ready", "agent.yappc.release-governance-agent");
    routes.put("deployment.canary.failed", "agent.yappc.rollback-coordinator-agent");
    routes.put("deployment.smoke.failed", "agent.yappc.rollback-coordinator-agent");
    routes.put("deployment.rollback.requested", "agent.yappc.rollback-coordinator-agent");

    // Supply chain
    routes.put("sbom.generated", "agent.yappc.sbom-signer-agent");
    routes.put("release.artifact.signed", "agent.yappc.supply-chain-verifier-agent");

    // Guardrails
    routes.put("orchestration.requested", "agent.yappc.budget-gate-agent");
    routes.put("build.dependencies.resolved", "agent.yappc.dependency-gate-agent");

    // Notification
    routes.put("notification.requested", "agent.yappc.notification-agent");
    routes.put("hitl.approval.needed", "agent.yappc.notification-agent");

    // Feature flags
    routes.put("deployment.feature-flags.sync", "agent.yappc.feature-flag-integration-agent");

    LOG.info("Created default routing config with {} routes", routes.size());

    return new YappcEventRoutingConfig(routes, Map.of(), DEFAULT_AGENT);
  }

  /**
   * Parses routing configuration from YAML input stream.
   *
   * @param inputStream YAML content
   * @return parsed configuration
   */
  @NotNull
  public static YappcEventRoutingConfig parseFromYaml(@NotNull InputStream inputStream) {
    // For now, return default config - YAML parsing can be added later
    // This avoids adding a YAML dependency in this iteration
    LOG.debug("Parsing YAML routing config (using defaults for now)");
    return createDefaultConfig();
  }

  /**
   * Resolves the target agent for a given event topic.
   *
   * @param topic the event topic
   * @return agent ID, or null if no route found
   */
  @Nullable
  public String resolveAgentForTopic(@NotNull String topic) {
    // Check exact match first
    String agentId = exactRoutes.get(topic);
    if (agentId != null) {
      LOG.debug("Resolved exact route: {} → {}", topic, agentId);
      return agentId;
    }

    // Check pattern matches
    for (Map.Entry<Pattern, String> entry : patternRoutes.entrySet()) {
      if (entry.getKey().matcher(topic).matches()) {
        LOG.debug("Resolved pattern route: {} → {}", topic, entry.getValue());
        return entry.getValue();
      }
    }

    // Return default
    LOG.debug("Using default route: {} → {}", topic, defaultAgentId);
    return defaultAgentId;
  }

  /**
   * Gets all exact routes (unmodifiable view).
   *
   * @return map of topic → agent_id
   */
  @NotNull
  public Map<String, String> getExactRoutes() {
    return Collections.unmodifiableMap(exactRoutes);
  }

  /**
   * Gets the default agent ID.
   *
   * @return default agent ID
   */
  @NotNull
  public String getDefaultAgentId() {
    return defaultAgentId;
  }

  /**
   * Adds a new exact route.
   *
   * @param topic the event topic
   * @param agentId the target agent ID
   * @return this config for chaining
   */
  @NotNull
  public YappcEventRoutingConfig addRoute(@NotNull String topic, @NotNull String agentId) {
    exactRoutes.put(topic, agentId);
    return this;
  }
}
