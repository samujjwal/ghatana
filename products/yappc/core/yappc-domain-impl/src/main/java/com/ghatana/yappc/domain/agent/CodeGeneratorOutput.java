package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Output from the Code Generator Agent.
 *
 * @doc.type record
 * @doc.purpose Code generation output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record CodeGeneratorOutput(
        @NotNull List<GeneratedFile> files,
        @Nullable List<GeneratedTest> tests,
        @Nullable String documentation,
        @NotNull CodeQualityScore qualityScore,
        @NotNull List<SecurityIssue> securityIssues,
        @NotNull GenerationMetadata metadata
) {
    /**
     * A generated file.
     */
    public record GeneratedFile(
            @NotNull String path,
            @NotNull String content,
            @NotNull String language,
            @NotNull FileType type,
            @Nullable String description,
            int lineCount
    ) {
        public enum FileType {
            SOURCE,
            TEST,
            CONFIG,
            DOCUMENTATION,
            MIGRATION,
            ASSET
        }
    }

    /**
     * A generated test case.
     */
    public record GeneratedTest(
            @NotNull String name,
            @NotNull String description,
            @NotNull String code,
            @NotNull TestType type,
            @Nullable List<String> coveredMethods
    ) {
        public enum TestType {
            UNIT,
            INTEGRATION,
            E2E,
            PERFORMANCE,
            SECURITY
        }
    }

    /**
     * Code quality assessment.
     */
    public record CodeQualityScore(
            double overall,
            double maintainability,
            double readability,
            double testability,
            double security,
            @NotNull List<QualityIssue> issues
    ) {
        public record QualityIssue(
                @NotNull String type,
                @NotNull String severity,
                @NotNull String message,
                @Nullable String file,
                @Nullable Integer line,
                @Nullable String suggestion
        ) {}
    }

    /**
     * Security issue found in generated code.
     */
    public record SecurityIssue(
            @NotNull String type,
            @NotNull Severity severity,
            @NotNull String description,
            @Nullable String file,
            @Nullable Integer line,
            @Nullable String remediation,
            @Nullable String cweId
    ) {
        public enum Severity {
            INFO,
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }

    /**
     * Metadata about the generation.
     */
    public record GenerationMetadata(
            @NotNull String modelUsed,
            @NotNull String modelVersion,
            int tokensUsed,
            long generationTimeMs,
            @NotNull String language,
            @Nullable String framework,
            @NotNull Map<String, Object> parameters
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<GeneratedFile> files = List.of();
        private List<GeneratedTest> tests;
        private String documentation;
        private CodeQualityScore qualityScore;
        private List<SecurityIssue> securityIssues = List.of();
        private GenerationMetadata metadata;

        public Builder files(List<GeneratedFile> files) {
            this.files = files;
            return this;
        }

        public Builder tests(List<GeneratedTest> tests) {
            this.tests = tests;
            return this;
        }

        public Builder documentation(String documentation) {
            this.documentation = documentation;
            return this;
        }

        public Builder qualityScore(CodeQualityScore qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public Builder securityIssues(List<SecurityIssue> securityIssues) {
            this.securityIssues = securityIssues;
            return this;
        }

        public Builder metadata(GenerationMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public CodeGeneratorOutput build() {
            if (qualityScore == null) {
                qualityScore = new CodeQualityScore(0, 0, 0, 0, 0, List.of());
            }
            if (metadata == null) {
                throw new IllegalStateException("metadata is required");
            }
            return new CodeGeneratorOutput(
                    files, tests, documentation, qualityScore, securityIssues, metadata
            );
        }
    }
}
