package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the Copilot Agent.
 *
 * @doc.type record
 * @doc.purpose Copilot agent input
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record CopilotInput(
        @NotNull String query,
        @Nullable String sessionId,
        @NotNull CopilotViewContext currentView,
        @Nullable List<String> selectedItems,
        @Nullable List<CopilotRecentAction> recentActions,
        @Nullable Map<String, Object> additionalContext
) {

    /**
     * Current view context.
     */
    public record CopilotViewContext(
            @NotNull String route,
            @Nullable String phaseId,
            @Nullable String viewMode,
            @Nullable Map<String, Object> filters
    ) {
        public static CopilotViewContext of(String route) {
            return new CopilotViewContext(route, null, null, null);
        }
    }

    /**
     * Recent user action.
     */
    public record CopilotRecentAction(
            @NotNull String type,
            @NotNull String description,
            @NotNull String timestamp,
            @Nullable Map<String, Object> details
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String query;
        private String sessionId;
        private CopilotViewContext currentView;
        private List<String> selectedItems;
        private List<CopilotRecentAction> recentActions;
        private Map<String, Object> additionalContext;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder currentView(CopilotViewContext currentView) {
            this.currentView = currentView;
            return this;
        }

        public Builder selectedItems(List<String> selectedItems) {
            this.selectedItems = selectedItems;
            return this;
        }

        public Builder recentActions(List<CopilotRecentAction> recentActions) {
            this.recentActions = recentActions;
            return this;
        }

        public Builder additionalContext(Map<String, Object> additionalContext) {
            this.additionalContext = additionalContext;
            return this;
        }

        public CopilotInput build() {
            return new CopilotInput(
                    query,
                    sessionId,
                    currentView,
                    selectedItems,
                    recentActions,
                    additionalContext
            );
        }
    }
}
