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
import java.util.Optional;

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
    private static final int MIN_EPISODES_FOR_PATTERN = 1;
    private static final int MIN_EPISODES_FOR_POLICY_EXTRACTION = 3;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    private static final double LOW_QUALITY_THRESHOLD = 0.6;

    private final KnowledgeBaseService knowledgeBaseService;
    private final ContentQualityValidator qualityValidator;
    private final LearnerProfileHttpClient learnerProfileClient;
    
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
        this(generator, knowledgeBaseService, qualityValidator, LearnerProfileHttpClient.createFromEnvironment());
    }

    public ContentGenerationAgent(
            @NotNull OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> generator,
            @NotNull KnowledgeBaseService knowledgeBaseService,
            @NotNull ContentQualityValidator qualityValidator,
            @NotNull LearnerProfileHttpClient learnerProfileClient) {
        super("tutorputor-content-agent", generator);
        this.knowledgeBaseService = knowledgeBaseService;
        this.qualityValidator = qualityValidator;
        this.learnerProfileClient = learnerProfileClient;
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

        Promise<Void> immediateLearnerReflection = Promise.complete();
        if (request.learnerId() != null && !request.learnerId().isBlank()) {
            double qualityScore = analyzeGenerationQuality(response);
            context.recordMetric("tutorputor.reflection.instant_quality", qualityScore);

            if (qualityScore >= HIGH_CONFIDENCE_THRESHOLD) {
                immediateLearnerReflection = extractAndStoreStrategy(
                    request,
                    response,
                    qualityScore,
                    context
                );
            }

            immediateLearnerReflection = immediateLearnerReflection.then($ ->
                updateLearnerModel(request.learnerId(), request, response, qualityScore, context)
            );
        }
        
        return immediateLearnerReflection.then($ -> reflectOnRecentEpisodes(request, context));
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

        Optional<LearnerProfileHttpClient.LearnerPersonalizationSnapshot> snapshot =
            loadPersonalizationSnapshot(request.learnerId(), request.topic(), context);

        List<String> preferences = loadLearnerPreferences(snapshot, context);
        String adjustedDifficulty = adjustDifficultyForLearner(snapshot, request, context);
        List<String> knowledgeGaps = detectKnowledgeGaps(snapshot, request.topic(), context);
        
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

    private Optional<LearnerProfileHttpClient.LearnerPersonalizationSnapshot> loadPersonalizationSnapshot(
            String learnerId,
            String topic,
            AgentContext context) {
        Optional<LearnerProfileHttpClient.LearnerPersonalizationSnapshot> snapshot =
            learnerProfileClient.getPersonalization(learnerId, topic);

        snapshot.ifPresent(value -> context.recordMetric("tutorputor.content.personalization_lookup", 1));
        return snapshot;
    }

    private List<String> loadLearnerPreferences(
            Optional<LearnerProfileHttpClient.LearnerPersonalizationSnapshot> snapshot,
            AgentContext context) {
        if (snapshot.isPresent() && snapshot.get().preferences != null && !snapshot.get().preferences.isEmpty()) {
            return snapshot.get().preferences;
        }

        context.recordMetric("tutorputor.content.personalization_fallback", 1);
        return List.of("visual-learning", "step-by-step-explanations");
    }

    private String adjustDifficultyForLearner(
            Optional<LearnerProfileHttpClient.LearnerPersonalizationSnapshot> snapshot,
            ContentGenerationRequest request,
            AgentContext context) {
        if (request.difficulty() != null && !request.difficulty().isBlank()) {
            return request.difficulty();
        }

        if (snapshot.isPresent() && snapshot.get().adjustedDifficulty != null && !snapshot.get().adjustedDifficulty.isBlank()) {
            return snapshot.get().adjustedDifficulty;
        }

        context.recordMetric("tutorputor.content.personalization_fallback", 1);
        return "medium";
    }

    private List<String> detectKnowledgeGaps(
            Optional<LearnerProfileHttpClient.LearnerPersonalizationSnapshot> snapshot,
            String topic,
            AgentContext context) {
        if (snapshot.isPresent() && snapshot.get().knowledgeGaps != null) {
            return snapshot.get().knowledgeGaps;
        }

        context.recordMetric("tutorputor.content.personalization_fallback", 1);
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

    private Promise<Void> reflectOnRecentEpisodes(
            ContentGenerationRequest request,
            AgentContext context) {
        MemoryFilter filter = MemoryFilter.builder()
            .agentId(getAgentId())
            .build();

        return context.getMemoryStore().queryEpisodes(filter, 20)
            .then(episodes -> {
                List<Episode> domainEpisodes = episodes.stream()
                    .filter(e -> request.domain().equals(e.getContext().get("domain")))
                    .toList();

                if (domainEpisodes.size() < MIN_EPISODES_FOR_PATTERN) {
                    return Promise.complete();
                }

                double avgQuality = domainEpisodes.stream()
                    .mapToDouble(e -> e.getReward() != null ? e.getReward() : 0.5)
                    .average()
                    .orElse(0.5);

                context.recordMetric("tutorputor.reflection.avg_quality", avgQuality);

                if (domainEpisodes.size() >= MIN_EPISODES_FOR_POLICY_EXTRACTION
                        && avgQuality >= HIGH_CONFIDENCE_THRESHOLD) {
                    return extractSuccessfulPatterns(domainEpisodes, avgQuality, context);
                }

                if (avgQuality < LOW_QUALITY_THRESHOLD) {
                    return identifyFailurePatterns(domainEpisodes, context);
                }

                return Promise.complete();
            });
    }

    private double analyzeGenerationQuality(ContentGenerationResponse response) {
        double alignmentBoost = response.curriculumAligned() ? 0.1 : 0.0;
        double validationBoost = (response.validationIssues() == null || response.validationIssues().isEmpty()) ? 0.05 : 0.0;
        double tokenBoost = response.tokenCount() >= 150 ? 0.05 : 0.0;
        return Math.min(1.0, response.qualityScore() * 0.8 + alignmentBoost + validationBoost + tokenBoost);
    }

    private Promise<Void> extractAndStoreStrategy(
            ContentGenerationRequest request,
            ContentGenerationResponse response,
            double qualityScore,
            AgentContext context) {
        String action = buildStrategyAction(request, response);
        Policy policy = Policy.builder()
            .agentId(getAgentId())
            .situation(String.format("learner:%s topic:%s content:%s",
                request.learnerId(),
                request.topic(),
                request.contentType().name()))
            .action(action)
            .confidence(qualityScore)
            .metadata(Map.of(
                "topic", request.topic(),
                "domain", request.domain(),
                "gradeLevel", request.gradeLevel(),
                "difficulty", request.difficulty() != null ? request.difficulty() : "medium",
                "qualityScore", qualityScore,
                "aligned", response.curriculumAligned()
            ))
            .build();

        context.recordMetric("tutorputor.reflection.immediate_strategy_extracted", 1);
        return context.getMemoryStore().storePolicy(policy).map($ -> null);
    }

    private String buildStrategyAction(
            ContentGenerationRequest request,
            ContentGenerationResponse response) {
        List<String> actions = new ArrayList<>();
        actions.add("target-difficulty:" + (request.difficulty() != null ? request.difficulty() : "medium"));
        actions.add("content-type:" + request.contentType().name().toLowerCase());
        if (request.learnerPreferences() != null) {
            request.learnerPreferences().stream()
                .limit(3)
                .forEach(preference -> actions.add("prefer:" + preference));
        }
        if (response.curriculumAligned()) {
            actions.add("retain-curriculum-alignment");
        }
        if (response.validationIssues() == null || response.validationIssues().isEmpty()) {
            actions.add("retain-complete-structure");
        }
        return String.join("; ", actions);
    }

    private Promise<Void> updateLearnerModel(
            String learnerId,
            ContentGenerationRequest request,
            ContentGenerationResponse response,
            double qualityScore,
            AgentContext context) {
        Fact fact = Fact.builder()
            .agentId(getAgentId())
            .subject(learnerId)
            .predicate("responds_well_to")
            .object(request.contentType().name().toLowerCase())
            .confidence(qualityScore)
            .source("reflection")
            .metadata(Map.of(
                "topic", request.topic(),
                "domain", request.domain(),
                "difficulty", request.difficulty() != null ? request.difficulty() : "medium",
                "curriculumAligned", response.curriculumAligned(),
                "qualityScore", qualityScore
            ))
            .build();

        context.recordMetric("tutorputor.reflection.learner_model_updated", 1);
        return context.getMemoryStore().storeFact(fact).map($ -> null);
    }

    private Promise<Void> extractSuccessfulPatterns(
            List<Episode> episodes,
            double avgQuality,
            AgentContext context) {
        context.getLogger().info("Extracting successful generation patterns from {} episodes", 
            episodes.size());
        
        // In production, this would:
        // 1. Use LLM to analyze successful prompts
        // 2. Extract common patterns
        // 3. Store as procedural memory (Policy)
        
        Policy pattern = Policy.builder()
            .agentId("ContentGenerationAgent")
            .situation("content generation quality >= " + HIGH_CONFIDENCE_THRESHOLD)
            .action("use-detailed-examples; include-visual-cues; provide-scaffolding")
            .confidence(avgQuality)
            .metadata(Map.of(
                "minEpisodes", MIN_EPISODES_FOR_POLICY_EXTRACTION,
                "avgQuality", avgQuality,
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
