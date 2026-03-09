package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * OutputGenerator for tutoring responses using adaptive algorithms.
 * 
 * <p>This generator:
 * <ul>
 *   <li>Analyzes learner's current state and history</li>
 *   <li>Applies pedagogical rules for feedback</li>
 *   <li>Uses LLM for personalized explanations</li>
 *   <li>Implements spaced repetition for mastery</li>
 *   <li>Adapts difficulty based on performance</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Adaptive tutoring response generation
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class TutoringResponseGenerator 
        implements OutputGenerator<LearnerAction, TutoringResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(TutoringResponseGenerator.class);
    
    private static final double MASTERY_THRESHOLD = 0.85;
    private static final double STRUGGLING_THRESHOLD = 0.4;
    private static final int CONSECUTIVE_CORRECT_FOR_MASTERY = 3;
    
    private final LLMGateway llmGateway;
    private final Executor executor;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter responsesCounter;
    private final Counter llmCallsCounter;
    private final Timer responseTimer;
    
    private static final GeneratorMetadata METADATA = GeneratorMetadata.builder()
        .name("TutoringResponseGenerator")
        .type("hybrid")
        .description("Adaptive tutoring with rule-based and LLM components")
        .version("1.0.0")
        .build();

    /**
     * Creates a new TutoringResponseGenerator.
     *
     * @param llmGateway the LLM gateway for explanations
     * @param executor the executor for async operations
     * @param meterRegistry the metrics registry
     */
    public TutoringResponseGenerator(
            @NotNull LLMGateway llmGateway,
            @NotNull Executor executor,
            @NotNull MeterRegistry meterRegistry) {
        this.llmGateway = llmGateway;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        
        this.responsesCounter = Counter.builder("tutorputor.tutor.responses")
            .description("Number of tutoring responses generated")
            .register(meterRegistry);
        this.llmCallsCounter = Counter.builder("tutorputor.tutor.llm_calls")
            .description("Number of LLM calls for explanations")
            .register(meterRegistry);
        this.responseTimer = Timer.builder("tutorputor.tutor.latency")
            .description("Tutoring response latency")
            .register(meterRegistry);
    }

    @Override
    @NotNull
    public Promise<TutoringResponse> generate(
            @NotNull LearnerAction action,
            @NotNull AgentContext context) {
        
        Instant start = Instant.now();
        responsesCounter.increment();
        
        context.getLogger().info("Generating tutoring response for {} action by learner {}",
            action.actionType(), action.learnerId());
        
        // Route to appropriate handler based on action type
        return (switch (action.actionType()) {
            case ANSWER_SUBMITTED -> handleAnswerSubmission(action, context);
            case HINT_REQUESTED -> handleHintRequest(action, context);
            case EXPLANATION_REQUESTED -> handleExplanationRequest(action, context);
            case QUESTION_SKIPPED -> handleSkip(action, context);
            case TOPIC_STARTED -> handleTopicStart(action, context);
            case TOPIC_COMPLETED -> handleTopicCompletion(action, context);
            default -> handleDefault(action, context);
        }).whenComplete((response, error) -> {
            responseTimer.record(Duration.between(start, Instant.now()));
        });
    }

    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return METADATA;
    }

    private Promise<TutoringResponse> handleAnswerSubmission(
            LearnerAction action, AgentContext context) {
        
        boolean correct = action.isCorrect() != null && action.isCorrect();
        
        // Get learner's mastery level for this topic
        return getLearnerMastery(action.learnerId(), action.topicId(), context)
            .map(mastery -> {
                String adjustedDifficulty = calculateAdjustedDifficulty(mastery, correct);
                
                if (correct) {
                    return buildCorrectResponse(action, mastery, adjustedDifficulty);
                } else {
                    return buildIncorrectResponse(action, mastery, adjustedDifficulty);
                }
            });
    }

    private Promise<TutoringResponse> handleHintRequest(
            LearnerAction action, AgentContext context) {
        
        int hintLevel = action.hintLevel() != null ? action.hintLevel() : 1;
        
        // Generate hint using LLM
        return generateHint(action.topicId(), hintLevel, context)
            .map(hintText -> TutoringResponse.builder()
                .responseType(TutoringResponse.ResponseType.HINT)
                .message("Here's a hint to help you:")
                .hintText(hintText)
                .nextAction(TutoringResponse.NextAction.RETRY)
                .masteryLevel(0.0) // Will be updated by capture phase
                .adjustedDifficulty("medium")
                .build());
    }

    private Promise<TutoringResponse> handleExplanationRequest(
            LearnerAction action, AgentContext context) {
        
        llmCallsCounter.increment();
        
        // Generate detailed explanation using LLM
        return generateExplanation(action.topicId(), action.answer(), context)
            .map(explanation -> TutoringResponse.builder()
                .responseType(TutoringResponse.ResponseType.EXPLANATION)
                .message("Let me explain this concept:")
                .explanationText(explanation)
                .nextAction(TutoringResponse.NextAction.RETRY)
                .masteryLevel(0.0)
                .adjustedDifficulty("medium")
                .build());
    }

    private Promise<TutoringResponse> handleSkip(
            LearnerAction action, AgentContext context) {
        
        return Promise.of(TutoringResponse.builder()
            .responseType(TutoringResponse.ResponseType.ENCOURAGEMENT)
            .message("No problem! Let's try something different.")
            .nextAction(TutoringResponse.NextAction.NEXT_QUESTION)
            .masteryLevel(0.0)
            .adjustedDifficulty("easy") // Lower difficulty after skip
            .encouragementMessage("It's okay to skip - everyone learns at their own pace!")
            .build());
    }

    private Promise<TutoringResponse> handleTopicStart(
            LearnerAction action, AgentContext context) {
        
        return getLearnerMastery(action.learnerId(), action.topicId(), context)
            .map(mastery -> TutoringResponse.builder()
                .responseType(TutoringResponse.ResponseType.NEXT_QUESTION)
                .message("Let's begin learning about " + action.topicId() + "!")
                .nextAction(TutoringResponse.NextAction.CONTINUE)
                .masteryLevel(mastery)
                .adjustedDifficulty(mastery > 0.5 ? "medium" : "easy")
                .encouragementMessage("You've got this! Let's learn something new.")
                .build());
    }

    private Promise<TutoringResponse> handleTopicCompletion(
            LearnerAction action, AgentContext context) {
        
        return getLearnerMastery(action.learnerId(), action.topicId(), context)
            .map(mastery -> {
                boolean achieved = mastery >= MASTERY_THRESHOLD;
                
                return TutoringResponse.builder()
                    .responseType(achieved 
                        ? TutoringResponse.ResponseType.MASTERY_ACHIEVED 
                        : TutoringResponse.ResponseType.ENCOURAGEMENT)
                    .message(achieved 
                        ? "Congratulations! You've mastered this topic!" 
                        : "Great effort! Keep practicing to achieve mastery.")
                    .nextAction(achieved 
                        ? TutoringResponse.NextAction.NEXT_TOPIC 
                        : TutoringResponse.NextAction.CONTINUE)
                    .masteryLevel(mastery)
                    .adjustedDifficulty("medium")
                    .encouragementMessage(achieved 
                        ? "🎉 Amazing work! You're a star!" 
                        : "You're making progress! Keep going!")
                    .build();
            });
    }

    private Promise<TutoringResponse> handleDefault(
            LearnerAction action, AgentContext context) {
        
        return Promise.of(TutoringResponse.builder()
            .responseType(TutoringResponse.ResponseType.NEXT_QUESTION)
            .message("Ready for the next challenge?")
            .nextAction(TutoringResponse.NextAction.CONTINUE)
            .masteryLevel(0.5)
            .adjustedDifficulty("medium")
            .build());
    }

    private Promise<Double> getLearnerMastery(
            String learnerId, String topicId, AgentContext context) {
        
        // Query facts for learner's knowledge of this topic
        // Using subject-predicate-object pattern
        return context.getMemoryStore().queryFacts(learnerId, "has_knowledge_of", topicId)
            .map(facts -> {
                // Get the most confident fact
                return facts.stream()
                    .mapToDouble(Fact::getConfidence)
                    .max()
                    .orElse(0.0);
            });
    }

    private String calculateAdjustedDifficulty(double mastery, boolean correct) {
        if (mastery >= MASTERY_THRESHOLD) {
            return "hard";
        }
        
        if (mastery >= 0.6) {
            return correct ? "hard" : "medium";
        }
        
        if (mastery >= 0.3) {
            return correct ? "medium" : "easy";
        }
        
        return "easy";
    }

    private TutoringResponse buildCorrectResponse(
            LearnerAction action, double mastery, String difficulty) {
        
        double newMastery = Math.min(1.0, mastery + 0.1);
        boolean masteryAchieved = newMastery >= MASTERY_THRESHOLD;
        
        TutoringResponse.ResponseType type = masteryAchieved 
            ? TutoringResponse.ResponseType.MASTERY_ACHIEVED 
            : TutoringResponse.ResponseType.CORRECT_FEEDBACK;
        
        TutoringResponse.NextAction nextAction = masteryAchieved 
            ? TutoringResponse.NextAction.NEXT_TOPIC 
            : TutoringResponse.NextAction.NEXT_QUESTION;
        
        String message = selectCorrectFeedback(action.attemptNumber());
        String encouragement = selectEncouragement(newMastery);
        
        return TutoringResponse.builder()
            .responseType(type)
            .message(message)
            .feedbackText("That's correct!")
            .nextAction(nextAction)
            .masteryLevel(newMastery)
            .adjustedDifficulty(difficulty)
            .encouragementMessage(encouragement)
            .build();
    }

    private TutoringResponse buildIncorrectResponse(
            LearnerAction action, double mastery, String difficulty) {
        
        double newMastery = Math.max(0.0, mastery - 0.05);
        int attempts = action.attemptNumber() != null ? action.attemptNumber() : 1;
        
        TutoringResponse.NextAction nextAction;
        TutoringResponse.ResponseType type;
        String message;
        
        if (attempts >= 3) {
            // After 3 attempts, provide remediation
            type = TutoringResponse.ResponseType.REMEDIATION;
            nextAction = TutoringResponse.NextAction.REVIEW_PREREQUISITE;
            message = "Let's review the concept together before trying again.";
        } else if (attempts == 2) {
            // Offer a hint on second attempt
            type = TutoringResponse.ResponseType.INCORRECT_FEEDBACK;
            nextAction = TutoringResponse.NextAction.RETRY;
            message = "Not quite. Would you like a hint?";
        } else {
            type = TutoringResponse.ResponseType.INCORRECT_FEEDBACK;
            nextAction = TutoringResponse.NextAction.RETRY;
            message = "That's not correct. Try again!";
        }
        
        return TutoringResponse.builder()
            .responseType(type)
            .message(message)
            .feedbackText("The answer was incorrect.")
            .nextAction(nextAction)
            .masteryLevel(newMastery)
            .adjustedDifficulty(difficulty)
            .encouragementMessage("Don't worry, mistakes help us learn!")
            .build();
    }

    private Promise<String> generateHint(String topicId, int hintLevel, AgentContext context) {
        String prompt = String.format("""
            Generate a hint (level %d of 3) for a student learning about: %s
            
            Level 1: A gentle nudge in the right direction
            Level 2: More specific guidance
            Level 3: Almost giving the answer away
            
            Keep the hint age-appropriate and encouraging.
            """, hintLevel, topicId);
        
        CompletionRequest request = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(150)
            .temperature(0.5)
            .build();

        return llmGateway.complete(request).map(result -> result.getText());
    }

    private Promise<String> generateExplanation(
            String topicId, String answer, AgentContext context) {
        
        String prompt = String.format("""
            Provide a clear, educational explanation for the topic: %s
            
            The student answered: %s
            
            Create an explanation that:
            1. Is easy to understand
            2. Uses relatable examples
            3. Builds on prior knowledge
            4. Is encouraging and supportive
            
            Keep it concise but thorough.
            """, topicId, answer != null ? answer : "no answer provided");
        
        CompletionRequest request = CompletionRequest.builder()
            .prompt(prompt)
            .maxTokens(300)
            .temperature(0.6)
            .build();

        return llmGateway.complete(request).map(result -> result.getText());
    }

    private String selectCorrectFeedback(Integer attemptNumber) {
        if (attemptNumber == null || attemptNumber == 1) {
            return "Excellent! You got it on the first try!";
        } else if (attemptNumber == 2) {
            return "Great job! You figured it out!";
        } else {
            return "You did it! Persistence pays off!";
        }
    }

    private String selectEncouragement(double mastery) {
        if (mastery >= 0.9) {
            return "🌟 You're doing amazing! Almost at mastery!";
        } else if (mastery >= 0.7) {
            return "💪 Great progress! Keep up the excellent work!";
        } else if (mastery >= 0.5) {
            return "👍 You're getting there! Keep practicing!";
        } else {
            return "🎯 Every step forward counts! You can do this!";
        }
    }
}
