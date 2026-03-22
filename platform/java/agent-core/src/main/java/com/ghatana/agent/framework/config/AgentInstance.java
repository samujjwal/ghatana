/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.FailureMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tenant-scoped, mutable runtime configuration for a deployed agent.
 *
 * <p>An {@code AgentInstance} pairs an immutable {@link AgentDefinition} with
 * tenant-specific overrides — model selection, rate limits, feature flags,
 * and cost budgets. It supports <b>hot reload</b>: overrides can be updated
 * without restarting the agent.
 *
 * <h2>Relationship to AgentDefinition</h2>
 * <pre>{@code
 * AgentDefinition (immutable blueprint)
 *   └── AgentInstance (tenant=acme, overrides: model=gpt-4o, rateLimit=100/s)
 *   └── AgentInstance (tenant=globex, overrides: model=claude-3, rateLimit=50/s)
 * }</pre>
 *
 * <h2>Override Semantics</h2>
 * <ul>
 *   <li>Scalars (timeout, model, temperature): override replaces definition value</li>
 *   <li>Maps (labels, featureFlags): shallow merge with definition</li>
 *   <li>Null override value → fall back to definition default</li>
 * </ul>
 *
 * <h2>Hot Reload</h2>
 * Call {@link #applyOverrides(Overrides)} to update runtime configuration
 * atomically without downtime. The underlying {@link AgentDefinition} is
 * never modified.
 *
 * @doc.type class
 * @doc.purpose Tenant-scoped mutable agent runtime configuration
 * @doc.layer platform
 * @doc.pattern ValueObject
 *
 * @author Ghatana AI Platform
 * @since 2.4.0
 */
public final class AgentInstance {

    private final String instanceId;
    private final String tenantId;
    private final AgentDefinition definition;
    private final Instant createdAt;

    private final AtomicReference<Overrides> activeOverrides;
    private volatile Instant lastUpdatedAt;

    private AgentInstance(Builder builder) {
        this.instanceId = Objects.requireNonNull(builder.instanceId, "instanceId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.definition = Objects.requireNonNull(builder.definition, "definition must not be null");
        this.createdAt = Instant.now();
        this.lastUpdatedAt = this.createdAt;
        this.activeOverrides = new AtomicReference<>(
                builder.overrides != null ? builder.overrides : Overrides.EMPTY
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Identity
    // ═══════════════════════════════════════════════════════════════════════════

    /** Unique instance identifier (e.g., "acme:fraud-detector:2.1.0"). */
    @NotNull
    public String getInstanceId() { return instanceId; }

    /** Tenant this instance belongs to. */
    @NotNull
    public String getTenantId() { return tenantId; }

    /** The underlying immutable agent definition. */
    @NotNull
    public AgentDefinition getDefinition() { return definition; }

    @NotNull
    public Instant getCreatedAt() { return createdAt; }

    @NotNull
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resolved Configuration (definition + overrides)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Effective model name: override → definition metadata "model" → null. */
    @Nullable
    public String getEffectiveModel() {
        String override = activeOverrides.get().modelOverride;
        if (override != null) return override;
        Object defModel = definition.getMetadata().get("model");
        return defModel != null ? defModel.toString() : null;
    }

    /** Effective timeout: override → definition timeout. */
    @NotNull
    public Duration getEffectiveTimeout() {
        Duration override = activeOverrides.get().timeoutOverride;
        return override != null ? override : definition.getTimeout();
    }

    /** Effective temperature: override → definition temperature. */
    public double getEffectiveTemperature() {
        Double override = activeOverrides.get().temperatureOverride;
        return override != null ? override : definition.getTemperature();
    }

    /** Effective max tokens: override → definition maxTokens. */
    public int getEffectiveMaxTokens() {
        Integer override = activeOverrides.get().maxTokensOverride;
        return override != null ? override : definition.getMaxTokens();
    }

    /** Effective failure mode: override → definition failure mode. */
    @NotNull
    public FailureMode getEffectiveFailureMode() {
        FailureMode override = activeOverrides.get().failureModeOverride;
        return override != null ? override : definition.getFailureMode();
    }

    /** Effective rate limit (requests/second). 0 = unlimited. */
    public int getEffectiveRateLimit() {
        return activeOverrides.get().rateLimitPerSecond;
    }

    /** Effective max cost per call in dollars. */
    public double getEffectiveMaxCostPerCall() {
        Double override = activeOverrides.get().maxCostPerCallOverride;
        return override != null ? override : definition.getMaxCostPerCall();
    }

    /** Merged labels: definition labels + override labels (override wins on conflict). */
    @NotNull
    public Map<String, String> getEffectiveLabels() {
        Map<String, String> merged = new LinkedHashMap<>(definition.getLabels());
        merged.putAll(activeOverrides.get().labelOverrides);
        return Collections.unmodifiableMap(merged);
    }

    /** Feature flags for this instance. */
    @NotNull
    public Map<String, Boolean> getFeatureFlags() {
        return activeOverrides.get().featureFlags;
    }

    /** Check if a feature flag is enabled (default: false). */
    public boolean isFeatureEnabled(String flag) {
        return activeOverrides.get().featureFlags.getOrDefault(flag, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Hot Reload
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Atomically applies new overrides without downtime.
     *
     * @param newOverrides the new overrides to apply
     */
    public void applyOverrides(@NotNull Overrides newOverrides) {
        Objects.requireNonNull(newOverrides, "overrides must not be null");
        activeOverrides.set(newOverrides);
        lastUpdatedAt = Instant.now();
    }

    /** Returns the currently active overrides. */
    @NotNull
    public Overrides getActiveOverrides() {
        return activeOverrides.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Overrides Record
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tenant-specific overrides that modify the base {@link AgentDefinition}.
     *
     * <p>All fields are nullable — null means "use the definition default".
     */
    public static final class Overrides {

        public static final Overrides EMPTY = new Overrides(new OverridesBuilder());

        final String modelOverride;
        final Duration timeoutOverride;
        final Double temperatureOverride;
        final Integer maxTokensOverride;
        final FailureMode failureModeOverride;
        final int rateLimitPerSecond;
        final Double maxCostPerCallOverride;
        final Map<String, String> labelOverrides;
        final Map<String, Boolean> featureFlags;

        private Overrides(OverridesBuilder b) {
            this.modelOverride = b.modelOverride;
            this.timeoutOverride = b.timeoutOverride;
            this.temperatureOverride = b.temperatureOverride;
            this.maxTokensOverride = b.maxTokensOverride;
            this.failureModeOverride = b.failureModeOverride;
            this.rateLimitPerSecond = b.rateLimitPerSecond;
            this.maxCostPerCallOverride = b.maxCostPerCallOverride;
            this.labelOverrides = Map.copyOf(b.labelOverrides);
            this.featureFlags = Map.copyOf(b.featureFlags);
        }

        public static OverridesBuilder builder() {
            return new OverridesBuilder();
        }
    }

    /**
     * Builder for {@link Overrides}.
     */
    public static final class OverridesBuilder {
        private String modelOverride;
        private Duration timeoutOverride;
        private Double temperatureOverride;
        private Integer maxTokensOverride;
        private FailureMode failureModeOverride;
        private int rateLimitPerSecond;
        private Double maxCostPerCallOverride;
        private final Map<String, String> labelOverrides = new LinkedHashMap<>();
        private final Map<String, Boolean> featureFlags = new LinkedHashMap<>();

        private OverridesBuilder() {}

        public OverridesBuilder model(String model) { this.modelOverride = model; return this; }
        public OverridesBuilder timeout(Duration timeout) { this.timeoutOverride = timeout; return this; }
        public OverridesBuilder temperature(double temperature) { this.temperatureOverride = temperature; return this; }
        public OverridesBuilder maxTokens(int maxTokens) { this.maxTokensOverride = maxTokens; return this; }
        public OverridesBuilder failureMode(FailureMode mode) { this.failureModeOverride = mode; return this; }
        public OverridesBuilder rateLimitPerSecond(int rps) { this.rateLimitPerSecond = rps; return this; }
        public OverridesBuilder maxCostPerCall(double cost) { this.maxCostPerCallOverride = cost; return this; }
        public OverridesBuilder label(String key, String value) { this.labelOverrides.put(key, value); return this; }
        public OverridesBuilder featureFlag(String flag, boolean enabled) { this.featureFlags.put(flag, enabled); return this; }

        public Overrides build() {
            return new Overrides(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String instanceId;
        private String tenantId;
        private AgentDefinition definition;
        private Overrides overrides;

        private Builder() {}

        public Builder instanceId(String instanceId) { this.instanceId = instanceId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder definition(AgentDefinition definition) { this.definition = definition; return this; }
        public Builder overrides(Overrides overrides) { this.overrides = overrides; return this; }

        /**
         * Auto-generates instanceId as "{tenantId}:{definitionId}:{version}".
         */
        public Builder autoInstanceId() {
            if (tenantId != null && definition != null) {
                this.instanceId = tenantId + ":" + definition.getCanonicalId();
            }
            return this;
        }

        public AgentInstance build() {
            if (instanceId == null && tenantId != null && definition != null) {
                autoInstanceId();
            }
            return new AgentInstance(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentInstance that)) return false;
        return instanceId.equals(that.instanceId);
    }

    @Override
    public int hashCode() {
        return instanceId.hashCode();
    }

    @Override
    public String toString() {
        return "AgentInstance{" + instanceId + ", tenant=" + tenantId
                + ", definition=" + definition.getCanonicalId() + "}";
    }
}
