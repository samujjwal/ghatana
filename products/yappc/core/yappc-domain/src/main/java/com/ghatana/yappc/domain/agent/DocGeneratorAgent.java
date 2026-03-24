package com.ghatana.products.yappc.domain.agent;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Documentation Generator Agent - creates comprehensive documentation.
 * <p>
 * Uses Claude-3 for high-quality documentation generation including
 * READMEs, API docs, changelogs, and technical specifications.
 *
 * @doc.type class
 * @doc.purpose AI-powered documentation generation
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DocGeneratorAgent extends AbstractAIAgent<DocGeneratorAgent.DocInput, DocGeneratorAgent.DocOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(DocGeneratorAgent.class);

        private static final String VERSION = "1.0.0";
        private static final String DESCRIPTION = "AI-powered documentation generation for READMEs, APIs, changelogs, and specs";
        private static final List<String> CAPABILITIES = List.of(
            "documentation",
            "readme",
            "api-docs",
            "changelog",
            "specs"
        );
        private static final List<String> SUPPORTED_MODELS = List.of(
            "claude-3-sonnet",
            "claude-3-opus",
            "gpt-4"
        );

    private final LLMGateway llmGateway;
    private final TemplateService templateService;

    public DocGeneratorAgent(
            @NotNull MetricsCollector metricsCollector,
            @NotNull LLMGateway llmGateway,
            @NotNull TemplateService templateService
    ) {
        super(
                AgentName.DOC_GENERATOR_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.llmGateway = llmGateway;
        this.templateService = templateService;
    }

    @Override
    public void validateInput(@NotNull DocInput input) {
        if (input.docType() == null) {
            throw new IllegalArgumentException("docType is required");
        }
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return Promise.of(Map.of(
                "llmGateway", AgentHealth.DependencyStatus.HEALTHY,
                "templateService", AgentHealth.DependencyStatus.HEALTHY
        ));
    }

    @Override
    protected @NotNull Promise<ProcessResult<DocOutput>> processRequest(
            @NotNull DocInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.info("Generating {} documentation", input.docType());
        long startTime = System.currentTimeMillis();

        return switch (input.docType()) {
            case README -> generateReadme(input, startTime);
            case API_REFERENCE -> generateApiDocs(input, startTime);
            case CHANGELOG -> generateChangelog(input, startTime);
            case TECHNICAL_SPEC -> generateTechnicalSpec(input, startTime);
            case USER_GUIDE -> generateUserGuide(input, startTime);
            case ARCHITECTURE -> generateArchitectureDoc(input, startTime);
            case RELEASE_NOTES -> generateReleaseNotes(input, startTime);
            case RUNBOOK -> generateRunbook(input, startTime);
        };
    }

    private Promise<ProcessResult<DocOutput>> generateReadme(DocInput input, long startTime) {
        String systemPrompt = """
                You are an expert technical writer. Generate a comprehensive README.md file.
                
                Include the following sections:
                1. Project title and badges
                2. Brief description
                3. Features list
                4. Prerequisites
                5. Installation instructions
                6. Quick start / Usage examples
                7. Configuration options
                8. API overview (if applicable)
                9. Contributing guidelines
                10. License
                
                Use clear, concise language. Include code examples where appropriate.
                Format with proper Markdown including headers, code blocks, tables, and lists.
                """;

        return generateDocument(input, systemPrompt, "readme", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateApiDocs(DocInput input, long startTime) {
        String systemPrompt = """
                You are an expert API documentation writer. Generate comprehensive API reference documentation.
                
                For each endpoint/method include:
                1. Description and purpose
                2. HTTP method and path (for REST) or method signature
                3. Request parameters with types and descriptions
                4. Request body schema (if applicable)
                5. Response schema with examples
                6. Error codes and handling
                7. Authentication requirements
                8. Rate limiting information
                9. Code examples in multiple languages
                
                Use OpenAPI/Swagger-compatible format where possible.
                Include curl examples and SDK usage.
                """;

        return generateDocument(input, systemPrompt, "api-docs", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateChangelog(DocInput input, long startTime) {
        String systemPrompt = """
                You are a technical writer specializing in changelogs. Generate a CHANGELOG.md following Keep a Changelog format.
                
                Structure:
                1. Version header with date
                2. Sections: Added, Changed, Deprecated, Removed, Fixed, Security
                3. Clear, user-focused descriptions
                4. Link to issues/PRs where applicable
                
                Be concise but informative. Group related changes together.
                Follow Semantic Versioning principles in descriptions.
                """;

        return generateDocument(input, systemPrompt, "changelog", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateTechnicalSpec(DocInput input, long startTime) {
        String systemPrompt = """
                You are a technical architect. Generate a detailed technical specification document.
                
                Include:
                1. Overview and objectives
                2. System requirements
                3. Architecture design
                4. Component descriptions
                5. Data models and schemas
                6. API contracts
                7. Security considerations
                8. Performance requirements
                9. Testing strategy
                10. Deployment considerations
                11. Monitoring and observability
                12. Future considerations
                
                Use diagrams (Mermaid syntax) where helpful.
                Be thorough but organized.
                """;

        return generateDocument(input, systemPrompt, "technical-spec", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateUserGuide(DocInput input, long startTime) {
        String systemPrompt = """
                You are a user experience writer. Generate a user-friendly guide.
                
                Include:
                1. Getting started
                2. Basic concepts
                3. Step-by-step tutorials
                4. Feature walkthroughs
                5. Tips and best practices
                6. Troubleshooting common issues
                7. FAQ section
                8. Glossary of terms
                
                Use simple, clear language. Include screenshots placeholders.
                Anticipate user questions and address them proactively.
                """;

        return generateDocument(input, systemPrompt, "user-guide", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateArchitectureDoc(DocInput input, long startTime) {
        String systemPrompt = """
                You are a solutions architect. Generate architecture documentation.
                
                Include:
                1. System overview
                2. High-level architecture diagram (Mermaid)
                3. Component architecture
                4. Data flow diagrams
                5. Integration points
                6. Technology stack
                7. Scalability considerations
                8. Security architecture
                9. Deployment architecture
                10. Decision log (ADRs)
                
                Use C4 model concepts where appropriate.
                Include both current state and future considerations.
                """;

        return generateDocument(input, systemPrompt, "architecture", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateReleaseNotes(DocInput input, long startTime) {
        String systemPrompt = """
                You are a product manager. Generate user-facing release notes.
                
                Include:
                1. Release highlights
                2. New features with benefits
                3. Improvements and enhancements
                4. Bug fixes
                5. Known issues
                6. Upgrade instructions
                7. Breaking changes (if any)
                8. Deprecation notices
                
                Write for end users, not developers.
                Focus on benefits and impact.
                Be concise and scannable.
                """;

        return generateDocument(input, systemPrompt, "release-notes", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateRunbook(DocInput input, long startTime) {
        String systemPrompt = """
                You are an SRE/DevOps expert. Generate an operational runbook.
                
                Include:
                1. Service overview
                2. Architecture summary
                3. Dependencies
                4. Health checks
                5. Common alerts and responses
                6. Troubleshooting procedures
                7. Escalation paths
                8. Rollback procedures
                9. Disaster recovery
                10. Contact information
                
                Use clear step-by-step procedures.
                Include command examples.
                Prioritize actionable information.
                """;

        return generateDocument(input, systemPrompt, "runbook", startTime);
    }

    private Promise<ProcessResult<DocOutput>> generateDocument(
            DocInput input,
            String systemPrompt,
            String docTypeId,
            long startTime
    ) {
        // First check for template
        return templateService.getTemplate(docTypeId, input.templateId())
                .then(template -> {
                    String userPrompt = buildUserPrompt(input, template);

                    CompletionRequest request = CompletionRequest.builder()
                            .model("claude-3-sonnet")
                            .messages(List.of(
                                    ChatMessage.system(systemPrompt),
                                    ChatMessage.user(userPrompt)
                            ))
                            .temperature(0.4)
                            .maxTokens(4096)
                            .build();

                    return llmGateway.complete(request);
                })
                .map(result -> {
                    String content = result.getText();
                    int tokensUsed = result.getTokensUsed();
                    String modelUsed = result.getModelUsed() != null ? result.getModelUsed() : "unknown";

                    // Parse sections from generated content
                    List<DocSection> sections = parseSections(content);

                    // Generate table of contents
                    String tableOfContents = generateToc(sections);

                    DocOutput output = new DocOutput(
                            content,
                            sections,
                            tableOfContents,
                            estimateReadingTime(content),
                            new DocMetadata(
                                    input.docType(),
                                    modelUsed,
                                    tokensUsed,
                                    System.currentTimeMillis() - startTime,
                                    content.split("\\r?\\n").length,
                                    content.split("\\s+").length
                            )
                    );

                            return ProcessResult.of(output, tokensUsed, modelUsed, null);
                });
    }

    private String buildUserPrompt(DocInput input, DocTemplate template) {
        StringBuilder prompt = new StringBuilder();

        if (input.title() != null) {
            prompt.append("Title: ").append(input.title()).append("\n\n");
        }

        if (input.description() != null) {
            prompt.append("Description:\n").append(input.description()).append("\n\n");
        }

        if (input.context() != null && !input.context().isEmpty()) {
            prompt.append("Context:\n");
            for (Map.Entry<String, Object> entry : input.context().entrySet()) {
                prompt.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            prompt.append("\n");
        }

        if (input.sourceCode() != null && !input.sourceCode().isEmpty()) {
            prompt.append("Source Code for Reference:\n");
            for (Map.Entry<String, String> entry : input.sourceCode().entrySet()) {
                prompt.append("File: ").append(entry.getKey()).append("\n");
                prompt.append("```\n").append(entry.getValue()).append("\n```\n\n");
            }
        }

        if (input.existingDocs() != null && !input.existingDocs().isEmpty()) {
            prompt.append("Existing Documentation to Enhance:\n");
            for (Map.Entry<String, String> entry : input.existingDocs().entrySet()) {
                prompt.append("### ").append(entry.getKey()).append("\n");
                prompt.append(entry.getValue()).append("\n\n");
            }
        }

        if (template != null && template.sections() != null) {
            prompt.append("Required Sections:\n");
            for (String section : template.sections()) {
                prompt.append("- ").append(section).append("\n");
            }
        }

        return prompt.toString();
    }

    private List<DocSection> parseSections(String content) {
        List<DocSection> sections = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        String currentHeading = null;
        int currentLevel = 0;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("#")) {
                // Save previous section
                if (currentHeading != null) {
                    sections.add(new DocSection(
                            currentHeading,
                            currentLevel,
                            currentContent.toString().trim()
                    ));
                }

                // Parse new heading
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                currentHeading = line.substring(level).trim();
                currentLevel = level;
                currentContent = new StringBuilder();
            } else {
                currentContent.append(line).append("\n");
            }
        }

        // Add last section
        if (currentHeading != null) {
            sections.add(new DocSection(
                    currentHeading,
                    currentLevel,
                    currentContent.toString().trim()
            ));
        }

        return sections;
    }

    private String generateToc(List<DocSection> sections) {
        StringBuilder toc = new StringBuilder("## Table of Contents\n\n");

        for (DocSection section : sections) {
            if (section.level() <= 3) { // Only include up to h3
                String indent = "  ".repeat(Math.max(0, section.level() - 1));
                String anchor = section.heading().toLowerCase()
                        .replaceAll("[^a-z0-9\\s-]", "")
                        .replaceAll("\\s+", "-");
                toc.append(indent).append("- [").append(section.heading())
                        .append("](#").append(anchor).append(")\n");
            }
        }

        return toc.toString();
    }

    private int estimateReadingTime(String content) {
        int wordCount = content.split("\\s+").length;
        return Math.max(1, wordCount / 200); // Assume 200 words per minute
    }

    // Input/Output types

    public record DocInput(
            @NotNull DocType docType,
            @Nullable String title,
            @Nullable String description,
            @Nullable Map<String, Object> context,
            @Nullable Map<String, String> sourceCode,
            @Nullable Map<String, String> existingDocs,
            @Nullable String templateId,
            @Nullable String language,
            @Nullable String audience
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private DocType docType;
            private String title;
            private String description;
            private Map<String, Object> context;
            private Map<String, String> sourceCode;
            private Map<String, String> existingDocs;
            private String templateId;
            private String language = "en";
            private String audience = "developers";

            public Builder docType(DocType docType) {
                this.docType = docType;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder context(Map<String, Object> context) {
                this.context = context;
                return this;
            }

            public Builder sourceCode(Map<String, String> sourceCode) {
                this.sourceCode = sourceCode;
                return this;
            }

            public Builder existingDocs(Map<String, String> existingDocs) {
                this.existingDocs = existingDocs;
                return this;
            }

            public Builder templateId(String templateId) {
                this.templateId = templateId;
                return this;
            }

            public Builder language(String language) {
                this.language = language;
                return this;
            }

            public Builder audience(String audience) {
                this.audience = audience;
                return this;
            }

            public DocInput build() {
                if (docType == null) {
                    throw new IllegalStateException("docType is required");
                }
                return new DocInput(
                        docType, title, description, context, sourceCode,
                        existingDocs, templateId, language, audience
                );
            }
        }
    }

    public record DocOutput(
            @NotNull String content,
            @NotNull List<DocSection> sections,
            @NotNull String tableOfContents,
            int estimatedReadingTime,
            @NotNull DocMetadata metadata
    ) {}

    public record DocSection(
            @NotNull String heading,
            int level,
            @NotNull String content
    ) {}

    public record DocMetadata(
            @NotNull DocType docType,
            @NotNull String modelUsed,
            int tokensUsed,
            long generationTimeMs,
            int lineCount,
            int wordCount
    ) {}

    public enum DocType {
        README,
        API_REFERENCE,
        CHANGELOG,
        TECHNICAL_SPEC,
        USER_GUIDE,
        ARCHITECTURE,
        RELEASE_NOTES,
        RUNBOOK
    }

    // Service interface

    public interface TemplateService {
        Promise<DocTemplate> getTemplate(String docTypeId, String templateId);
    }

    public record DocTemplate(
            String id,
            String name,
            List<String> sections,
            String format
    ) {}
}
