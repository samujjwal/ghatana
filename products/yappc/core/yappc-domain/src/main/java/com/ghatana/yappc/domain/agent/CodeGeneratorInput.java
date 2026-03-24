package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the Code Generator Agent.
 *
 * @doc.type record
 * @doc.purpose Code generation input
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record CodeGeneratorInput(
        @NotNull String itemId,
        @NotNull GenerationType generationType,
        @Nullable String language,
        @Nullable String framework,
        @Nullable String description,
        @Nullable List<String> requirements,
        @Nullable Map<String, Object> context,
        @Nullable CodeStyle codeStyle,
        @Nullable TestingConfig testingConfig
) {
    /**
     * Code generation types.
     */
    public enum GenerationType {
        SCAFFOLD,       // Directory structure and boilerplate
        IMPLEMENTATION, // Full implementation
        TEST,           // Test cases only
        DOCUMENTATION,  // Comments and README
        REFACTOR,       // Refactoring suggestions
        MIGRATION       // Migration scripts
    }

    /**
     * Code style preferences.
     */
    public record CodeStyle(
            @Nullable String indentation,
            @Nullable Boolean useSemicolons,
            @Nullable String quoteStyle,
            @Nullable Integer maxLineLength,
            @Nullable String namingConvention
    ) {}

    /**
     * Testing configuration.
     */
    public record TestingConfig(
            @NotNull String framework,
            @Nullable Boolean includeIntegration,
            @Nullable Boolean includeMocks,
            @Nullable Double coverageTarget
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String itemId;
        private GenerationType generationType = GenerationType.IMPLEMENTATION;
        private String language;
        private String framework;
        private String description;
        private List<String> requirements;
        private Map<String, Object> context;
        private CodeStyle codeStyle;
        private TestingConfig testingConfig;

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder generationType(GenerationType generationType) {
            this.generationType = generationType;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requirements(List<String> requirements) {
            this.requirements = requirements;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder codeStyle(CodeStyle codeStyle) {
            this.codeStyle = codeStyle;
            return this;
        }

        public Builder testingConfig(TestingConfig testingConfig) {
            this.testingConfig = testingConfig;
            return this;
        }

        public CodeGeneratorInput build() {
            if (itemId == null) {
                throw new IllegalStateException("itemId is required");
            }
            return new CodeGeneratorInput(
                    itemId, generationType, language, framework,
                    description, requirements, context, codeStyle, testingConfig
            );
        }
    }
}
