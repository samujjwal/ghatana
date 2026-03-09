package com.ghatana.tutorputor.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents a learner's action in the tutoring system.
 *
 * @doc.type record
 * @doc.purpose Learner action DTO
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @param learnerId unique identifier for the learner
 * @param topicId the topic being studied
 * @param actionType the type of action performed
 * @param answer the learner's answer (if applicable)
 * @param isCorrect whether the answer was correct
 * @param timeSpentMs time spent on this action in milliseconds
 * @param hintLevel current hint level (0 = no hints used)
 * @param attemptNumber which attempt this is for the same question
 * @param emotionalState detected emotional state
 * @param metadata additional action metadata
 */
public record LearnerAction(
    @NotNull String learnerId,
    @NotNull String topicId,
    @NotNull ActionType actionType,
    @Nullable String answer,
    @Nullable Boolean isCorrect,
    @Nullable Long timeSpentMs,
    @Nullable Integer hintLevel,
    @Nullable Integer attemptNumber,
    @Nullable String emotionalState,
    @Nullable Map<String, Object> metadata
) {
    /**
     * Creates a simple answer action.
     *
     * @param learnerId the learner ID
     * @param topicId the topic ID
     * @param answer the answer
     * @param isCorrect whether correct
     * @return a new action
     */
    public static LearnerAction answer(
            String learnerId, String topicId, String answer, boolean isCorrect) {
        return new LearnerAction(
            learnerId, topicId, ActionType.ANSWER_SUBMITTED,
            answer, isCorrect, null, null, 1, null, null
        );
    }

    /**
     * Creates a hint request action.
     *
     * @param learnerId the learner ID
     * @param topicId the topic ID
     * @param hintLevel the requested hint level
     * @return a new action
     */
    public static LearnerAction requestHint(String learnerId, String topicId, int hintLevel) {
        return new LearnerAction(
            learnerId, topicId, ActionType.HINT_REQUESTED,
            null, null, null, hintLevel, null, null, null
        );
    }

    /**
     * Types of learner actions.
     */
    public enum ActionType {
        /** Learner submitted an answer */
        ANSWER_SUBMITTED,
        /** Learner requested a hint */
        HINT_REQUESTED,
        /** Learner requested explanation */
        EXPLANATION_REQUESTED,
        /** Learner skipped the question */
        QUESTION_SKIPPED,
        /** Learner started a new topic */
        TOPIC_STARTED,
        /** Learner completed a topic */
        TOPIC_COMPLETED,
        /** Learner paused their session */
        SESSION_PAUSED,
        /** Learner resumed their session */
        SESSION_RESUMED,
        /** Learner reviewed previous content */
        REVIEW_REQUESTED,
        /** Learner submitted assessment */
        ASSESSMENT_SUBMITTED
    }
}
