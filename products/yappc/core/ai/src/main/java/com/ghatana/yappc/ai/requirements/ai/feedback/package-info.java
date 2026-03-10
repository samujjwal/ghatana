/**
 * User feedback collection and learning for AI improvement.
 *
 * <p>
 * <b>Purpose</b><br>
 * Collects user feedback on AI-generated requirements and suggestions to
 * continuously improve recommendation quality. Implements reinforcement
 * learning from human feedback (RLHF) patterns for adaptive AI behavior.
 *
 * <p>
 * <b>Key Components</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.feedback.SuggestionFeedback} -
 * Feedback value object</li>
 * <li>{@link com.ghatana.requirements.ai.feedback.FeedbackType} - Feedback type
 * enum</li>
 * <li>{@link com.ghatana.requirements.ai.feedback.FeedbackLearningService} -
 * Learning service</li>
 * </ul>
 *
 * <p>
 * <b>Feedback Types</b><br>
 * <ul>
 * <li>{@code POSITIVE} - User accepts suggestion (implicit or explicit)</li>
 * <li>{@code NEGATIVE} - User rejects suggestion</li>
 * <li>{@code MODIFIED} - User accepts with modifications</li>
 * <li>{@code IGNORED} - User dismisses suggestion</li>
 * </ul>
 *
 * <p>
 * <b>Basic Usage</b><br>
 * <pre>{@code
 * // Record feedback
 * SuggestionFeedback feedback = SuggestionFeedback.builder()
 *     .suggestionId("sugg-123")
 *     .userId("user-456")
 *     .feedbackType(FeedbackType.POSITIVE)
 *     .comments("Very helpful suggestion")
 *     .timestamp(Instant.now())
 *     .build();
 *
 * // Learn from feedback
 * FeedbackLearningService learningService = new FeedbackLearningService(
 *     vectorStore,
 *     embeddingService
 * );
 *
 * learningService.processFeedback(feedback)
 *     .then(updated -> {
 *         logger.info("Updated suggestion model with feedback");
 *     });
 * }</pre>
 *
 * <p>
 * <b>Learning Strategies</b><br>
 * <ul>
 * <li>Update suggestion ranking weights based on acceptance rates</li>
 * <li>Adjust embedding similarity thresholds</li>
 * <li>Refine persona-based prompt templates</li>
 * <li>Identify low-quality suggestion patterns</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * All components are thread-safe and can be safely shared across threads.
 *
 * @since 1.0.0
 * @see com.ghatana.requirements.ai.feedback.SuggestionFeedback
 * @see com.ghatana.requirements.ai.feedback.FeedbackLearningService
 * @doc.type package
 * @doc.purpose User feedback collection and learning for AI improvement
 * @doc.layer product
 * @doc.pattern Reinforcement Learning from Human Feedback (RLHF)
 */
package com.ghatana.yappc.ai.requirements.ai.feedback;
