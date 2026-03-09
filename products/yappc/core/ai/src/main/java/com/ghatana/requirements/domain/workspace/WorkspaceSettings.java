package com.ghatana.requirements.domain.workspace;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Workspace configuration settings.
 *
 * <p><b>Purpose</b><br>
 * Holds workspace-level configuration options for features and behaviors.
 * All settings are immutable and validated.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkspaceSettings settings = new WorkspaceSettings(
 *     true,           // AI suggestions enabled
 *     false,          // Approval not required
 *     "scrum",        // Project template
 *     Map.of(
 *         "max_projects", 50,
 *         "retention_days", 90
 *     )
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable. Safe for concurrent access.
 *
 * @doc.type record
 * @doc.purpose Workspace configuration holder
 * @doc.layer product
 * @doc.pattern Configuration
 */
public record WorkspaceSettings(
    boolean aiSuggestionsEnabled,
    boolean requireApprovalForRequirements,
    String defaultProjectTemplate,
    Map<String, Object> customSettings
) {
    /**
     * Creates workspace settings with validation.
     *
     * @param aiSuggestionsEnabled Enable AI suggestion features
     * @param requireApprovalForRequirements Require approval workflow for requirements
     * @param defaultProjectTemplate Default project template (cannot be null/blank)
     * @param customSettings Custom settings map (will be defensively copied)
     * @throws NullPointerException if required fields are null
     * @throws IllegalArgumentException if template is blank
     */
    public WorkspaceSettings {
        Objects.requireNonNull(defaultProjectTemplate, "defaultProjectTemplate cannot be null");
        Objects.requireNonNull(customSettings, "customSettings cannot be null");

        if (defaultProjectTemplate.isBlank()) {
            throw new IllegalArgumentException("defaultProjectTemplate cannot be blank");
        }

        // Defensive copy of custom settings
        customSettings = Collections.unmodifiableMap(new HashMap<>(customSettings));
    }

    /**
     * Returns default workspace settings.
     *
     * <p>Default settings:
     * - AI suggestions enabled
     * - No approval required
     * - Default project template
     * - No custom settings
     *
     * @return Default WorkspaceSettings instance
     */
    public static WorkspaceSettings defaults() {
        return new WorkspaceSettings(
            true,       // AI suggestions enabled by default
            false,      // No approval required by default
            "default",  // Default template
            Map.of()    // No custom settings
        );
    }

    /**
     * Create builder for settings.
     *
     * @return new settings builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WorkspaceSettings.
     */
    public static final class Builder {
        private boolean aiSuggestionsEnabled = true;
        private boolean requireApprovalForRequirements = false;
        private String defaultProjectTemplate = "default";
        private Map<String, Object> customSettings = new HashMap<>();

        public Builder aiSuggestionsEnabled(boolean enabled) {
            this.aiSuggestionsEnabled = enabled;
            return this;
        }

        public Builder requireApprovalForRequirements(boolean required) {
            this.requireApprovalForRequirements = required;
            return this;
        }

        public Builder defaultProjectTemplate(String template) {
            this.defaultProjectTemplate = template;
            return this;
        }

        public Builder customSetting(String key, Object value) {
            this.customSettings.put(key, value);
            return this;
        }

        public Builder customSettings(Map<String, Object> settings) {
            this.customSettings = new HashMap<>(settings);
            return this;
        }

        /**
         * Build the settings.
         *
         * @return WorkspaceSettings instance
         */
        public WorkspaceSettings build() {
            return new WorkspaceSettings(
                aiSuggestionsEnabled,
                requireApprovalForRequirements,
                defaultProjectTemplate,
                customSettings
            );
        }
    }
}