package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the Recommendation Agent.
 *
 * @doc.type record
 * @doc.purpose Recommendation input
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record RecommendationInput(
        @NotNull RecommendationType type,
        @NotNull String userId,
        @Nullable String workspaceId,
        @Nullable String itemId,
        @Nullable String currentContext,
        @Nullable List<String> recentActions,
        @Nullable Map<String, Object> filters,
        int maxResults
) {
    /**
     * Types of recommendations.
     */
    public enum RecommendationType {
        ASSIGNEE,           // Suggest who to assign
        TAG,                // Suggest tags
        PRIORITY,           // Suggest priority
        PHASE,              // Suggest phase
        SIMILAR_ITEMS,      // Find similar items
        NEXT_ACTION,        // Suggest next action
        WORKFLOW_TEMPLATE,  // Suggest workflow template
        COLLABORATOR,       // Suggest collaborators
        RESOURCE,           // Suggest resources/docs
        TIME_ESTIMATE       // Suggest time estimate
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private RecommendationType type;
        private String userId;
        private String workspaceId;
        private String itemId;
        private String currentContext;
        private List<String> recentActions;
        private Map<String, Object> filters;
        private int maxResults = 5;

        public Builder type(RecommendationType type) {
            this.type = type;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder currentContext(String currentContext) {
            this.currentContext = currentContext;
            return this;
        }

        public Builder recentActions(List<String> recentActions) {
            this.recentActions = recentActions;
            return this;
        }

        public Builder filters(Map<String, Object> filters) {
            this.filters = filters;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public RecommendationInput build() {
            if (type == null) {
                throw new IllegalStateException("type is required");
            }
            if (userId == null) {
                throw new IllegalStateException("userId is required");
            }
            return new RecommendationInput(
                    type, userId, workspaceId, itemId,
                    currentContext, recentActions, filters, maxResults
            );
        }
    }
}
