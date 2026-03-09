package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TutorPutor Content Generation Agent implementing GAA lifecycle.
 * 
 * <p>This agent handles the complete content generation workflow:
 * <ul>
 *   <li><b>PERCEIVE</b>: Validates request, enriches with learner context</li>
 *   <li><b>REASON</b>: Generates content using LLM + knowledge base</li>
 *   <li><b>ACT</b>: Validates content, checks curriculum alignment</li>
 *   <li><b>CAPTURE</b>: Stores generation episode with quality metrics</li>
 *   <li><b>REFLECT</b>: Learns from learner feedback, improves prompts</li>
 * </ul>
 * 
 * <p><b>Adaptive Features:</b>
 * <ul>
 *   <li>Personalization based on learner history</li>
 *   <li>Automatic difficulty adjustment</li>
 *   <li>Learning style adaptation</li>
 *   <li>Knowledge gap detection and remediation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose TutorPutor content generation agent with GAA lifecycle
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 */
public class ContentGenerationAgent extends BaseAgent<ContentGenerationRequest, ContentGenerationResponse> {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ContentQualityValidator qualityValidator;
    
    /**
     * Creates a new ContentGenerationAgent.
     *
     * @param generator the output generator for content generation
     * @param knowledgeBaseService the knowledge base service for fact-checking
     * @param qualityValidator the content quality validator
     */
    public ContentGenerationAgent(
            @NotNull OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> generator,
            @NotNull KnowledgeBaseService knowledgeBaseService,
            @NotNull ContentQualityValidator qualityValidator) {
        super("tutorputor-content-agent", generator);
        this.knowledgeBaseService = knowledgeBaseService;
        this.qualityValidator = qualityValidator;
    }

    /**
     * Phase 1: PERCEIVE - Validates and enriches the content generation request.
     * 
     * <p>Enrichment includes:
     * <ul>
     *   <li>Loading learner profile from memory</li>
     *   <li>Analyzing previous interactions</li>
     *   <li>Detecting knowledge gaps</li>
     *   <li>Adjusting difficulty based on performance</li>
     * </ul>
     */
    @Override
    @NotNull
    protected ContentGenerationRequest perceive(
            @NotNull ContentGenerationRequest request,
            @NotNull AgentContext context) {
        
        context.getLogger().info("PERCEIVE: Processing request for topic '{}' at grade {}",
            request.topic(), request.gradeLevel());
        
        // Validate required fields
        validateRequest(request);
        
        // Create enriched request with learner context
        return enrichWithLearnerContext(request, context);
    }

    /**
     * Phase 3: ACT - Validates generated content and checks curriculum alignment.
     * 
     * <p>Actions include:
     * <ul>
     *   <li>Fact-checking claims against knowledge base</li>
     *   <li>Verifying curriculum alignment</li>
     *   <li>Checking age-appropriateness</li>
     *   <li>Ensuring accessibility standards</li>
     * </ul>
     */
    @Override
    @NotNull
    protected Promise<ContentGenerationResponse> act(
            @NotNull ContentGenerationResponse response,
            @NotNull AgentContext context) {
        
        context.getLogger().info("ACT: Validating generated content quality");
        
        // Validate content quality
        return qualityValidator.validate(response, context)
            .map(validationResult -> {
                if (!validationResult.passed()) {
                    context.getLogger().warn("Content quality validation failed: {}",
                        validationResult.issues());
                    
                    // Return response with validation warnings
                    return response.withValidationIssues(validationResult.issues());
                }
                
                context.getLogger().info("ACT: Content passed quality validation");
                return response;
            })
            .then(validatedResponse -> {
                // Check curriculum alignment asynchronously
                return knowledgeBaseService.checkCurriculumAlignment(
                        validatedResponse.content(),
                        validatedResponse.domain(),
                        validatedResponse.gradeLevel())
                    .map(alignment -> {
                        context.recordMetric("tutorputor.content.curriculum_alignment", 
                            alignment.confidenceScore());
                        
                        return validatedResponse.withCurriculumAlignment(
                            alignment.aligned(),
                            alignment.alignedTopics());
                    });
            });
    }

    /**
     * Phase 4: CAPTURE - Stores the generation episode with quality metrics.
     * 
     * <p>Captures:
     * <ul>
     *   <li>Request and response details</li>
     *   <li>Generation metadata (time, cost, tokens)</li>
     *   <li>Quality scores and validation results</li>
     *   <li>Curriculum alignment information</li>
     * </ul>
     */
    @Override
    @NotNull
    protected Promise<Void> capture(
            @NotNull ContentGenerationRequest request,
            @NotNull ContentGenerationResponse response,
            @NotNull AgentContext context) {
        
        context.getLogger().debug("CAPTURE: Storing content generation episode");
        
        // Build context map with all relevant metadata
        Map<String, Object> episodeContext = Map.of(
            "topic", request.topic(),
            "domain", request.domain(),
            "gradeLevel", request.gradeLevel(),
            "contentType", request.contentType().name(),
            "learnerId", request.learnerId() != null ? request.learnerId() : "anonymous",
            "qualityScore", response.qualityScore(),
            "curriculumAligned", response.curriculumAligned(),
            "generationTimeMs", response.generationTimeMs(),
            "tokenCount", response.tokenCount()
        );
        
        // Build detailed episode
        Episode episode = Episode.builder()
            .agentId(getAgentId())
            .turnId(context.getTurnId())
            .timestamp(Instant.now())
            .input(buildEpisodeInput(request))
            .output(buildEpisodeOutput(response))
            .context(episodeContext)
            .tags(List.of("content-generation", request.domain(), request.contentType().name()))
            .reward(response.qualityScore()) // Use quality score as reward
            .build();
        
        // Store episode
        return context.getMemoryStore().storeEpisode(episode)
            .then(stored -> {
                // Also store as semantic fact for future reference (triple format)
                if (response.qualityScore() >= 0.8) {
                    Fact fact = Fact.builder()
                        .agentId(getAgentId())
                        .subject(request.topic())
                        .predicate("has_quality_content")
                        .object(request.contentType().name())
                        .confidence(response.qualityScore())
                        .source("generated")
                        .metadata(Map.of(
                            "domain", request.domain(),
                            "gradeLevel", request.gradeLevel()
                        ))
                        .build();
                    
                    return context.getMemoryStore().storeFact(fact)
                        .map(f -> null);
                }
                return Promise.of(null);
            });
    }

    /**
     * Phase 5: REFLECT - Analyzes generation patterns and improves prompts.
     * 
     * <p>Reflection includes:
     * <ul>
     *   <li>Analyzing quality trends</li>
     *   <li>Identifying successful prompt patterns</li>
     *   <li>Detecting common failure modes</li>
     *   <li>Updating generation policies</li>
     * </ul>
     */
    @Override
    @NotNull
    protected Promise<Void> reflect(
            @NotNull ContentGenerationRequest request,
            @NotNull ContentGenerationResponse response,
            @NotNull AgentContext context) {
        
        context.getLogger().debug("REFLECT: Analyzing generation patterns (async)");
        
        // Get recent episodes for this agent
        MemoryFilter filter = MemoryFilter.builder()
            .agentId(getAgentId())
            .build();
        
        return context.getMemoryStore().queryEpisodes(filter, 20)
            .then(episodes -> {
                // Filter to this domain
                List<Episode> domainEpisodes = episodes.stream()
                    .filter(e -> request.domain().equals(e.getContext().get("domain")))
                    .toList();
                
                if (domainEpisodes.size() < 5) {
                    // Not enough data to reflect
                    return Promise.complete();
                }
                
                // Analyze quality trends (quality score stored in reward field)
                double avgQuality = domainEpisodes.stream()
                    .mapToDouble(e -> e.getReward() != null ? e.getReward() : 0.5)
                    .average()
                    .orElse(0.5);
                
                context.recordMetric("tutorputor.reflection.avg_quality", avgQuality);
                
                // If quality is consistently high, extract successful patterns
                if (avgQuality >= 0.85) {
                    return extractSuccessfulPatterns(domainEpisodes, context);
                }
                
                // If quality is low, identify failure patterns
                if (avgQuality < 0.6) {
                    return identifyFailurePatterns(domainEpisodes, context);
                }
                
                return Promise.complete();
            });
    }

    private void validateRequest(ContentGenerationRequest request) {
        if (request.topic() == null || request.topic().isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }
        if (request.gradeLevel() == null || request.gradeLevel().isBlank()) {
            throw new IllegalArgumentException("Grade level is required");
        }
        if (request.domain() == null) {
            throw new IllegalArgumentException("Domain is required");
        }
        if (request.contentType() == null) {
            throw new IllegalArgumentException("Content type is required");
        }
    }

    private ContentGenerationRequest enrichWithLearnerContext(
            ContentGenerationRequest request,
            AgentContext context) {
        
        if (request.learnerId() == null) {
            // Anonymous request, no enrichment possible
            return request;
        }
        
        // Load learner preferences from memory
        List<String> preferences = loadLearnerPreferences(request.learnerId(), context);
        
        // Adjust difficulty based on past performance
        String adjustedDifficulty = adjustDifficultyForLearner(request.learnerId(), request, context);
        
        // Detect knowledge gaps
        List<String> knowledgeGaps = detectKnowledgeGaps(request.learnerId(), request.topic(), context);
        
        return new ContentGenerationRequest(
            request.topic(),
            request.domain(),
            request.gradeLevel(),
            request.contentType(),
            request.learnerId(),
            adjustedDifficulty,
            preferences,
            knowledgeGaps,
            request.additionalContext()
        );
    }

    private List<String> loadLearnerPreferences(String learnerId, AgentContext context) {
        // In production, this would query the memory store
        // For now, return default preferences
        return List.of("visual-learning", "step-by-step-explanations");
    }

    private String adjustDifficultyForLearner(
            String learnerId,
            ContentGenerationRequest request,
            AgentContext context) {
        // In production, analyze learner's past performance
        // and adjust difficulty accordingly
        return request.difficulty() != null ? request.difficulty() : "medium";
    }

    private List<String> detectKnowledgeGaps(String learnerId, String topic, AgentContext context) {
        // In production, analyze learner's interaction history
        // to identify prerequisite concepts that may be missing
        return new ArrayList<>();
    }

    private String buildEpisodeInput(ContentGenerationRequest request) {
        return String.format(
            "Topic: %s, Domain: %s, Grade: %s, Type: %s",
            request.topic(),
            request.domain(),
            request.gradeLevel(),
            request.contentType()
        );
    }

    private String buildEpisodeOutput(ContentGenerationResponse response) {
        return String.format(
            "Quality: %.2f, Tokens: %d, Aligned: %s",
            response.qualityScore(),
            response.tokenCount(),
            response.curriculumAligned()
        );
    }

    private Promise<Void> extractSuccessfulPatterns(List<Episode> episodes, AgentContext context) {
        context.getLogger().info("Extracting successful generation patterns from {} episodes", 
            episodes.size());
        
        // In production, this would:
        // 1. Use LLM to analyze successful prompts
        // 2. Extract common patterns
        // 3. Store as procedural memory (Policy)
        
        Policy pattern = Policy.builder()
            .agentId("ContentGenerationAgent")
            .situation("content generation quality >= 0.85")
            .action("use-detailed-examples; include-visual-cues; provide-scaffolding")
            .confidence(0.85)
            .metadata(Map.of(
                "minEpisodes", 5,
                "avgQuality", 0.85,
                "actions", List.of(
                    "use-detailed-examples",
                    "include-visual-cues",
                    "provide-scaffolding"
                )
            ))
            .build();
        
        return context.getMemoryStore().storePolicy(pattern)
            .map(p -> null);
    }

    private Promise<Void> identifyFailurePatterns(List<Episode> episodes, AgentContext context) {
        context.getLogger().warn("Identifying failure patterns from {} episodes", 
            episodes.size());
        
        // In production, this would:
        // 1. Analyze low-quality generations
        // 2. Identify common issues
        // 3. Flag for human review
        
        context.recordMetric("tutorputor.reflection.failure_analysis_triggered", 1);
        
        return Promise.complete();
    }
}
