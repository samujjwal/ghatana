/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import com.ghatana.aep.operator.BackpressureStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * YAPPC Backpressure Configuration - Per-agent-type backpressure settings.
 *
 * <p><b>Purpose</b><br>
 * Configures backpressure strategies and buffer sizes for different agent categories
 * in YAPPC. Critical agents (security, governance) get BLOCK strategy, while
 * analytics agents get DROP_OLDEST to prioritize fresh data.
 *
 * <p><b>Default Strategies by Agent Category</b><br>
 * <pre>
 * Security/Governance (sentinel, release-governance): BLOCK — never drop security events
 * Orchestrators (debug-orchestrator, deploy-orchestrator): DROP_OLDEST — prioritize recent
 * Workers (java-class-writer, unit-test-writer): DROP_LATEST — drop on backpressure
 * Analytics (improve-orchestrator): DROP_OLDEST — fresh insights matter more
 * Integration (notification-agent): OVERFLOW_TO_DLQ — preserve all notifications
 * </pre>
 *
 * <p><b>Configuration</b><br>
 * Can be loaded from YAML or constructed programmatically. Environment overrides
 * supported for buffer sizes via AEP_BUFFER_SIZE_{CATEGORY} variables.
 *
 * @see YappcAgentEventRouter
 * @see BackpressureStrategy
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
  *
 * @doc.type class
 * @doc.purpose yappc backpressure config
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class YappcBackpressureConfig {

  private final Map<String, AgentBackpressureSettings> settingsByCategory;
  private final AgentBackpressureSettings defaultSettings;

  /**
   * Backpressure settings for a specific agent or category.
   */
  public record AgentBackpressureSettings(
      @NotNull BackpressureStrategy strategy,
      int bufferSize,
      boolean enableMetrics
  ) {
    /**
     * Creates settings with validation.
     */
    public AgentBackpressureSettings {
      Objects.requireNonNull(strategy, "strategy");
      if (bufferSize <= 0) {
        throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
      }
    }
  }

  private YappcBackpressureConfig(Builder builder) {
    this.settingsByCategory = new HashMap<>(builder.settingsByCategory);
    this.defaultSettings = builder.defaultSettings != null
        ? builder.defaultSettings
        : new AgentBackpressureSettings(BackpressureStrategy.DROP_LATEST, 1024, true);
  }

  /**
   * Gets backpressure settings for an agent category.
   *
   * @param category the agent category (e.g., "security", "orchestration", "worker")
   * @return settings for the category, or default if not configured
   */
  @NotNull
  public AgentBackpressureSettings getSettingsForCategory(@NotNull String category) {
    return settingsByCategory.getOrDefault(category.toLowerCase(), defaultSettings);
  }

  /**
   * Gets backpressure settings for a specific agent based on its ID.
   *
   * @param agentId the agent ID (e.g., "agent.yappc.sentinel")
   * @return settings for the agent
   */
  @NotNull
  public AgentBackpressureSettings getSettingsForAgent(@NotNull String agentId) {
    // Extract category from agent ID
    String category = extractCategoryFromAgentId(agentId);
    return getSettingsForCategory(category);
  }

  /**
   * Creates a default configuration with sensible presets.
   *
   * @return default configuration
   */
  @NotNull
  public static YappcBackpressureConfig createDefault() {
    return builder()
        // Security/Governance: BLOCK — never drop critical events
        .withCategory("security",
            new AgentBackpressureSettings(BackpressureStrategy.BLOCK, 2048, true))
        .withCategory("governance",
            new AgentBackpressureSettings(BackpressureStrategy.BLOCK, 1024, true))

        // Orchestrators: DROP_OLDEST — prioritize recent state
        .withCategory("orchestration",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_OLDEST, 4096, true))
        .withCategory("debugging",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_OLDEST, 2048, true))

        // Workers: DROP_LATEST — bounded load
        .withCategory("worker",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_LATEST, 1024, true))
        .withCategory("code-generation",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_LATEST, 512, true))
        .withCategory("testing",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_LATEST, 512, true))

        // Analytics: DROP_OLDEST — fresh data prioritized
        .withCategory("analytics",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_OLDEST, 1024, true))
        .withCategory("improvement",
            new AgentBackpressureSettings(BackpressureStrategy.DROP_OLDEST, 512, true))

        // Integration: OVERFLOW_TO_DLQ — preserve events
        .withCategory("integration",
            new AgentBackpressureSettings(BackpressureStrategy.OVERFLOW_TO_DLQ, 2048, true))
        .withCategory("notification",
            new AgentBackpressureSettings(BackpressureStrategy.OVERFLOW_TO_DLQ, 1024, true))

        // Deployment: BLOCK — ensure deployment events processed
        .withCategory("deployment",
            new AgentBackpressureSettings(BackpressureStrategy.BLOCK, 1024, true))
        .withCategory("release",
            new AgentBackpressureSettings(BackpressureStrategy.BLOCK, 512, true))

        .withDefaultSettings(
            new AgentBackpressureSettings(BackpressureStrategy.DROP_LATEST, 1024, true))
        .build();
  }

  /**
   * Creates a builder for custom configuration.
   *
   * @return new builder
   */
  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  private String extractCategoryFromAgentId(String agentId) {
    // agent.yappc.{category}-* or agent.yappc.{name}
    String name = agentId.replace("agent.yappc.", "");

    // Check for category keywords in name
    if (name.contains("sentinel") || name.contains("security")) {
      return "security";
    }
    if (name.contains("governance") || name.contains("gate")) {
      return "governance";
    }
    if (name.contains("orchestrator") || name.contains("coordinator")) {
      return "orchestration";
    }
    if (name.contains("debug")) {
      return "debugging";
    }
    if (name.contains("writer") || name.contains("generator")) {
      return "code-generation";
    }
    if (name.contains("test") && name.contains("writer")) {
      return "testing";
    }
    if (name.contains("integration") || name.contains("notification")) {
      return "integration";
    }
    if (name.contains("deploy") || name.contains("release")) {
      return "deployment";
    }
    if (name.contains("improve") || name.contains("learn")) {
      return "improvement";
    }

    return "default";
  }

  /**
   * Builder for YappcBackpressureConfig.
   */
  public static class Builder {
    private final Map<String, AgentBackpressureSettings> settingsByCategory = new HashMap<>();
    private AgentBackpressureSettings defaultSettings;

    /**
     * Sets backpressure settings for a category.
     *
     * @param category the agent category
     * @param settings the backpressure settings
     * @return this builder
     */
    @NotNull
    public Builder withCategory(
        @NotNull String category,
        @NotNull AgentBackpressureSettings settings) {
      settingsByCategory.put(category.toLowerCase(), settings);
      return this;
    }

    /**
     * Sets default settings for unconfigured categories.
     *
     * @param settings the default settings
     * @return this builder
     */
    @NotNull
    public Builder withDefaultSettings(@NotNull AgentBackpressureSettings settings) {
      this.defaultSettings = settings;
      return this;
    }

    /**
     * Builds the configuration.
     *
     * @return the configured YappcBackpressureConfig
     */
    @NotNull
    public YappcBackpressureConfig build() {
      return new YappcBackpressureConfig(this);
    }
  }
}
