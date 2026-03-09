package com.ghatana.tutorputor.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Request for content generation by the TutorPutor agent.
 *
 * @doc.type record
 * @doc.purpose Content generation request DTO
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @param topic the topic to generate content for
 * @param domain the subject domain (MATH, SCIENCE, etc.)
 * @param gradeLevel the target grade level
 * @param contentType the type of content to generate
 * @param learnerId optional learner ID for personalization
 * @param difficulty optional difficulty level (easy, medium, hard)
 * @param learnerPreferences learner preferences loaded from memory
 * @param knowledgeGaps detected knowledge gaps for remediation
 * @param additionalContext additional context for generation
 */
public record ContentGenerationRequest(
    @NotNull String topic,
    @NotNull String domain,
    @NotNull String gradeLevel,
    @NotNull ContentType contentType,
    @Nullable String learnerId,
    @Nullable String difficulty,
    @Nullable List<String> learnerPreferences,
    @Nullable List<String> knowledgeGaps,
    @Nullable Map<String, Object> additionalContext
) {
    /**
     * Creates a basic request without personalization.
     *
     * @param topic the topic
     * @param domain the domain
     * @param gradeLevel the grade level
     * @param contentType the content type
     * @return a new request
     */
    public static ContentGenerationRequest basic(
            String topic, String domain, String gradeLevel, ContentType contentType) {
        return new ContentGenerationRequest(
            topic, domain, gradeLevel, contentType,
            null, null, null, null, null
        );
    }

    /**
     * Creates a personalized request for a specific learner.
     *
     * @param topic the topic
     * @param domain the domain
     * @param gradeLevel the grade level
     * @param contentType the content type
     * @param learnerId the learner ID
     * @return a new request
     */
    public static ContentGenerationRequest forLearner(
            String topic, String domain, String gradeLevel, 
            ContentType contentType, String learnerId) {
        return new ContentGenerationRequest(
            topic, domain, gradeLevel, contentType,
            learnerId, null, null, null, null
        );
    }

    /**
     * Types of educational content.
     */
    public enum ContentType {
        /** Atomic learning statements */
        CLAIM,
        /** Concrete examples illustrating concepts */
        EXAMPLE,
        /** Interactive simulations */
        SIMULATION,
        /** Animated explanations */
        ANIMATION,
        /** Practice exercises */
        EXERCISE,
        /** Assessment questions */
        ASSESSMENT,
        /** Full lesson content */
        LESSON
    }
}
