package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User AI preferences for agent behavior customization.
 *
 * @doc.type record
 * @doc.purpose User AI preferences
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record UserAIPreferences(
        boolean enableAISuggestions,
        boolean enablePredictions,
        boolean enableCopilot,
        @NotNull String preferredModel,
        double temperature,
        int maxTokens,
        boolean autoAcceptLowConfidence,
        @NotNull NotificationLevel notificationLevel,
        @NotNull Set<AgentName> excludedAgents
) {

    /**
     * Notification level for AI alerts.
     */
    public enum NotificationLevel {
        ALL,
        IMPORTANT,
        CRITICAL,
        NONE
    }

    /**
     * Default user AI preferences.
     */
    public static UserAIPreferences defaults() {
        return new UserAIPreferences(
                true,
                true,
                true,
                "gpt-4-turbo",
                0.7,
                2048,
                false,
                NotificationLevel.IMPORTANT,
                Set.of()
        );
    }

    /**
     * Builder for UserAIPreferences.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enableAISuggestions = true;
        private boolean enablePredictions = true;
        private boolean enableCopilot = true;
        private String preferredModel = "gpt-4-turbo";
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private boolean autoAcceptLowConfidence = false;
        private NotificationLevel notificationLevel = NotificationLevel.IMPORTANT;
        private Set<AgentName> excludedAgents = Set.of();

        public Builder enableAISuggestions(boolean enableAISuggestions) {
            this.enableAISuggestions = enableAISuggestions;
            return this;
        }

        public Builder enablePredictions(boolean enablePredictions) {
            this.enablePredictions = enablePredictions;
            return this;
        }

        public Builder enableCopilot(boolean enableCopilot) {
            this.enableCopilot = enableCopilot;
            return this;
        }

        public Builder preferredModel(String preferredModel) {
            this.preferredModel = preferredModel;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder autoAcceptLowConfidence(boolean autoAcceptLowConfidence) {
            this.autoAcceptLowConfidence = autoAcceptLowConfidence;
            return this;
        }

        public Builder notificationLevel(NotificationLevel notificationLevel) {
            this.notificationLevel = notificationLevel;
            return this;
        }

        public Builder excludedAgents(Set<AgentName> excludedAgents) {
            this.excludedAgents = excludedAgents;
            return this;
        }

        public UserAIPreferences build() {
            return new UserAIPreferences(
                    enableAISuggestions,
                    enablePredictions,
                    enableCopilot,
                    preferredModel,
                    temperature,
                    maxTokens,
                    autoAcceptLowConfidence,
                    notificationLevel,
                    excludedAgents
            );
        }
    }
}
