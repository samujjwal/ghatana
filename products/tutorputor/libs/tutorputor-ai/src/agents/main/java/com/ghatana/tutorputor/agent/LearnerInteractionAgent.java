package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.Preference;
import com.ghatana.agent.framework.runtime.BaseAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Learner Interaction Agent implementing GAA lifecycle for adaptive tutoring.
 * 
 * <p>This agent handles real-time learner interactions:
 * <ul>
 *   <li><b>PERCEIVE</b>: Understand learner's action, context, and state</li>
 *   <li><b>REASON</b>: Generate personalized response using learner model</li>
 *   <li><b>ACT</b>: Deliver adaptive feedback and next steps</li>
 *   <li><b>CAPTURE</b>: Record interaction and update learner model</li>
 *   <li><b>REFLECT</b>: Identify learning patterns and adjust strategy</li>
 * </ul>
 * 
 * <p><b>Adaptive Features:</b>
 * <ul>
 *   <li>Real-time performance tracking</li>
 *   <li>Adaptive difficulty scaling</li>
 *   <li>Personalized hint generation</li>
 *   <li>Mastery-based progression</li>
 *   <li>Emotional state awareness</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Adaptive tutoring agent with GAA lifecycle
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 */
public class LearnerInteractionAgent extends BaseAgent<LearnerAction, TutoringResponse> {

    private static final double MASTERY_THRESHOLD = 0.85;
    private static final int MIN_ATTEMPTS_FOR_MASTERY = 3;

    /**
     * Creates a new LearnerInteractionAgent.
     *
     * @param generator the output generator for response generation
     */
    public LearnerInteractionAgent(
            @NotNull OutputGenerator<LearnerAction, TutoringResponse> generator) {
        super("tutorputor-learner-agent", generator);
    }

    /**
     * Phase 1: PERCEIVE - Understands the learner's action and context.
     * 
     * <p>Analysis includes:
     * <ul>
     *   <li>Understanding the action type (answer, hint request, navigation)</li>
     *   <li>Loading learner's history for this topic</li>
     *   <li>Assessing current performance level</li>
     *   <li>Detecting emotional cues (frustration, confusion)</li>
     * </ul>
     */
    @Override
    @NotNull
    protected LearnerAction perceive(
            @NotNull LearnerAction action,
            @NotNull AgentContext context) {
        
        context.getLogger().info("PERCEIVE: Learner {} performed {} on topic {}",
            action.learnerId(), action.actionType(), action.topicId());
        
        // Validate action
        validateAction(action);
        
        // Enrich action with learner state
        return enrichWithLearnerState(action, context);
    }

    /**
     * Phase 3: ACT - Delivers the tutoring response to the learner.
     * 
     * <p>Actions include:
     * <ul>
     *   <li>Formatting response for delivery</li>
     *   <li>Adding accessibility features</li>
     *   <li>Triggering UI updates</li>
     *   <li>Logging analytics events</li>
     * </ul>
     */
    @Override
    @NotNull
    protected Promise<TutoringResponse> act(
            @NotNull TutoringResponse response,
            @NotNull AgentContext context) {
        
        context.getLogger().info("ACT: Delivering {} response with difficulty {}",
            response.responseType(), response.adjustedDifficulty());
        
        // Record action metrics
        context.recordMetric("tutorputor.interaction.response_type", 
            responseTypeToValue(response.responseType()));
        context.recordMetric("tutorputor.interaction.difficulty", 
            difficultyToValue(response.adjustedDifficulty()));
        
        return Promise.of(response);
    }

    /**
     * Phase 4: CAPTURE - Records the interaction and updates learner model.
     * 
     * <p>Captures:
     * <ul>
     *   <li>Interaction episode with full context</li>
     *   <li>Performance update for knowledge tracking</li>
     *   <li>Time-on-task metrics</li>
     *   <li>Mastery state changes</li>
     * </ul>
     */
    @Override
    @NotNull
    protected Promise<Void> capture(
            @NotNull LearnerAction action,
            @NotNull TutoringResponse response,
            @NotNull AgentContext context) {
        
        context.getLogger().debug("CAPTURE: Recording interaction for learner {}",
            action.learnerId());
        
        // Calculate performance score for this interaction
        double performanceScore = calculatePerformanceScore(action, response);
        
        // Build interaction episode
        Episode episode = Episode.builder()
            .agentId(getAgentId())
            .turnId(context.getTurnId())
            .timestamp(Instant.now())
            .input(buildInteractionInput(action))
            .output(buildInteractionOutput(response))
            .context(Map.of(
                "learnerId", action.learnerId(),
                "topicId", action.topicId(),
                "actionType", action.actionType().name(),
                "isCorrect", action.isCorrect() != null ? action.isCorrect() : false,
                "timeSpentMs", action.timeSpentMs() != null ? action.timeSpentMs() : 0,
                "responseType", response.responseType().name(),
                "performanceScore", performanceScore
            ))
            .tags(List.of(
                "learner-interaction",
                action.topicId(),
                action.actionType().name()
            ))
            .reward(performanceScore)
            .build();
        
        // Store episode and update learner knowledge model
        return context.getMemoryStore().storeEpisode(episode)
            .then(stored -> updateLearnerKnowledge(action, performanceScore, context));
    }

    /**
     * Phase 5: REFLECT - Analyzes learning patterns and adjusts strategy.
     * 
     * <p>Reflection includes:
     * <ul>
     *   <li>Analyzing learning velocity</li>
     *   <li>Detecting struggling patterns</li>
     *   <li>Identifying optimal content types</li>
     *   <li>Updating learner preferences</li>
     * </ul>
     */
    @Override
    @NotNull
    protected Promise<Void> reflect(
            @NotNull LearnerAction action,
            @NotNull TutoringResponse response,
            @NotNull AgentContext context) {
        
        context.getLogger().debug("REFLECT: Analyzing learning patterns for {} (async)",
            action.learnerId());
        
        // Query recent interactions for this agent
        MemoryFilter filter = MemoryFilter.builder()
            .agentId(getAgentId())
            .build();
        
        return context.getMemoryStore().queryEpisodes(filter, 20)
            .then(episodes -> {
                // Filter to this learner
                List<Episode> learnerEpisodes = episodes.stream()
                    .filter(e -> action.learnerId().equals(e.getContext().get("learnerId")))
                    .toList();
                
                if (learnerEpisodes.size() < 5) {
                    return Promise.complete();
                }
                
                // Analyze patterns
                LearningPattern pattern = analyzeLearningPattern(learnerEpisodes);
                
                context.recordMetric("tutorputor.learner.success_rate", pattern.successRate());
                context.recordMetric("tutorputor.learner.avg_time_per_item", pattern.avgTimeMs());
                
                // Update learner preferences based on observed patterns
                if (pattern.preferredContentType() != null) {
                    return updateLearnerPreference(
                        action.learnerId(),
                        "preferred_content_type",
                        pattern.preferredContentType(),
                        context
                    );
                }
                
                return Promise.complete();
            });
    }

    private void validateAction(LearnerAction action) {
        if (action.learnerId() == null || action.learnerId().isBlank()) {
            throw new IllegalArgumentException("Learner ID is required");
        }
        if (action.topicId() == null || action.topicId().isBlank()) {
            throw new IllegalArgumentException("Topic ID is required");
        }
        if (action.actionType() == null) {
            throw new IllegalArgumentException("Action type is required");
        }
    }

    private LearnerAction enrichWithLearnerState(LearnerAction action, AgentContext context) {
        // In production, query memory store for learner state
        // For now, return action with default enrichments
        return new LearnerAction(
            action.learnerId(),
            action.topicId(),
            action.actionType(),
            action.answer(),
            action.isCorrect(),
            action.timeSpentMs(),
            action.hintLevel(),
            action.attemptNumber() != null ? action.attemptNumber() : 1,
            detectEmotionalState(action),
            action.metadata()
        );
    }

    private String detectEmotionalState(LearnerAction action) {
        // Simple heuristics for emotional state detection
        if (action.timeSpentMs() != null) {
            if (action.timeSpentMs() > 120000) { // More than 2 minutes
                return "struggling";
            }
            if (action.timeSpentMs() < 5000 && action.isCorrect() != null && !action.isCorrect()) {
                return "rushed";
            }
        }
        
        if (action.hintLevel() != null && action.hintLevel() >= 3) {
            return "frustrated";
        }
        
        if (action.isCorrect() != null && action.isCorrect() && 
            action.attemptNumber() != null && action.attemptNumber() == 1) {
            return "confident";
        }
        
        return "neutral";
    }

    private double calculatePerformanceScore(LearnerAction action, TutoringResponse response) {
        double score = 0.5; // Base score
        
        // Correctness contribution
        if (action.isCorrect() != null && action.isCorrect()) {
            score += 0.3;
            
            // Bonus for first attempt
            if (action.attemptNumber() != null && action.attemptNumber() == 1) {
                score += 0.1;
            }
            
            // Bonus for no hints
            if (action.hintLevel() == null || action.hintLevel() == 0) {
                score += 0.1;
            }
        }
        
        // Time efficiency (not too fast, not too slow)
        if (action.timeSpentMs() != null) {
            long time = action.timeSpentMs();
            if (time > 10000 && time < 60000) { // 10s - 60s is ideal
                score += 0.05;
            }
        }
        
        return Math.min(1.0, Math.max(0.0, score));
    }

    private String buildInteractionInput(LearnerAction action) {
        return String.format(
            "Learner: %s, Topic: %s, Action: %s, Answer: %s",
            action.learnerId(),
            action.topicId(),
            action.actionType(),
            action.answer() != null ? action.answer() : "N/A"
        );
    }

    private String buildInteractionOutput(TutoringResponse response) {
        return String.format(
            "Response: %s, Mastery: %.2f, Next: %s",
            response.responseType(),
            response.masteryLevel(),
            response.nextAction()
        );
    }

    private Promise<Void> updateLearnerKnowledge(
            LearnerAction action,
            double performanceScore,
            AgentContext context) {
        
        // Store knowledge state as a fact (triple format)
        Fact knowledgeFact = Fact.builder()
            .agentId(getAgentId())
            .subject(action.learnerId())
            .predicate("has_knowledge_of")
            .object(action.topicId())
            .confidence(performanceScore)
            .source("interaction")
            .metadata(Map.of(
                "lastUpdated", Instant.now().toString(),
                "attemptCount", action.attemptNumber() != null ? action.attemptNumber() : 1
            ))
            .build();
        
        return context.getMemoryStore().storeFact(knowledgeFact)
            .map(f -> null);
    }

    private LearningPattern analyzeLearningPattern(List<Episode> episodes) {
        // Calculate success rate
        long correctCount = episodes.stream()
            .filter(e -> Boolean.TRUE.equals(e.getContext().get("isCorrect")))
            .count();
        double successRate = (double) correctCount / episodes.size();
        
        // Calculate average time
        double avgTime = episodes.stream()
            .filter(e -> e.getContext().get("timeSpentMs") != null)
            .mapToLong(e -> ((Number) e.getContext().get("timeSpentMs")).longValue())
            .average()
            .orElse(30000);
        
        // Find preferred content type (most successful)
        String preferredType = episodes.stream()
            .filter(e -> Boolean.TRUE.equals(e.getContext().get("isCorrect")))
            .map(e -> (String) e.getContext().get("responseType"))
            .filter(t -> t != null)
            .reduce((a, b) -> a) // Just take the first for now
            .orElse(null);
        
        return new LearningPattern(successRate, avgTime, preferredType);
    }

    private Promise<Void> updateLearnerPreference(
            String learnerId,
            String key,
            String value,
            AgentContext context) {
        
        Preference pref = Preference.builder()
            .agentId(getAgentId())
            .namespace("learner:" + learnerId)
            .key(key)
            .value(value)
            .build();
        
        return context.getMemoryStore().storePreference(pref)
            .map(p -> null);
    }

    private double responseTypeToValue(TutoringResponse.ResponseType type) {
        return switch (type) {
            case CORRECT_FEEDBACK -> 1.0;
            case INCORRECT_FEEDBACK -> 0.0;
            case HINT -> 0.5;
            case EXPLANATION -> 0.5;
            case ENCOURAGEMENT -> 0.7;
            case NEXT_QUESTION -> 0.8;
            case MASTERY_ACHIEVED -> 1.0;
            case REMEDIATION -> 0.3;
        };
    }

    private double difficultyToValue(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy" -> 0.25;
            case "medium" -> 0.5;
            case "hard" -> 0.75;
            case "expert" -> 1.0;
            default -> 0.5;
        };
    }

    private record LearningPattern(
        double successRate,
        double avgTimeMs,
        String preferredContentType
    ) {}
}
