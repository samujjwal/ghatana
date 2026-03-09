package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import com.ghatana.tutorputor.contentstudio.prompt.PromptTemplates;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * OutputGenerator that uses LLM for educational content generation.
 * 
 * <p>This generator:
 * <ul>
 *   <li>Builds context-aware prompts using PromptTemplates</li>
 *   <li>Calls LLM via LLMGateway</li>
 *   <li>Parses and validates responses</li>
 *   <li>Enriches with knowledge base information</li>
 *   <li>Tracks metrics for quality monitoring</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose LLM-based content generation for TutorPutor agent
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class ContentGenerationOutputGenerator 
        implements OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationOutputGenerator.class);
    
    private final LLMGateway llmGateway;
    private final KnowledgeBaseService knowledgeBaseService;
    private final Executor executor;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter generationsCounter;
    private final Counter errorCounter;
    private final Timer generationTimer;
    
    private static final GeneratorMetadata METADATA = GeneratorMetadata.builder()
        .name("ContentGenerationOutputGenerator")
        .type("llm")
        .description("LLM-based educational content generation with knowledge base enrichment")
        .version("1.0.0")
        .build();

    /**
     * Creates a new ContentGenerationOutputGenerator.
     *
     * @param llmGateway the LLM gateway for completions
     * @param knowledgeBaseService the knowledge base for enrichment
     * @param executor the executor for async operations
     * @param meterRegistry the metrics registry
     */
    public ContentGenerationOutputGenerator(
            @NotNull LLMGateway llmGateway,
            @NotNull KnowledgeBaseService knowledgeBaseService,
            @NotNull Executor executor,
            @NotNull MeterRegistry meterRegistry) {
        this.llmGateway = llmGateway;
        this.knowledgeBaseService = knowledgeBaseService;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        
        this.generationsCounter = Counter.builder("tutorputor.generator.generations")
            .description("Number of content generations")
            .register(meterRegistry);
        this.errorCounter = Counter.builder("tutorputor.generator.errors")
            .description("Number of generation errors")
            .register(meterRegistry);
        this.generationTimer = Timer.builder("tutorputor.generator.latency")
            .description("Content generation latency")
            .register(meterRegistry);
    }

    @Override
    @NotNull
    public Promise<ContentGenerationResponse> generate(
            @NotNull ContentGenerationRequest request,
            @NotNull AgentContext context) {
        
        Instant start = Instant.now();
        generationsCounter.increment();
        
        context.getLogger().info("Generating {} content for topic: {}",
            request.contentType(), request.topic());
        
        // Build the appropriate prompt based on content type
        String prompt = buildPrompt(request, context);
        
        // Check budget before making LLM call
        Double budget = context.getRemainingBudget();
        if (budget != null && budget < 0.01) {
            errorCounter.increment();
            return Promise.ofException(new BudgetExceededException("Insufficient budget for generation"));
        }
        
        // Create completion request
        CompletionRequest completionRequest = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(getMaxTokensForType(request.contentType()))
            .temperature(getTemperatureForType(request.contentType()))
            .topP(0.9)
            .build();

        // Execute LLM call (non-blocking)
        return llmGateway.complete(completionRequest)
            .then(llmResponse -> {
                // Deduct cost from budget
                double cost = estimateCost(llmResponse.getTokensUsed());
                try {
                    context.deductCost(cost);
                } catch (Exception e) {
                    // Log but don't fail - cost tracking is best-effort
                    context.getLogger().warn("Failed to deduct cost: {}", e.getMessage());
                }
                
                // Parse and build response
                String content = llmResponse.getText();
                long generationTimeMs = Duration.between(start, Instant.now()).toMillis();
                
                // Record metrics
                generationTimer.record(Duration.between(start, Instant.now()));
                context.recordMetric("tutorputor.generation.tokens", llmResponse.getTokensUsed());
                
                // Build initial response
                ContentGenerationResponse response = ContentGenerationResponse.builder()
                    .content(content)
                    .domain(request.domain())
                    .gradeLevel(request.gradeLevel())
                    .contentType(request.contentType())
                    .qualityScore(0.7) // Initial score, will be updated by validator
                    .generationTimeMs(generationTimeMs)
                    .tokenCount(llmResponse.getTokensUsed())
                    .metadata(Map.of(
                        "model", llmResponse.getModelUsed(),
                        "promptVersion", "1.0"
                    ))
                    .build();
                
                // Enrich with knowledge base verification
                return enrichWithKnowledgeBase(response, request, context);
            })
            .mapException(e -> {
                errorCounter.increment();
                context.getLogger().error("Content generation failed", e);
                return new RuntimeException("Content generation failed: " + e.getMessage(), e);
            });
    }

    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return METADATA;
    }

    private String buildPrompt(ContentGenerationRequest request, AgentContext context) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add learner context if available
        if (request.learnerPreferences() != null && !request.learnerPreferences().isEmpty()) {
            promptBuilder.append("Learner preferences: ")
                .append(String.join(", ", request.learnerPreferences()))
                .append("\n\n");
        }
        
        if (request.knowledgeGaps() != null && !request.knowledgeGaps().isEmpty()) {
            promptBuilder.append("Address these knowledge gaps: ")
                .append(String.join(", ", request.knowledgeGaps()))
                .append("\n\n");
        }
        
        // Build content-type specific prompt
        String contentPrompt = switch (request.contentType()) {
            case CLAIM -> PromptTemplates.buildClaimsPrompt(
                request.topic(),
                request.domain(),
                request.gradeLevel(),
                5 // Number of claims
            );
            case EXAMPLE -> PromptTemplates.buildExamplesPrompt(
                request.topic(),
                request.domain(),
                request.gradeLevel(),
                "concrete", // Example type
                3 // Number of examples
            );
            case SIMULATION -> PromptTemplates.buildSimulationPrompt(
                request.topic(),
                request.domain(),
                request.gradeLevel()
            );
            case ANIMATION -> PromptTemplates.buildAnimationPrompt(
                request.topic(),
                request.domain(),
                request.gradeLevel()
            );
            case EXERCISE, ASSESSMENT, LESSON -> buildExercisePrompt(request);
        };
        
        promptBuilder.append(contentPrompt);
        
        // Add difficulty modifier
        if (request.difficulty() != null) {
            promptBuilder.append("\n\nDifficulty level: ").append(request.difficulty());
        }
        
        return promptBuilder.toString();
    }

    private String buildExercisePrompt(ContentGenerationRequest request) {
        return String.format("""
            Generate practice exercises for the following educational content:
            
            Topic: %s
            Domain: %s
            Grade Level: %s
            Type: %s
            
            Create exercises that:
            1. Test understanding of key concepts
            2. Progress from basic recall to application
            3. Include clear instructions
            4. Provide scaffolding hints
            5. Are engaging and age-appropriate
            
            Format the response as JSON with the following structure:
            {
              "exercises": [
                {
                  "id": "exercise-1",
                  "type": "multiple-choice|fill-blank|short-answer|matching",
                  "question": "The question text",
                  "options": ["option1", "option2"],
                  "correctAnswer": "The correct answer",
                  "explanation": "Why this is correct",
                  "difficulty": "easy|medium|hard",
                  "hints": ["hint1", "hint2"]
                }
              ]
            }
            """,
            request.topic(),
            request.domain(),
            request.gradeLevel(),
            request.contentType()
        );
    }

    private int getMaxTokensForType(ContentGenerationRequest.ContentType type) {
        return switch (type) {
            case CLAIM -> 500;
            case EXAMPLE -> 1000;
            case SIMULATION -> 2000;
            case ANIMATION -> 2000;
            case EXERCISE -> 1500;
            case ASSESSMENT -> 2000;
            case LESSON -> 3000;
        };
    }

    private double getTemperatureForType(ContentGenerationRequest.ContentType type) {
        return switch (type) {
            case CLAIM -> 0.3; // More deterministic for factual claims
            case EXAMPLE -> 0.7; // More creative for examples
            case SIMULATION -> 0.5;
            case ANIMATION -> 0.6;
            case EXERCISE -> 0.4;
            case ASSESSMENT -> 0.3;
            case LESSON -> 0.5;
        };
    }

    private double estimateCost(int tokens) {
        // Rough estimate: $0.002 per 1K tokens (GPT-3.5 pricing)
        return tokens * 0.000002;
    }

    private Promise<ContentGenerationResponse> enrichWithKnowledgeBase(
            ContentGenerationResponse response,
            ContentGenerationRequest request,
            AgentContext context) {
        
        // Verify content against knowledge base
        return knowledgeBaseService.verifyLearningClaim(
                response.content(),
                request.domain(),
                request.gradeLevel())
            .map(verification -> {
                // Adjust quality score based on verification
                double adjustedScore = response.qualityScore();
                
                if (verification.status() == KnowledgeBaseService.VerificationStatus.VERIFIED) {
                    adjustedScore = Math.min(1.0, adjustedScore + 0.2);
                } else if (verification.status() == KnowledgeBaseService.VerificationStatus.DISPUTED) {
                    adjustedScore = Math.max(0.0, adjustedScore - 0.3);
                }
                
                context.recordMetric("tutorputor.verification.confidence", 
                    verification.overallConfidence());
                
                return ContentGenerationResponse.builder()
                    .content(response.content())
                    .domain(response.domain())
                    .gradeLevel(response.gradeLevel())
                    .contentType(response.contentType())
                    .qualityScore(adjustedScore)
                    .curriculumAligned(verification.status() == 
                        KnowledgeBaseService.VerificationStatus.VERIFIED)
                    .alignedTopics(verification.relatedTopics())
                    .generationTimeMs(response.generationTimeMs())
                    .tokenCount(response.tokenCount())
                    .metadata(response.metadata())
                    .build();
            });
    }

    /**
     * Exception thrown when budget is exceeded.
     */
    public static class BudgetExceededException extends RuntimeException {
        public BudgetExceededException(String message) {
            super(message);
        }
    }
}
