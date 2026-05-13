package com.ghatana.agent.framework.memory;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

/**
 * Filter for querying memory.
 *
 * @doc.type class
 * @doc.purpose Memory query filter
 * @doc.layer framework
 * @doc.pattern Specification
 */
public final class MemoryFilter {

    private final Instant startTime;
    private final Instant endTime;
    private final List<String> tags;
    private final String agentId;
    private final String turnId;
    private final String skillId;
    private final MasteryState masteryState;
    private final VersionContext versionContext;
    private final Duration freshnessThreshold;
    private final String tenantId;

    private MemoryFilter(Builder builder) {
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.tags = builder.tags != null ? List.copyOf(builder.tags) : List.of();
        this.agentId = builder.agentId;
        this.turnId = builder.turnId;
        this.skillId = builder.skillId;
        this.masteryState = builder.masteryState;
        this.versionContext = builder.versionContext;
        this.freshnessThreshold = builder.freshnessThreshold;
        this.tenantId = builder.tenantId;
    }

    @Nullable
    public Instant getStartTime() {
        return startTime;
    }

    @Nullable
    public Instant getEndTime() {
        return endTime;
    }

    @NotNull
    public List<String> getTags() {
        return tags;
    }

    @Nullable
    public String getAgentId() {
        return agentId;
    }

    @Nullable
    public String getTurnId() {
        return turnId;
    }

    @Nullable
    public String getSkillId() {
        return skillId;
    }

    @Nullable
    public MasteryState getMasteryState() {
        return masteryState;
    }

    @Nullable
    public VersionContext getVersionContext() {
        return versionContext;
    }

    @Nullable
    public Duration getFreshnessThreshold() {
        return freshnessThreshold;
    }

    @Nullable
    public String getTenantId() {
        return tenantId;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant startTime;
        private Instant endTime;
        private List<String> tags;
        private String agentId;
        private String turnId;
        private String skillId;
        private MasteryState masteryState;
        private VersionContext versionContext;
        private Duration freshnessThreshold;
        private String tenantId;

        private Builder() {}

        public Builder startTime(@Nullable Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(@Nullable Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder tags(@NotNull List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder agentId(@Nullable String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder turnId(@Nullable String turnId) {
            this.turnId = turnId;
            return this;
        }

        public Builder skillId(@Nullable String skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder masteryState(@Nullable MasteryState masteryState) {
            this.masteryState = masteryState;
            return this;
        }

        public Builder versionContext(@Nullable VersionContext versionContext) {
            this.versionContext = versionContext;
            return this;
        }

        public Builder freshnessThreshold(@Nullable Duration freshnessThreshold) {
            this.freshnessThreshold = freshnessThreshold;
            return this;
        }

        public Builder tenantId(@Nullable String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        @NotNull
        public MemoryFilter build() {
            return new MemoryFilter(this);
        }
    }
}
