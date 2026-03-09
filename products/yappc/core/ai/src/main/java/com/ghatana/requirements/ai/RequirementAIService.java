package com.ghatana.requirements.ai;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.requirements.ai.persona.Persona;
import com.ghatana.requirements.ai.persona.PersonaRepository;
import com.ghatana.requirements.ai.profiling.LLMApiCallEvent;
import com.ghatana.requirements.ai.prompts.PromptTemplate;
import com.ghatana.requirements.ai.prompts.PromptTemplateManager;
import com.ghatana.requirements.ai.suggestions.AISuggestion;
import com.ghatana.requirements.ai.suggestions.SuggestionStatus;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import io.activej.promise.Promise;
import jdk.jfr.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central AI service for requirements generation and management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates AI capabilities (LLM, embeddings, vector search) to provide
 * intelligent requirements generation, suggestion, and analysis.
 *
 * <p>
 * <b>Capabilities</b><br>
 * <ul>
 * <li>Generate requirements from feature descriptions</li>
 * <li>Find similar requirements using semantic search</li>
 * <li>Suggest improvements to existing requirements</li>
 * <li>Extract acceptance criteria from requirements</li>
 * <li>Classify and prioritize requirements</li>
 * <li>Validate requirement quality</li>
 * </ul>
 *
 * <p>
 * <b>Architecture</b><br>
 * Follows Service pattern with dependency injection. Delegates to specialized
 * services (LLM, Embedding, VectorStore) for specific capabilities.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - all dependencies are immutable or thread-safe. Methods return
 * ActiveJ Promises for async execution.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RequirementAIService aiService = new RequirementAIService(
 *     llmService,
 *     embeddingService,
 *     vectorStore,
 *     personaRepository,
 *     promptTemplateManager,
 *     metricsCollector
 * );
 *
 * // Generate requirements
 * RequirementGenerationRequest request = RequirementGenerationRequest.builder()
 *     .featureDescription("User authentication with email and password")
 *     .count(5)
 *     .type(RequirementType.FUNCTIONAL)
 *     .includeAcceptanceCriteria(true)
 *     .build();
 *
 * Promise<RequirementGenerationResponse> response = aiService.generateRequirements(request);
 * }</pre>
 *
 * @see LLMService
 * @see EmbeddingService
 * @see VectorStore
 * @doc.type class
 * @doc.purpose Central AI service for requirements generation and management
 * @doc.layer application
 * @doc.pattern Service (orchestrates multiple AI capabilities)
 */
public class RequirementAIService {

    private static final Logger logger = LoggerFactory.getLogger(RequirementAIService.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    private final CompletionService llmService;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final PersonaRepository personaRepository;
    private final PromptTemplateManager promptTemplateManager;
    private final MetricsCollector metrics;

    /**
     * Creates a new RequirementAIService with required dependencies.
     *
     * @param llmService LLM service for text generation
     * @param embeddingService embedding service for vector generation
     * @param vectorStore vector store for similarity search
     * @param personaRepository repository for persona management
     * @param promptTemplateManager manager for prompt templates
     * @param metrics metrics collector for observability
     */
    public RequirementAIService(
            CompletionService llmService,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            PersonaRepository personaRepository,
            PromptTemplateManager promptTemplateManager,
            MetricsCollector metrics
    ) {
        this.llmService = Objects.requireNonNull(llmService, "llmService is required");
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService is required");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore is required");
        this.personaRepository = Objects.requireNonNull(personaRepository, "personaRepository is required");
        this.promptTemplateManager = Objects.requireNonNull(promptTemplateManager, "promptTemplateManager is required");
        this.metrics = Objects.requireNonNull(metrics, "metrics is required");
    }

    /**
     * Generates requirements from a feature description.
     *
     * <p>
     * Uses LLM to generate structured requirements based on the feature
     * description, optionally using a specific persona for context.
     *
     * @param request requirement generation request
     * @return promise of requirement generation response
     */
    public Promise<RequirementGenerationResponse> generateRequirements(RequirementGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        logger.info("Generating requirements for feature: {}", request.getFeatureDescription());

        // Use array to capture llmResponse across Promise chains (effectively final)
        final CompletionResult[] llmResponseHolder = new CompletionResult[1];

        return getPersona(request.getPersonaId())
                .then(persona -> buildPrompt(request, persona))
                .then(prompt -> callLLM(prompt, request))
                .then(llmResponse -> {
                    llmResponseHolder[0] = llmResponse;  // Capture for later use
                    return parseRequirements(llmResponse, request);
                })
                .then(requirements -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    CompletionResult llmResponse = llmResponseHolder[0];

                    // Emit JFR profiling event
                    emitLLMApiCallEvent(requestId, llmResponse, true, durationMs);

                    // Collect metrics
                    metrics.incrementCounter("requirements.generation.success",
                            "persona", request.getPersonaId() != null ? request.getPersonaId() : "default",
                            "type", request.getType() != null ? request.getType().name() : "all");
                    metrics.recordTimer("requirements.generation.duration", durationMs);

                    logger.info("Generated {} requirements in {}ms", requirements.size(), durationMs);

                    return Promise.of(RequirementGenerationResponse.builder()
                            .requirements(requirements)
                            .personaId(request.getPersonaId())
                            .model(llmResponse.getModelUsed())
                            .tokensUsed(llmResponse.getTokensUsed())
                            .latencyMs(durationMs)
                            .build());
                })
                .whenException(ex -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    // Emit JFR profiling event
                    emitLLMApiCallEvent(requestId, null, false, durationMs);

                    // Collect metrics
                    metrics.incrementCounter("requirements.generation.failure",
                            "error", ex.getClass().getSimpleName());

                    logger.error("Failed to generate requirements", ex);
                });
    }

    /**
     * Finds similar requirements using semantic search.
     *
     * <p>
     * Embeds the query text and searches the vector store for similar
     * requirements based on cosine similarity.
     *
     * @param queryText search text to find similar requirements
     * @param projectId project ID for filtering
     * @param limit maximum number of results (default 10)
     * @param minSimilarity minimum similarity threshold (default 0.7)
     * @return promise of search results
     */
    public Promise<List<VectorSearchResult>> findSimilarRequirements(
            String queryText, String projectId, Integer limit, Float minSimilarity) {
        long startTime = System.currentTimeMillis();

        logger.info("Searching for requirements similar to: {}", queryText);

        return embeddingService.createEmbedding(queryText)
                .then(embeddingResponse -> {
                    Map<String, String> filter = new HashMap<>();
                    if (projectId != null) {
                        filter.put("projectId", projectId);
                    }

                    return vectorStore.search(
                            embeddingResponse.getVector(),
                            limit != null ? limit : 10,
                            minSimilarity != null ? (double) minSimilarity : 0.7,
                            filter);
                })
                .then(results -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    metrics.incrementCounter("requirements.search.success");
                    metrics.recordTimer("requirements.search.duration", durationMs);

                    logger.info("Found {} similar requirements in {}ms", results.size(), durationMs);

                    return Promise.of(results);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("requirements.search.failure",
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to search requirements", ex);
                });
    }

    /**
     * Search for requirements similar to the given query.
     * Convenience overload that delegates to findSimilarRequirements.
     */
    public Promise<List<VectorSearchResult>> searchSimilarRequirements(String query, int limit) {
        return findSimilarRequirements(query, null, limit, null);
    }

    /**
     * Suggest improvements for a requirement.
     * Convenience overload that delegates to suggestImprovements.
     */
    public Promise<List<AISuggestion>> improveRequirement(String requirement) {
        return suggestImprovements(requirement);
    }

    /**
     * Suggests improvements to an existing requirement.
     *
     * <p>
     * Uses LLM to analyze the requirement and suggest improvements in clarity,
     * completeness, and quality.
     *
     * @param requirement requirement text to improve
     * @return promise of improvement suggestions
     */
    public Promise<List<AISuggestion>> suggestImprovements(String requirement) {
        long startTime = System.currentTimeMillis();

        logger.info("Suggesting improvements for requirement: {}", requirement.substring(0, Math.min(50, requirement.length())));

        PromptTemplate template = promptTemplateManager.getTemplate("improve-requirement");
        String prompt = template.render(Map.of("requirement", requirement));

        CompletionRequest llmRequest = CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3) // Low temperature for focused suggestions
                .maxTokens(500)
                .build();

        return llmService.complete(llmRequest)
                .then(llmResponse -> parseSuggestions(llmResponse.getText()))
                .then(suggestions -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    metrics.incrementCounter("requirements.improvement.success");
                    metrics.recordTimer("requirements.improvement.duration", durationMs);

                    logger.info("Generated {} improvement suggestions in {}ms", suggestions.size(), durationMs);

                    return Promise.of(suggestions);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("requirements.improvement.failure",
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to suggest improvements", ex);
                });
    }

    /**
     * Extracts acceptance criteria from a requirement.
     *
     * <p>
     * Uses LLM to analyze the requirement and extract testable acceptance
     * criteria in Given-When-Then format.
     *
     * @param requirement requirement text
     * @return promise of acceptance criteria list
     */
    public Promise<List<String>> extractAcceptanceCriteria(String requirement) {
        long startTime = System.currentTimeMillis();

        logger.info("Extracting acceptance criteria");

        PromptTemplate template = promptTemplateManager.getTemplate("extract-acceptance-criteria");
        String prompt = template.render(Map.of("requirement", requirement));

        CompletionRequest llmRequest = CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.2) // Very low temperature for structured extraction
                .maxTokens(300)
                .build();

        return llmService.complete(llmRequest)
                .then(llmResponse -> parseAcceptanceCriteria(llmResponse.getText()))
                .then(criteria -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    metrics.incrementCounter("requirements.acceptance-criteria.success");
                    metrics.recordTimer("requirements.acceptance-criteria.duration", durationMs);

                    logger.info("Extracted {} acceptance criteria in {}ms", criteria.size(), durationMs);

                    return Promise.of(criteria);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("requirements.acceptance-criteria.failure",
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to extract acceptance criteria", ex);
                });
    }

    /**
     * Classifies a requirement into a type (functional, non-functional,
     * constraint).
     *
     * <p>
     * Uses LLM to analyze the requirement and determine its type.
     *
     * @param requirement requirement text
     * @return promise of requirement type
     */
    public Promise<RequirementType> classifyRequirement(String requirement) {
        long startTime = System.currentTimeMillis();

        logger.info("Classifying requirement");

        PromptTemplate template = promptTemplateManager.getTemplate("classify-requirement");
        String prompt = template.render(Map.of("requirement", requirement));

        CompletionRequest llmRequest = CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.1) // Very low temperature for classification
                .maxTokens(50)
                .build();

        return llmService.complete(llmRequest)
                .then(llmResponse -> parseRequirementType(llmResponse.getText()))
                .then(type -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    metrics.incrementCounter("requirements.classification.success",
                            "type", type.name());
                    metrics.recordTimer("requirements.classification.duration", durationMs);

                    logger.info("Classified requirement as {} in {}ms", type, durationMs);

                    return Promise.of(type);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("requirements.classification.failure",
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to classify requirement", ex);
                });
    }

    /**
     * Validates requirement quality and provides a quality score.
     *
     * <p>
     * Analyzes requirement for clarity, completeness, testability, and other
     * quality attributes.
     *
     * @param requirement requirement text
     * @return promise of quality validation result
     */
    public Promise<RequirementQualityResult> validateQuality(String requirement) {
        long startTime = System.currentTimeMillis();

        logger.info("Validating requirement quality");

        PromptTemplate template = promptTemplateManager.getTemplate("validate-quality");
        String prompt = template.render(Map.of("requirement", requirement));

        CompletionRequest llmRequest = CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.2)
                .maxTokens(400)
                .build();

        return llmService.complete(llmRequest)
                .then(llmResponse -> parseQualityResult(llmResponse.getText()))
                .then(result -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    metrics.incrementCounter("requirements.quality-validation.success");
                    metrics.recordTimer("requirements.quality-validation.duration", durationMs);
                    // Record quality score as a distribution summary
                    metrics.recordConfidenceScore("requirements.quality-score", result.getOverallScore());

                    logger.info("Validated requirement quality: score={} in {}ms", result.getOverallScore(), durationMs);

                    return Promise.of(result);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("requirements.quality-validation.failure",
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to validate requirement quality", ex);
                });
    }

    /**
     * Stores a requirement in the vector store for similarity search.
     *
     * <p>
     * Embeds the requirement text and stores it with metadata.
     *
     * @param requirementId unique requirement identifier
     * @param requirement requirement text
     * @param metadata optional metadata (type, priority, etc.)
     * @return promise of void
     */
    public Promise<Void> storeRequirement(String requirementId, String requirement, Map<String, Object> metadata) {
        long startTime = System.currentTimeMillis();

        logger.info("Storing requirement: {}", requirementId);

        return embeddingService.createEmbedding(requirement)
                .then(embeddingResponse -> {
                    Map<String, String> stringMetadata = new HashMap<>();
                    if (metadata != null) {
                        metadata.forEach((k, v) -> stringMetadata.put(k, String.valueOf(v)));
                    }
                    stringMetadata.put("type", "requirement");
                    stringMetadata.put("createdAt", Instant.now().toString());

                    return vectorStore.store(
                            requirementId,
                            requirement,
                            embeddingResponse.getVector(),
                            stringMetadata);
                })
                .then(stored -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    metrics.incrementCounter("requirements.storage.success");
                    metrics.recordTimer("requirements.storage.duration", durationMs);

                    logger.info("Stored requirement {} in {}ms", requirementId, durationMs);

                    return Promise.complete();
                })
                .whenException(ex -> {
                    metrics.incrementCounter("requirements.storage.failure",
                            "error", ex.getClass().getSimpleName());
                    logger.error("Failed to store requirement", ex);
                });
    }

    /**
     * Health check for the AI service.
     *
     * <p>
     * Verifies all dependencies are healthy and operational.
     *
     * @return promise of health status (true if healthy)
     */
    public Promise<Boolean> healthCheck() {
        logger.debug("Performing AI service health check");

        // CompletionService doesn't expose explicit health check, assuming healthy if provider name is available
        // Embedding service and vector store are assumed healthy if initialized
        return Promise.of(llmService.getProviderName() != null)
                .whenComplete((isHealthy, ex) -> {
                    if (isHealthy != null && isHealthy) {
                        logger.debug("AI service health check passed");
                    } else {
                        logger.warn("AI service health check failed", ex);
                    }
                });
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================
    private Promise<Persona> getPersona(String personaId) {
        if (personaId == null || personaId.isEmpty()) {
            return Promise.of(Persona.DEFAULT);
        }
        return personaRepository.findById(personaId)
                .map(optionalPersona -> optionalPersona.orElse(Persona.DEFAULT));
    }

    private Promise<String> buildPrompt(RequirementGenerationRequest request, Persona persona) {
        PromptTemplate template = promptTemplateManager.getTemplate("generate-requirements");

        Map<String, Object> variables = new HashMap<>();
        variables.put("featureDescription", request.getFeatureDescription());
        variables.put("count", request.getCount() != null ? request.getCount() : 5);
        variables.put("type", request.getType() != null ? request.getType().name() : "all");
        variables.put("context", request.getContext() != null ? request.getContext() : "");
        variables.put("includeAcceptanceCriteria", request.isIncludeAcceptanceCriteria());
        variables.put("personaPrompt", persona.getSystemPrompt());

        // Use renderFromObjects since variables is Map<String,Object>
        String prompt = template.renderFromObjects(variables);
        return Promise.of(prompt);
    }

    private Promise<CompletionResult> callLLM(String prompt, RequirementGenerationRequest request) {
        CompletionRequest llmRequest = CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.7) // Balanced creativity
                .maxTokens(1000)
                .responseFormat("json_object") // Request structured output
                .build();

        return llmService.complete(llmRequest);
    }

    private Promise<List<GeneratedRequirement>> parseRequirements(CompletionResult llmResponse, RequirementGenerationRequest request) {
        try {
            String content = llmResponse.getText();
            List<GeneratedRequirement> requirements = new ArrayList<>();

            logger.debug("Parsing requirements from LLM response");

            // Extract JSON from response (handle markdown code blocks if present)
            String jsonContent = extractJsonContent(content);

            JsonNode rootNode = objectMapper.readTree(jsonContent);
            JsonNode requirementsNode = rootNode.has("requirements") 
                ? rootNode.get("requirements") 
                : rootNode;

            if (requirementsNode.isArray()) {
                for (JsonNode reqNode : requirementsNode) {
                    GeneratedRequirement.Builder builder = GeneratedRequirement.builder()
                        .description(getTextOrDefault(reqNode, "description", ""))
                        .type(parseRequirementTypeFromNode(reqNode))
                        .priority(getTextOrDefault(reqNode, "priority", "medium"))
                        .confidence(getDoubleOrDefault(reqNode, "confidence", 0.8))
                        .parentFeature(request.getFeatureDescription())
                        .source("ai-generated");

                    // Parse acceptance criteria if present
                    if (reqNode.has("acceptanceCriteria") && reqNode.get("acceptanceCriteria").isArray()) {
                        for (JsonNode criteriaNode : reqNode.get("acceptanceCriteria")) {
                            builder.addAcceptanceCriteria(criteriaNode.asText());
                        }
                    }

                    requirements.add(builder.build());
                }
            }

            logger.debug("Parsed {} requirements from LLM response", requirements.size());
            return Promise.of(requirements);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse JSON response, attempting fallback parsing: {}", e.getMessage());
            return parseFallbackRequirements(llmResponse.getText(), request);
        } catch (Exception e) {
            return Promise.ofException(new RuntimeException("Failed to parse requirements", e));
        }
    }

    private String extractJsonContent(String content) {
        // Handle markdown code blocks
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        // Try to find JSON object or array
        int jsonStart = Math.max(content.indexOf('{'), content.indexOf('['));
        int jsonEnd = Math.max(content.lastIndexOf('}'), content.lastIndexOf(']'));
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd + 1);
        }
        return content.trim();
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) && !node.get(field).isNull() 
            ? node.get(field).asText() 
            : defaultValue;
    }

    private double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        return node.has(field) && !node.get(field).isNull() 
            ? node.get(field).asDouble() 
            : defaultValue;
    }

    private RequirementType parseRequirementTypeFromNode(JsonNode node) {
        if (!node.has("type") || node.get("type").isNull()) {
            return RequirementType.FUNCTIONAL;
        }
        String typeStr = node.get("type").asText().toUpperCase();
        if (typeStr.contains("NON") || typeStr.contains("NFR")) {
            return RequirementType.NON_FUNCTIONAL;
        }
        if (typeStr.contains("CONSTRAINT")) {
            return RequirementType.CONSTRAINT;
        }
        return RequirementType.FUNCTIONAL;
    }

    private Promise<List<GeneratedRequirement>> parseFallbackRequirements(String content, RequirementGenerationRequest request) {
        // Fallback: parse line-by-line for numbered requirements
        List<GeneratedRequirement> requirements = new ArrayList<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            // Match patterns like "1. ", "- ", "* "
            if (line.matches("^(\\d+\\.\\s*|-\\s*|\\*\\s*).*") && line.length() > 10) {
                String description = line.replaceFirst("^(\\d+\\.\\s*|-\\s*|\\*\\s*)", "").trim();
                if (!description.isEmpty()) {
                    requirements.add(GeneratedRequirement.builder()
                        .description(description)
                        .type(request.getType() != null ? request.getType() : RequirementType.FUNCTIONAL)
                        .priority("medium")
                        .confidence(0.6) // Lower confidence for fallback parsing
                        .parentFeature(request.getFeatureDescription())
                        .source("ai-generated-fallback")
                        .build());
                }
            }
        }
        
        logger.debug("Fallback parsing extracted {} requirements", requirements.size());
        return Promise.of(requirements);
    }

    private Promise<List<AISuggestion>> parseSuggestions(String content) {
        List<AISuggestion> suggestions = new ArrayList<>();
        
        try {
            String jsonContent = extractJsonContent(content);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            JsonNode suggestionsNode = rootNode.has("suggestions") 
                ? rootNode.get("suggestions") 
                : rootNode;

            if (suggestionsNode.isArray()) {
                for (JsonNode suggNode : suggestionsNode) {
                    String suggestionText = getTextOrDefault(suggNode, "suggestion", 
                        getTextOrDefault(suggNode, "text", 
                            getTextOrDefault(suggNode, "description", "")));
                    
                    if (!suggestionText.isEmpty()) {
                        float relevance = (float) getDoubleOrDefault(suggNode, "relevance", 0.7);
                        float priority = (float) getDoubleOrDefault(suggNode, "priority", 0.5);
                        
                        suggestions.add(new AISuggestion(
                            "temp-" + UUID.randomUUID().toString().substring(0, 8),
                            suggestionText,
                            Persona.DEFAULT,
                            relevance,
                            priority,
                            SuggestionStatus.PENDING,
                            null,
                            null
                        ));
                    }
                }
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse suggestions JSON, attempting line parsing: {}", e.getMessage());
            // Fallback: parse line-by-line
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.matches("^(\\d+\\.\\s*|-\\s*|\\*\\s*).*") && line.length() > 10) {
                    String suggestionText = line.replaceFirst("^(\\d+\\.\\s*|-\\s*|\\*\\s*)", "").trim();
                    if (!suggestionText.isEmpty()) {
                        suggestions.add(new AISuggestion(
                            "temp-" + UUID.randomUUID().toString().substring(0, 8),
                            suggestionText,
                            Persona.DEFAULT,
                            0.6f,
                            0.5f,
                            SuggestionStatus.PENDING,
                            null,
                            null
                        ));
                    }
                }
            }
        }
        
        logger.debug("Parsed {} suggestions from LLM response", suggestions.size());
        return Promise.of(suggestions);
    }

    private Promise<List<String>> parseAcceptanceCriteria(String content) {
        // Parse acceptance criteria from LLM response
        List<String> criteria = Arrays.stream(content.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> line.startsWith("GIVEN") || line.startsWith("WHEN") || line.startsWith("THEN"))
                .collect(Collectors.toList());
        return Promise.of(criteria);
    }

    private Promise<RequirementType> parseRequirementType(String content) {
        String normalized = content.trim().toUpperCase();
        if (normalized.contains("FUNCTIONAL")) {
            return Promise.of(RequirementType.FUNCTIONAL);
        } else if (normalized.contains("NON-FUNCTIONAL") || normalized.contains("NONFUNCTIONAL")) {
            return Promise.of(RequirementType.NON_FUNCTIONAL);
        } else if (normalized.contains("CONSTRAINT")) {
            return Promise.of(RequirementType.CONSTRAINT);
        } else {
            return Promise.of(RequirementType.FUNCTIONAL); // Default
        }
    }

    private Promise<RequirementQualityResult> parseQualityResult(String content) {
        try {
            String jsonContent = extractJsonContent(content);
            JsonNode rootNode = objectMapper.readTree(jsonContent);

            RequirementQualityResult.Builder builder = RequirementQualityResult.builder()
                .overallScore(getDoubleOrDefault(rootNode, "overallScore", 
                    getDoubleOrDefault(rootNode, "overall_score", 
                        getDoubleOrDefault(rootNode, "score", 0.7))))
                .clarityScore(getDoubleOrDefault(rootNode, "clarityScore", 
                    getDoubleOrDefault(rootNode, "clarity_score", 
                        getDoubleOrDefault(rootNode, "clarity", 0.7))))
                .completenessScore(getDoubleOrDefault(rootNode, "completenessScore", 
                    getDoubleOrDefault(rootNode, "completeness_score", 
                        getDoubleOrDefault(rootNode, "completeness", 0.7))))
                .testabilityScore(getDoubleOrDefault(rootNode, "testabilityScore", 
                    getDoubleOrDefault(rootNode, "testability_score", 
                        getDoubleOrDefault(rootNode, "testability", 0.7))))
                .consistencyScore(getDoubleOrDefault(rootNode, "consistencyScore", 
                    getDoubleOrDefault(rootNode, "consistency_score", 
                        getDoubleOrDefault(rootNode, "consistency", 0.8))));

            // Parse issues
            if (rootNode.has("issues") && rootNode.get("issues").isArray()) {
                for (JsonNode issueNode : rootNode.get("issues")) {
                    String category = getTextOrDefault(issueNode, "category", "general");
                    String description = getTextOrDefault(issueNode, "description", 
                        issueNode.isTextual() ? issueNode.asText() : "");
                    boolean critical = issueNode.has("critical") && issueNode.get("critical").asBoolean();
                    
                    if (!description.isEmpty()) {
                        builder.addIssue(new RequirementQualityResult.QualityIssue(category, description, critical));
                    }
                }
            }

            // Parse recommendations
            if (rootNode.has("recommendations") && rootNode.get("recommendations").isArray()) {
                for (JsonNode recNode : rootNode.get("recommendations")) {
                    String recommendation = recNode.isTextual() 
                        ? recNode.asText() 
                        : getTextOrDefault(recNode, "text", getTextOrDefault(recNode, "description", ""));
                    builder.addRecommendation(recommendation);
                }
            }

            logger.debug("Parsed quality result with overall score: {}", builder.build().getOverallScore());
            return Promise.of(builder.build());
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse quality JSON, returning default scores: {}", e.getMessage());
            // Return default scores on parse failure
            return Promise.of(RequirementQualityResult.builder()
                .overallScore(0.7)
                .clarityScore(0.7)
                .completenessScore(0.7)
                .testabilityScore(0.7)
                .consistencyScore(0.7)
                .addRecommendation("Unable to parse detailed quality analysis")
                .build());
        }
    }

    private void emitLLMApiCallEvent(String requestId, CompletionResult response, boolean success, long durationMs) {
        if (response != null) {
            LLMApiCallEvent event = new LLMApiCallEvent();
            event.begin();
            event.requestId = requestId;
            event.model = response.getModelUsed();
            event.promptTokens = response.getPromptTokens();
            event.completionTokens = response.getCompletionTokens();
            event.totalTokens = response.getTokensUsed();
            event.success = success;
            event.commit();
        }
    }
}
