package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Output from the Copilot Agent.
 *
 * @doc.type record
 * @doc.purpose Copilot agent output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record CopilotOutput(
        @NotNull String response,
        @Nullable CopilotAction action,
        @Nullable ActionExecutionResult executionResult,
        @NotNull List<String> suggestions
) {

    /**
     * A suggested action from the copilot.
     */
    public record CopilotAction(
            @NotNull String type,
            @NotNull String description,
            @NotNull ActionImpact impact,
            double confidence,
            @NotNull Map<String, Object> parameters,
            boolean requiresConfirmation
    ) {
        /**
         * Impact level of an action.
         */
        public enum ActionImpact {
            LOW,
            MEDIUM,
            HIGH
        }

        public static CopilotAction navigation(String route, double confidence) {
            return new CopilotAction(
                    "navigate",
                    "Navigate to " + route,
                    ActionImpact.LOW,
                    confidence,
                    Map.of("route", route),
                    false
            );
        }

        public static CopilotAction filter(Map<String, Object> filters, double confidence) {
            return new CopilotAction(
                    "filter",
                    "Apply filters",
                    ActionImpact.LOW,
                    confidence,
                    filters,
                    false
            );
        }

        public static CopilotAction create(String itemType, Map<String, Object> data, double confidence) {
            return new CopilotAction(
                    "create",
                    "Create " + itemType,
                    ActionImpact.MEDIUM,
                    confidence,
                    data,
                    true
            );
        }

        public static CopilotAction update(String itemId, Map<String, Object> changes, double confidence) {
            return new CopilotAction(
                    "update",
                    "Update item " + itemId,
                    ActionImpact.MEDIUM,
                    confidence,
                    Map.of("itemId", itemId, "changes", changes),
                    true
            );
        }

        public static CopilotAction delete(String itemId, double confidence) {
            return new CopilotAction(
                    "delete",
                    "Delete item " + itemId,
                    ActionImpact.HIGH,
                    confidence,
                    Map.of("itemId", itemId),
                    true
            );
        }
    }

    /**
     * Result of executing an action.
     */
    public record ActionExecutionResult(
            boolean success,
            @Nullable String message,
            @Nullable Map<String, Object> data
    ) {
        public static ActionExecutionResult successful() {
            return new ActionExecutionResult(true, null, null);
        }

        public static ActionExecutionResult successful(String message) {
            return new ActionExecutionResult(true, message, null);
        }

        public static ActionExecutionResult successful(String message, Map<String, Object> data) {
            return new ActionExecutionResult(true, message, data);
        }

        public static ActionExecutionResult failure(String message) {
            return new ActionExecutionResult(false, message, null);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String response;
        private CopilotAction action;
        private ActionExecutionResult executionResult;
        private List<String> suggestions = List.of();

        public Builder response(String response) {
            this.response = response;
            return this;
        }

        public Builder action(CopilotAction action) {
            this.action = action;
            return this;
        }

        public Builder executionResult(ActionExecutionResult executionResult) {
            this.executionResult = executionResult;
            return this;
        }

        public Builder suggestions(List<String> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public CopilotOutput build() {
            return new CopilotOutput(response, action, executionResult, suggestions);
        }
    }
}
