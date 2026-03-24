package com.ghatana.products.yappc.domain.agent;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code Generator Agent - generates code, tests, and documentation.
 * <p>
 * Uses GPT-4 or CodeLlama for intelligent code generation based on
 * item requirements and context.
 *
 * @doc.type class
 * @doc.purpose AI-powered code generation
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class CodeGeneratorAgent extends AbstractAIAgent<CodeGeneratorInput, CodeGeneratorOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(CodeGeneratorAgent.class);

    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "AI-powered code generation for scaffolding, implementation, and tests";
    private static final List<String> CAPABILITIES = List.of(
            "code-generation",
            "test-generation",
            "scaffolding",
            "refactoring"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "gpt-4",
            "codellama-34b",
            "claude-3-sonnet"
    );

    private final LLMGateway llmGateway;
    private final CodeTemplateService templateService;
    private final SecurityScanner securityScanner;

    public CodeGeneratorAgent(
            @NotNull MetricsCollector metricsCollector,
            @NotNull LLMGateway llmGateway,
            @NotNull CodeTemplateService templateService,
            @NotNull SecurityScanner securityScanner
    ) {
                super(
                                AgentName.CODE_GENERATOR_AGENT,
                                VERSION,
                                DESCRIPTION,
                                CAPABILITIES,
                                SUPPORTED_MODELS,
                                metricsCollector
                );
        this.llmGateway = llmGateway;
        this.templateService = templateService;
        this.securityScanner = securityScanner;
    }

    @Override
        public void validateInput(@NotNull CodeGeneratorInput input) {
        if (input.itemId() == null || input.itemId().isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (input.generationType() == null) {
            throw new IllegalArgumentException("generationType is required");
        }
    }

    @Override
    protected @NotNull Promise<ProcessResult<CodeGeneratorOutput>> processRequest(
            @NotNull CodeGeneratorInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.info("Generating code for item {} with type {}", input.itemId(), input.generationType());
        long startTime = System.currentTimeMillis();

        return switch (input.generationType()) {
            case SCAFFOLD -> generateScaffold(input, context, startTime);
            case IMPLEMENTATION -> generateImplementation(input, context, startTime);
            case TEST -> generateTests(input, context, startTime);
            case DOCUMENTATION -> generateDocumentation(input, context, startTime);
            case REFACTOR -> generateRefactoring(input, context, startTime);
            case MIGRATION -> generateMigration(input, context, startTime);
        };
    }

    private Promise<ProcessResult<CodeGeneratorOutput>> generateScaffold(
            CodeGeneratorInput input,
            AIAgentContext context,
            long startTime
    ) {
        // Use templates for scaffolding
        String language = input.language() != null ? input.language() : "typescript";
        String framework = input.framework() != null ? input.framework() : "react";

        return templateService.getScaffoldTemplate(language, framework)
                .then(template -> {
                    List<CodeGeneratorOutput.GeneratedFile> files = new ArrayList<>();

                    // Generate directory structure from template
                    for (Map.Entry<String, String> entry : template.files().entrySet()) {
                        String path = entry.getKey().replace("{{itemId}}", input.itemId());
                        String content = entry.getValue()
                                .replace("{{itemId}}", input.itemId())
                                .replace("{{description}}", input.description() != null ? input.description() : "");

                        files.add(new CodeGeneratorOutput.GeneratedFile(
                                path,
                                content,
                                language,
                                CodeGeneratorOutput.GeneratedFile.FileType.SOURCE,
                                "Scaffolded file",
                                content.split("\n").length
                        ));
                    }

                    CodeGeneratorOutput output = CodeGeneratorOutput.builder()
                            .files(files)
                            .qualityScore(new CodeGeneratorOutput.CodeQualityScore(
                                    0.9, 0.9, 0.9, 0.8, 0.9, List.of()
                            ))
                            .metadata(new CodeGeneratorOutput.GenerationMetadata(
                                    "template",
                                    "1.0.0",
                                    0,
                                    System.currentTimeMillis() - startTime,
                                    language,
                                    framework,
                                    Map.of("type", "scaffold")
                            ))
                            .build();

                    return Promise.of(ProcessResult.of(output));
                });
    }

    private Promise<ProcessResult<CodeGeneratorOutput>> generateImplementation(
            CodeGeneratorInput input,
            AIAgentContext context,
            long startTime
    ) {
        String systemPrompt = buildImplementationSystemPrompt(input);
        String userPrompt = buildImplementationUserPrompt(input);

        CompletionRequest request = CompletionRequest.builder()
                .model("gpt-4")
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)
                ))
                .temperature(0.2) // Low temperature for code
                .maxTokens(4096)
                .build();

        return llmGateway.complete(request)
                .then(result -> {
                                        String generatedCode = result.getText();
                                        int tokensUsed = result.getTokensUsed();
                                        String modelUsed = result.getModelUsed() != null ? result.getModelUsed() : "unknown";

                    // Parse the generated code into files
                    List<CodeGeneratorOutput.GeneratedFile> files = parseGeneratedCode(
                            generatedCode,
                            input.language() != null ? input.language() : "typescript"
                    );

                    // Scan for security issues
                    return securityScanner.scan(files)
                            .map(securityIssues -> {
                                // Calculate quality score
                                CodeGeneratorOutput.CodeQualityScore qualityScore = assessCodeQuality(files, securityIssues);

                                CodeGeneratorOutput output = CodeGeneratorOutput.builder()
                                        .files(files)
                                        .qualityScore(qualityScore)
                                        .securityIssues(securityIssues)
                                        .metadata(new CodeGeneratorOutput.GenerationMetadata(
                                                modelUsed,
                                                "2024-01",
                                                tokensUsed,
                                                System.currentTimeMillis() - startTime,
                                                input.language() != null ? input.language() : "typescript",
                                                input.framework(),
                                                Map.of("type", "implementation")
                                        ))
                                        .build();

                                return ProcessResult.of(output, tokensUsed, modelUsed, null);
                            });
                });
    }

    private Promise<ProcessResult<CodeGeneratorOutput>> generateTests(
            CodeGeneratorInput input,
            AIAgentContext context,
            long startTime
    ) {
        String testFramework = input.testingConfig() != null
                ? input.testingConfig().framework()
                : "jest";

        String systemPrompt = String.format("""
                You are an expert test engineer. Generate comprehensive test cases.
                
                Testing Framework: %s
                Include integration tests: %s
                Include mocks: %s
                Coverage target: %s%%
                
                Generate well-structured, maintainable tests with:
                - Clear test descriptions
                - Proper setup and teardown
                - Edge case coverage
                - Mock usage where appropriate
                """,
                testFramework,
                input.testingConfig() != null && Boolean.TRUE.equals(input.testingConfig().includeIntegration()),
                input.testingConfig() != null && Boolean.TRUE.equals(input.testingConfig().includeMocks()),
                input.testingConfig() != null && input.testingConfig().coverageTarget() != null
                        ? input.testingConfig().coverageTarget() * 100 : 80
        );

        String userPrompt = String.format("""
                Generate tests for the following:
                
                Item ID: %s
                Description: %s
                Requirements:
                %s
                
                Generate test files with full implementations.
                """,
                input.itemId(),
                input.description() != null ? input.description() : "No description provided",
                input.requirements() != null ? String.join("\n- ", input.requirements()) : "No specific requirements"
        );

        CompletionRequest request = CompletionRequest.builder()
                .model("gpt-4")
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)
                ))
                .temperature(0.3)
                .maxTokens(4096)
                .build();

        return llmGateway.complete(request)
                .map(result -> {
                                        String generatedCode = result.getText();
                                        int tokensUsed = result.getTokensUsed();
                                        String modelUsed = result.getModelUsed() != null ? result.getModelUsed() : "unknown";

                    List<CodeGeneratorOutput.GeneratedFile> files = parseGeneratedCode(
                            generatedCode,
                            input.language() != null ? input.language() : "typescript"
                    );

                    // Convert to test format
                    List<CodeGeneratorOutput.GeneratedTest> tests = files.stream()
                            .map(file -> new CodeGeneratorOutput.GeneratedTest(
                                    file.path(),
                                    file.description() != null ? file.description() : "Generated test",
                                    file.content(),
                                    CodeGeneratorOutput.GeneratedTest.TestType.UNIT,
                                    List.of()
                            ))
                            .toList();

                    CodeGeneratorOutput output = CodeGeneratorOutput.builder()
                            .files(files)
                            .tests(tests)
                            .qualityScore(new CodeGeneratorOutput.CodeQualityScore(
                                    0.85, 0.85, 0.9, 0.95, 0.8, List.of()
                            ))
                            .metadata(new CodeGeneratorOutput.GenerationMetadata(
                                    modelUsed,
                                    "2024-01",
                                    tokensUsed,
                                    System.currentTimeMillis() - startTime,
                                    input.language() != null ? input.language() : "typescript",
                                    testFramework,
                                    Map.of("type", "test")
                            ))
                            .build();

                    return ProcessResult.of(output, tokensUsed, modelUsed, null);
                });
    }

    private Promise<ProcessResult<CodeGeneratorOutput>> generateDocumentation(
            CodeGeneratorInput input,
            AIAgentContext context,
            long startTime
    ) {
        String systemPrompt = """
                You are a technical documentation expert. Generate clear, comprehensive documentation.
                
                Include:
                - Overview and purpose
                - Installation/setup instructions
                - API reference
                - Usage examples
                - Configuration options
                - Troubleshooting guide
                
                Use Markdown format with proper headings, code blocks, and tables.
                """;

        String userPrompt = String.format("""
                Generate documentation for:
                
                Item ID: %s
                Description: %s
                Language: %s
                Framework: %s
                
                Requirements:
                %s
                """,
                input.itemId(),
                input.description() != null ? input.description() : "No description",
                input.language() != null ? input.language() : "typescript",
                input.framework() != null ? input.framework() : "Not specified",
                input.requirements() != null ? String.join("\n- ", input.requirements()) : "No requirements"
        );

        CompletionRequest request = CompletionRequest.builder()
                .model("claude-3-sonnet") // Claude is great for documentation
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)
                ))
                .temperature(0.4)
                .maxTokens(4096)
                .build();

        return llmGateway.complete(request)
                .map(result -> {
                                        String documentation = result.getText();
                                        int tokensUsed = result.getTokensUsed();
                                        String modelUsed = result.getModelUsed() != null ? result.getModelUsed() : "unknown";

                    List<CodeGeneratorOutput.GeneratedFile> files = List.of(
                            new CodeGeneratorOutput.GeneratedFile(
                                    "README.md",
                                    documentation,
                                    "markdown",
                                    CodeGeneratorOutput.GeneratedFile.FileType.DOCUMENTATION,
                                    "Generated documentation",
                                    documentation.split("\n").length
                            )
                    );

                    CodeGeneratorOutput output = CodeGeneratorOutput.builder()
                            .files(files)
                            .documentation(documentation)
                            .qualityScore(new CodeGeneratorOutput.CodeQualityScore(
                                    0.9, 0.95, 0.95, 0.8, 0.85, List.of()
                            ))
                            .metadata(new CodeGeneratorOutput.GenerationMetadata(
                                    modelUsed,
                                    "2024-01",
                                    tokensUsed,
                                    System.currentTimeMillis() - startTime,
                                    "markdown",
                                    null,
                                    Map.of("type", "documentation")
                            ))
                            .build();

                    return ProcessResult.of(output, tokensUsed, modelUsed, null);
                });
    }

    private Promise<ProcessResult<CodeGeneratorOutput>> generateRefactoring(
            CodeGeneratorInput input,
            AIAgentContext context,
            long startTime
    ) {
        // Similar implementation - analyze existing code and suggest refactorings
        return Promise.of(ProcessResult.of(
                CodeGeneratorOutput.builder()
                        .files(List.of())
                        .metadata(new CodeGeneratorOutput.GenerationMetadata(
                                "gpt-4", "2024-01", 0,
                                System.currentTimeMillis() - startTime,
                                input.language() != null ? input.language() : "typescript",
                                null, Map.of("type", "refactor")
                        ))
                        .build()
        ));
    }

    private Promise<ProcessResult<CodeGeneratorOutput>> generateMigration(
            CodeGeneratorInput input,
            AIAgentContext context,
            long startTime
    ) {
        // Generate database or code migrations
        return Promise.of(ProcessResult.of(
                CodeGeneratorOutput.builder()
                        .files(List.of())
                        .metadata(new CodeGeneratorOutput.GenerationMetadata(
                                "gpt-4", "2024-01", 0,
                                System.currentTimeMillis() - startTime,
                                input.language() != null ? input.language() : "sql",
                                null, Map.of("type", "migration")
                        ))
                        .build()
        ));
    }

    private String buildImplementationSystemPrompt(CodeGeneratorInput input) {
        return String.format("""
                You are an expert software engineer. Generate production-quality code.
                
                Language: %s
                Framework: %s
                
                Code Style:
                - Clean, readable, and maintainable
                - Follow SOLID principles
                - Include proper error handling
                - Add JSDoc/JavaDoc comments
                - Use meaningful variable names
                
                Output format:
                Use ```filename.ext to mark file boundaries.
                Include all necessary imports.
                """,
                input.language() != null ? input.language() : "typescript",
                input.framework() != null ? input.framework() : "none"
        );
    }

    private String buildImplementationUserPrompt(CodeGeneratorInput input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate code for:\n\n");
        prompt.append("Item ID: ").append(input.itemId()).append("\n");

        if (input.description() != null) {
            prompt.append("Description: ").append(input.description()).append("\n");
        }

        if (input.requirements() != null && !input.requirements().isEmpty()) {
            prompt.append("\nRequirements:\n");
            for (String req : input.requirements()) {
                prompt.append("- ").append(req).append("\n");
            }
        }

        if (input.context() != null && !input.context().isEmpty()) {
            prompt.append("\nContext:\n");
            for (Map.Entry<String, Object> entry : input.context().entrySet()) {
                prompt.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return prompt.toString();
    }

    private List<CodeGeneratorOutput.GeneratedFile> parseGeneratedCode(String content, String language) {
        List<CodeGeneratorOutput.GeneratedFile> files = new ArrayList<>();

        // Parse code blocks with filenames
        Pattern pattern = Pattern.compile("```(\\S+)?\\s*\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        int fileIndex = 0;
        while (matcher.find()) {
            String fileInfo = matcher.group(1);
            String code = matcher.group(2).trim();

            String path;
            String lang = language;

            if (fileInfo != null && fileInfo.contains(".")) {
                path = fileInfo;
                // Detect language from extension
                if (fileInfo.endsWith(".ts") || fileInfo.endsWith(".tsx")) {
                    lang = "typescript";
                } else if (fileInfo.endsWith(".js") || fileInfo.endsWith(".jsx")) {
                    lang = "javascript";
                } else if (fileInfo.endsWith(".java")) {
                    lang = "java";
                } else if (fileInfo.endsWith(".py")) {
                    lang = "python";
                }
            } else {
                path = String.format("generated-%d.%s", fileIndex++, getExtension(language));
            }

            files.add(new CodeGeneratorOutput.GeneratedFile(
                    path,
                    code,
                    lang,
                    path.contains("test") || path.contains("spec")
                            ? CodeGeneratorOutput.GeneratedFile.FileType.TEST
                            : CodeGeneratorOutput.GeneratedFile.FileType.SOURCE,
                    null,
                    code.split("\n").length
            ));
        }

        // If no code blocks found, treat entire content as single file
        if (files.isEmpty() && !content.isBlank()) {
            files.add(new CodeGeneratorOutput.GeneratedFile(
                    "generated." + getExtension(language),
                    content,
                    language,
                    CodeGeneratorOutput.GeneratedFile.FileType.SOURCE,
                    null,
                    content.split("\n").length
            ));
        }

        return files;
    }

    private String getExtension(String language) {
        return switch (language.toLowerCase()) {
            case "typescript" -> "ts";
            case "javascript" -> "js";
            case "java" -> "java";
            case "python" -> "py";
            case "go" -> "go";
            case "rust" -> "rs";
            case "sql" -> "sql";
            default -> "txt";
        };
    }

    private CodeGeneratorOutput.CodeQualityScore assessCodeQuality(
            List<CodeGeneratorOutput.GeneratedFile> files,
            List<CodeGeneratorOutput.SecurityIssue> securityIssues
    ) {
        // Basic quality assessment
        double maintainability = 0.85;
        double readability = 0.85;
        double testability = 0.80;
        double security = securityIssues.isEmpty() ? 0.95 : 0.95 - (securityIssues.size() * 0.05);

        List<CodeGeneratorOutput.CodeQualityScore.QualityIssue> issues = new ArrayList<>();

        // Check for common issues
        for (CodeGeneratorOutput.GeneratedFile file : files) {
            if (file.lineCount() > 300) {
                issues.add(new CodeGeneratorOutput.CodeQualityScore.QualityIssue(
                        "file-too-long",
                        "warning",
                        "File exceeds 300 lines, consider splitting",
                        file.path(),
                        null,
                        "Split into smaller modules"
                ));
                maintainability -= 0.05;
            }
        }

        double overall = (maintainability + readability + testability + security) / 4;

        return new CodeGeneratorOutput.CodeQualityScore(
                Math.max(0, overall),
                Math.max(0, maintainability),
                Math.max(0, readability),
                Math.max(0, testability),
                Math.max(0, security),
                issues
        );
    }

        @Override
        protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
                return Promise.of(Map.of(
                                "llmGateway", AgentHealth.DependencyStatus.HEALTHY,
                                "templateService", AgentHealth.DependencyStatus.HEALTHY,
                                "securityScanner", AgentHealth.DependencyStatus.HEALTHY
                ));
        }

    // Service interfaces

    /**
     * Service for code templates.
     */
    public interface CodeTemplateService {
        Promise<ScaffoldTemplate> getScaffoldTemplate(String language, String framework);

        record ScaffoldTemplate(
                String language,
                String framework,
                Map<String, String> files
        ) {}
    }

    /**
     * Service for security scanning.
     */
    public interface SecurityScanner {
        Promise<List<CodeGeneratorOutput.SecurityIssue>> scan(List<CodeGeneratorOutput.GeneratedFile> files);
    }
}
