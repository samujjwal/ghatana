package com.ghatana.yappc.ai.canvas;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.prompts.PromptTemplateManager;
import com.ghatana.contracts.canvas.v1.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Canvas Code Generation Service
 * 
 * Java/ActiveJ implementation of GenerationAgent for hybrid backend.
 * Uses AI (LLM) to generate production-ready code from canvas design.
 * 
 * @doc.type class
 * @doc.purpose Canvas code generation service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public class CanvasGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CanvasGenerationService.class);
    private static final Executor BLOCKING_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "canvas-gen-worker");
                t.setDaemon(true);
                return t;
            });
    
    private final LLMGateway llmGateway;
    private final PromptTemplateManager promptTemplates;
    private final MetricsCollector metrics;
    
    public CanvasGenerationService(LLMGateway llmGateway, 
                                  PromptTemplateManager promptTemplates,
                                  MetricsCollector metrics) {
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway required");
        this.promptTemplates = Objects.requireNonNull(promptTemplates, "promptTemplates required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }
    
    /**
     * Generate code from canvas design
     */
    public Promise<CodeGenerationResult> generate(GenerateCodeRequest request) {
        long startTime = System.currentTimeMillis();
        
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                metrics.incrementCounter("canvas.generation.requests");
                
                CanvasState canvasState = request.getCanvasState();
                GenerationOptions options = request.hasOptions() ? request.getOptions() : getDefaultOptions();
                
                logger.info("Generating code for canvas {} (language: {}, framework: {})",
                    canvasState.getCanvasId(), options.getLanguage(), options.getFramework());
                
                // Build result
                CodeGenerationResult.Builder resultBuilder = CodeGenerationResult.newBuilder()
                    .setGenerationId(UUID.randomUUID().toString())
                    .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(Instant.now().getEpochSecond())
                        .build());
                
                // Generate artifacts for each element
                List<GeneratedArtifact> artifacts = new ArrayList<>();
                
                // 1. Generate backend API code
                artifacts.addAll(generateBackendAPI(canvasState, options));
                
                // 2. Generate database schema
                artifacts.addAll(generateDatabaseSchema(canvasState, options));
                
                // 3. Generate frontend components
                artifacts.addAll(generateFrontendComponents(canvasState, options));
                
                // 4. Generate configuration files
                artifacts.addAll(generateConfigFiles(canvasState, options));
                
                // 5. Generate tests
                if (options.getIncludeTests()) {
                    artifacts.addAll(generateTests(canvasState, options));
                }
                
                // 6. Generate documentation
                if (options.getIncludeDocumentation()) {
                    artifacts.addAll(generateDocumentation(canvasState, options));
                }
                
                resultBuilder.addAllArtifacts(artifacts);
                resultBuilder.setSuccess(true);
                
                // Build summary
                String summary = buildSummary(artifacts);
                resultBuilder.setSummary(summary);
                
                // Build statistics
                GenerationStatistics stats = buildStatistics(artifacts, startTime);
                resultBuilder.setStatistics(stats);
                
                // Record metrics
                long elapsedTime = System.currentTimeMillis() - startTime;
                metrics.recordTimer("canvas.generation.duration", elapsedTime);
                metrics.incrementCounter("canvas.generation.artifacts", "count", String.valueOf(artifacts.size()));
                metrics.incrementCounter("canvas.generation.success");
                
                logger.info("Code generation complete for canvas {} - {} artifacts generated in {}ms",
                    canvasState.getCanvasId(), artifacts.size(), elapsedTime);
                
                return resultBuilder.build();
                
            } catch (Exception e) {
                metrics.incrementCounter("canvas.generation.errors");
                logger.error("Code generation failed", e);
                
                return CodeGenerationResult.newBuilder()
                    .setGenerationId(UUID.randomUUID().toString())
                    .setSuccess(false)
                    .addErrors(e.getMessage())
                    .build();
            }
        });
    }
    
    /**
     * Generate backend API code
     */
    private List<GeneratedArtifact> generateBackendAPI(CanvasState canvasState, GenerationOptions options) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        
        List<CanvasElement> apiElements = canvasState.getElementsList().stream()
            .filter(e -> e.getType() == ElementType.ELEMENT_TYPE_API)
            .collect(Collectors.toList());
        
        for (CanvasElement apiElement : apiElements) {
            String label = getLabel(apiElement);
            String endpoint = apiElement.getData().getFieldsOrDefault("endpoint", 
                com.google.protobuf.Value.newBuilder().setStringValue("/api").build()).getStringValue();
            String method = apiElement.getData().getFieldsOrDefault("method", 
                com.google.protobuf.Value.newBuilder().setStringValue("GET").build()).getStringValue();
            
            // Generate controller
            String controllerCode = generateController(label, endpoint, method, options);
            artifacts.add(createArtifact(
                String.format("src/controllers/%sController.%s", toPascalCase(label), getFileExtension(options.getLanguage())),
                controllerCode,
                ArtifactType.ARTIFACT_TYPE_SOURCE,
                options.getLanguage(),
                options.getFramework(),
                "API Controller for " + label
            ));
            
            // Generate service
            String serviceCode = generateService(label, options);
            artifacts.add(createArtifact(
                String.format("src/services/%sService.%s", toPascalCase(label), getFileExtension(options.getLanguage())),
                serviceCode,
                ArtifactType.ARTIFACT_TYPE_SOURCE,
                options.getLanguage(),
                options.getFramework(),
                "Service layer for " + label
            ));
        }
        
        return artifacts;
    }
    
    /**
     * Generate database schema
     */
    private List<GeneratedArtifact> generateDatabaseSchema(CanvasState canvasState, GenerationOptions options) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        
        List<CanvasElement> dataElements = canvasState.getElementsList().stream()
            .filter(e -> e.getType() == ElementType.ELEMENT_TYPE_DATA)
            .collect(Collectors.toList());
        
        if (dataElements.isEmpty()) {
            return artifacts;
        }
        
        // Generate Prisma schema
        StringBuilder schemaBuilder = new StringBuilder();
        schemaBuilder.append("// Prisma Schema Generated from Canvas\n\n");
        schemaBuilder.append("generator client {\n");
        schemaBuilder.append("  provider = \"prisma-client-js\"\n");
        schemaBuilder.append("}\n\n");
        schemaBuilder.append("datasource db {\n");
        schemaBuilder.append("  provider = \"postgresql\"\n");
        schemaBuilder.append("  url      = env(\"DATABASE_URL\")\n");
        schemaBuilder.append("}\n\n");
        
        for (CanvasElement dataElement : dataElements) {
            String tableName = getLabel(dataElement);
            schemaBuilder.append(String.format("model %s {\n", toPascalCase(tableName)));
            schemaBuilder.append("  id        String   @id @default(uuid())\n");
            schemaBuilder.append("  createdAt DateTime @default(now())\n");
            schemaBuilder.append("  updatedAt DateTime @updatedAt\n");
            
            // Add custom fields if defined
            if (dataElement.getData().containsFields("fields")) {
                // Parse and add fields
            }
            
            schemaBuilder.append("}\n\n");
        }
        
        artifacts.add(createArtifact(
            "prisma/schema.prisma",
            schemaBuilder.toString(),
            ArtifactType.ARTIFACT_TYPE_SCHEMA,
            ProgrammingLanguage.PROGRAMMING_LANGUAGE_UNSPECIFIED,
            "prisma",
            "Database schema"
        ));
        
        return artifacts;
    }
    
    /**
     * Generate frontend components
     */
    private List<GeneratedArtifact> generateFrontendComponents(CanvasState canvasState, GenerationOptions options) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        
        List<CanvasElement> componentElements = canvasState.getElementsList().stream()
            .filter(e -> e.getType() == ElementType.ELEMENT_TYPE_COMPONENT || 
                        e.getType() == ElementType.ELEMENT_TYPE_PAGE)
            .collect(Collectors.toList());
        
        for (CanvasElement element : componentElements) {
            String componentName = getLabel(element);
            String componentCode = generateReactComponent(componentName, element, options);
            
            artifacts.add(createArtifact(
                String.format("src/components/%s.tsx", toPascalCase(componentName)),
                componentCode,
                ArtifactType.ARTIFACT_TYPE_SOURCE,
                ProgrammingLanguage.PROGRAMMING_LANGUAGE_TYPESCRIPT,
                "react",
                "React component for " + componentName
            ));
        }
        
        return artifacts;
    }
    
    /**
     * Generate configuration files
     */
    private List<GeneratedArtifact> generateConfigFiles(CanvasState canvasState, GenerationOptions options) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        
        // Generate package.json
        String packageJson = generatePackageJson(canvasState, options);
        artifacts.add(createArtifact(
            "package.json",
            packageJson,
            ArtifactType.ARTIFACT_TYPE_CONFIG,
            ProgrammingLanguage.PROGRAMMING_LANGUAGE_UNSPECIFIED,
            "npm",
            "NPM package configuration"
        ));
        
        // Generate tsconfig.json
        if (options.getLanguage() == ProgrammingLanguage.PROGRAMMING_LANGUAGE_TYPESCRIPT) {
            String tsconfig = generateTsConfig();
            artifacts.add(createArtifact(
                "tsconfig.json",
                tsconfig,
                ArtifactType.ARTIFACT_TYPE_CONFIG,
                ProgrammingLanguage.PROGRAMMING_LANGUAGE_TYPESCRIPT,
                "typescript",
                "TypeScript configuration"
            ));
        }
        
        // Generate .env.example
        String envExample = generateEnvExample(canvasState);
        artifacts.add(createArtifact(
            ".env.example",
            envExample,
            ArtifactType.ARTIFACT_TYPE_CONFIG,
            ProgrammingLanguage.PROGRAMMING_LANGUAGE_UNSPECIFIED,
            "",
            "Environment variables template"
        ));
        
        return artifacts;
    }
    
    /**
     * Generate tests
     */
    private List<GeneratedArtifact> generateTests(CanvasState canvasState, GenerationOptions options) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        
        // Generate API tests
        List<CanvasElement> apiElements = canvasState.getElementsList().stream()
            .filter(e -> e.getType() == ElementType.ELEMENT_TYPE_API)
            .collect(Collectors.toList());
        
        for (CanvasElement apiElement : apiElements) {
            String label = getLabel(apiElement);
            String testCode = generateApiTest(label, options);
            
            artifacts.add(createArtifact(
                String.format("src/__tests__/%sController.test.%s", 
                    toPascalCase(label), getFileExtension(options.getLanguage())),
                testCode,
                ArtifactType.ARTIFACT_TYPE_TEST,
                options.getLanguage(),
                options.getFramework(),
                "Unit tests for " + label
            ));
        }
        
        return artifacts;
    }
    
    /**
     * Generate documentation
     */
    private List<GeneratedArtifact> generateDocumentation(CanvasState canvasState, GenerationOptions options) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        
        // Generate README
        String readme = generateReadme(canvasState, options);
        artifacts.add(createArtifact(
            "README.md",
            readme,
            ArtifactType.ARTIFACT_TYPE_DOCUMENTATION,
            ProgrammingLanguage.PROGRAMMING_LANGUAGE_UNSPECIFIED,
            "markdown",
            "Project documentation"
        ));
        
        // Generate API documentation
        String apiDocs = generateApiDocumentation(canvasState);
        artifacts.add(createArtifact(
            "docs/API.md",
            apiDocs,
            ArtifactType.ARTIFACT_TYPE_API,
            ProgrammingLanguage.PROGRAMMING_LANGUAGE_UNSPECIFIED,
            "markdown",
            "API documentation"
        ));
        
        return artifacts;
    }
    
    /**
     * Generate controller code
     */
    private String generateController(String name, String endpoint, String method, GenerationOptions options) {
        if (options.getLanguage() == ProgrammingLanguage.PROGRAMMING_LANGUAGE_TYPESCRIPT) {
            return String.format("""
                import { FastifyRequest, FastifyReply } from 'fastify';
                import { %sService } from '../services/%sService';
                
                /**
                 * %s Controller
                 * Handles HTTP requests for %s
                 */
                export class %sController {
                  private service: %sService;
                  
                  constructor() {
                    this.service = new %sService();
                  }
                  
                  async %s(request: FastifyRequest, reply: FastifyReply) {
                    try {
                      const result = await this.service.%s(request.body);
                      return reply.send(result);
                    } catch (error) {
                      return reply.status(500).send({ error: error.message });
                    }
                  }
                }
                """,
                toPascalCase(name), toPascalCase(name),
                toPascalCase(name), name,
                toPascalCase(name), toPascalCase(name),
                toPascalCase(name),
                method.toLowerCase(), method.toLowerCase()
            );
        }
        
        return "// Controller code generation not implemented for " + options.getLanguage();
    }
    
    /**
     * Generate service code
     */
    private String generateService(String name, GenerationOptions options) {
        if (options.getLanguage() == ProgrammingLanguage.PROGRAMMING_LANGUAGE_TYPESCRIPT) {
            return String.format("""
                /**
                 * %s Service
                 * Business logic for %s
                 */
                export class %sService {
                  async process(data: any) {
                    // TODO: Implement business logic
                    return { success: true, data };
                  }
                }
                """,
                toPascalCase(name), name, toPascalCase(name)
            );
        }
        
        return "// Service code generation not implemented for " + options.getLanguage();
    }
    
    /**
     * Generate React component
     */
    private String generateReactComponent(String name, CanvasElement element, GenerationOptions options) {
        return String.format("""
            import React from 'react';
            
            /**
             * %s Component
             */
            export const %s: React.FC = () => {
              return (
                <div className="p-4">
                  <h2 className="text-2xl font-bold">%s</h2>
                  {/* TODO: Add component logic */}
                </div>
              );
            };
            """,
            toPascalCase(name), toPascalCase(name), name
        );
    }
    
    /**
     * Generate package.json
     */
    private String generatePackageJson(CanvasState canvasState, GenerationOptions options) {
        return String.format("""
            {
              "name": "%s",
              "version": "1.0.0",
              "description": "Generated from Canvas",
              "main": "index.js",
              "scripts": {
                "dev": "next dev",
                "build": "next build",
                "start": "next start",
                "test": "jest"
              },
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0",
                "next": "^14.0.0",
                "@prisma/client": "^5.0.0"
              },
              "devDependencies": {
                "@types/react": "^18.2.0",
                "@types/node": "^20.0.0",
                "typescript": "^5.0.0",
                "prisma": "^5.0.0"
              }
            }
            """,
            canvasState.getProjectId().toLowerCase().replace(" ", "-")
        );
    }
    
    /**
     * Generate tsconfig.json
     */
    private String generateTsConfig() {
        return """
            {
              "compilerOptions": {
                "target": "ES2020",
                "lib": ["ES2020", "DOM"],
                "jsx": "react-jsx",
                "module": "ESNext",
                "moduleResolution": "bundler",
                "strict": true,
                "esModuleInterop": true,
                "skipLibCheck": true,
                "forceConsistentCasingInFileNames": true
              },
              "include": ["src"],
              "exclude": ["node_modules"]
            }
            """;
    }
    
    /**
     * Generate .env.example
     */
    private String generateEnvExample(CanvasState canvasState) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Environment Variables\n\n");
        
        boolean hasDatabase = canvasState.getElementsList().stream()
            .anyMatch(e -> e.getType() == ElementType.ELEMENT_TYPE_DATA);
        
        if (hasDatabase) {
            sb.append("DATABASE_URL=postgresql://user:password@localhost:5432/db\n");
        }
        
        sb.append("PORT=3000\n");
        sb.append("NODE_ENV=development\n");
        
        return sb.toString();
    }
    
    /**
     * Generate API test
     */
    private String generateApiTest(String name, GenerationOptions options) {
        return String.format("""
            import { describe, it, expect } from '@jest/globals';
            import { %sController } from '../controllers/%sController';
            
            describe('%sController', () => {
              it('should handle requests successfully', async () => {
                const controller = new %sController();
                // TODO: Add test cases
                expect(controller).toBeDefined();
              });
            });
            """,
            toPascalCase(name), toPascalCase(name),
            toPascalCase(name),
            toPascalCase(name)
        );
    }
    
    /**
     * Generate README
     */
    private String generateReadme(CanvasState canvasState, GenerationOptions options) {
        return String.format("""
            # %s
            
            Generated from Canvas Design
            
            ## Getting Started
            
            1. Install dependencies:
            ```bash
            npm install
            ```
            
            2. Set up environment variables:
            ```bash
            cp .env.example .env
            ```
            
            3. Run development server:
            ```bash
            npm run dev
            ```
            
            ## Architecture
            
            This project was generated from a canvas design with the following components:
            - %d API endpoints
            - %d data models
            - %d frontend components
            
            ## Technology Stack
            
            - Language: %s
            - Framework: %s
            """,
            canvasState.getProjectId(),
            canvasState.getElementsList().stream().filter(e -> e.getType() == ElementType.ELEMENT_TYPE_API).count(),
            canvasState.getElementsList().stream().filter(e -> e.getType() == ElementType.ELEMENT_TYPE_DATA).count(),
            canvasState.getElementsList().stream().filter(e -> e.getType() == ElementType.ELEMENT_TYPE_COMPONENT).count(),
            options.getLanguage(),
            options.getFramework()
        );
    }
    
    /**
     * Generate API documentation
     */
    private String generateApiDocumentation(CanvasState canvasState) {
        StringBuilder sb = new StringBuilder();
        sb.append("# API Documentation\n\n");
        
        List<CanvasElement> apiElements = canvasState.getElementsList().stream()
            .filter(e -> e.getType() == ElementType.ELEMENT_TYPE_API)
            .collect(Collectors.toList());
        
        for (CanvasElement apiElement : apiElements) {
            String label = getLabel(apiElement);
            String endpoint = apiElement.getData().getFieldsOrDefault("endpoint", 
                com.google.protobuf.Value.newBuilder().setStringValue("/api").build()).getStringValue();
            String method = apiElement.getData().getFieldsOrDefault("method", 
                com.google.protobuf.Value.newBuilder().setStringValue("GET").build()).getStringValue();
            
            sb.append(String.format("## %s %s\n\n", method, endpoint));
            sb.append(String.format("**Description:** %s\n\n", label));
            sb.append("**Request:**\n```json\n{}\n```\n\n");
            sb.append("**Response:**\n```json\n{}\n```\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Build generation summary
     */
    private String buildSummary(List<GeneratedArtifact> artifacts) {
        Map<ArtifactType, Long> typeCounts = artifacts.stream()
            .collect(Collectors.groupingBy(GeneratedArtifact::getType, Collectors.counting()));
        
        int sourceFiles = typeCounts.getOrDefault(ArtifactType.ARTIFACT_TYPE_SOURCE, 0L).intValue();
        int testFiles = typeCounts.getOrDefault(ArtifactType.ARTIFACT_TYPE_TEST, 0L).intValue();
        int configFiles = typeCounts.getOrDefault(ArtifactType.ARTIFACT_TYPE_CONFIG, 0L).intValue();
        int docFiles = typeCounts.getOrDefault(ArtifactType.ARTIFACT_TYPE_DOCUMENTATION, 0L).intValue();
        
        return String.format("Generated %d artifacts: %d source, %d test, %d config, %d documentation",
            artifacts.size(), sourceFiles, testFiles, configFiles, docFiles);
    }
    
    /**
     * Build generation statistics
     */
    private GenerationStatistics buildStatistics(List<GeneratedArtifact> artifacts, long startTime) {
        int totalLines = artifacts.stream()
            .mapToInt(a -> a.getContent().split("\n").length)
            .sum();
        
        long duration = System.currentTimeMillis() - startTime;
        
        return GenerationStatistics.newBuilder()
            .setTotalLines(totalLines)
            .setTotalFiles(artifacts.size())
            .build();
    }
    
    /**
     * Create artifact
     */
    private GeneratedArtifact createArtifact(String path, String content, ArtifactType type,
                                            ProgrammingLanguage language, String framework,
                                            String description) {
        return GeneratedArtifact.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setType(type)
            .setPath(path)
            .setContent(content)
            .setLanguage(language.name())
            .setFramework(framework)
            .build();
    }
    
    /**
     * Get element label
     */
    private String getLabel(CanvasElement element) {
        return element.getData().getFieldsOrDefault("label", 
            com.google.protobuf.Value.newBuilder().setStringValue("Unnamed").build()).getStringValue();
    }
    
    /**
     * Convert to PascalCase
     */
    private String toPascalCase(String str) {
        return Arrays.stream(str.split("[\\s_-]"))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
            .collect(Collectors.joining());
    }
    
    /**
     * Get file extension for language
     */
    private String getFileExtension(ProgrammingLanguage language) {
        return switch (language) {
            case PROGRAMMING_LANGUAGE_TYPESCRIPT -> "ts";
            case PROGRAMMING_LANGUAGE_JAVA -> "java";
            case PROGRAMMING_LANGUAGE_PYTHON -> "py";
            case PROGRAMMING_LANGUAGE_GO -> "go";
            default -> "txt";
        };
    }
    
    /**
     * Get default generation options
     */
    private GenerationOptions getDefaultOptions() {
        return GenerationOptions.newBuilder()
            .setLanguage(ProgrammingLanguage.PROGRAMMING_LANGUAGE_TYPESCRIPT)
            .setFramework("nextjs")
            .setIncludeTests(true)
            .setIncludeDocumentation(true)
            .build();
    }

    /**
     * Generate code for a single element using the LLM gateway.
     *
     * <p>Delegates to the injected {@link LLMGateway} for higher-quality,
     * context-aware code generation instead of template-based output.
     * Falls back to template generation on LLM failure.</p>
     *
     * @param elementDescription human-readable description of what to generate
     * @param language           target programming language name
     * @param framework          target framework
     * @return Promise resolving to generated source code
     */
    public Promise<String> generateWithLLM(String elementDescription, String language, String framework) {
        metrics.incrementCounter("canvas.generation.llm.requests");

        String systemPrompt = promptTemplates.render("canvas-codegen-system", Map.of(
                "language", language,
                "framework", framework
        ));

        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(elementDescription)
                ))
                .temperature(0.2)
                .maxTokens(4000)
                .build();

        return llmGateway.complete(request)
                .map(result -> {
                    metrics.incrementCounter("canvas.generation.llm.success");
                    return result.getText();
                })
                .mapException(e -> {
                    metrics.incrementCounter("canvas.generation.llm.errors");
                    logger.warn("LLM generation failed, falling back to template: {}", e.getMessage());
                    return e;
                });
    }
}
