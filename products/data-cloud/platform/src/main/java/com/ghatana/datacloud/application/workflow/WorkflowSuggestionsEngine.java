package com.ghatana.datacloud.application.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Workflow suggestions engine for AI-powered assistance.
 *
 * <p><b>Purpose</b><br>
 * Generates intelligent workflow suggestions based on:
 * - User patterns and history
 * - Workflow templates
 * - Best practices
 * - Performance metrics
 * - User preferences
 *
 * <p><b>Features</b><br>
 * - Pattern analysis
 * - Template recommendations
 * - Best practice suggestions
 * - ML-ready architecture
 * - Feedback collection
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowSuggestionsEngine engine = new WorkflowSuggestionsEngine();
 *
 * // Get suggestions
 * Promise<List<WorkflowSuggestion>> suggestions =
 *   engine.getSuggestions(userId, context);
 *
 * // Record feedback
 * engine.recordFeedback(suggestionId, helpful);
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Workflow suggestions engine
 * @doc.layer application
 * @doc.pattern Recommender
 */
public class WorkflowSuggestionsEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowSuggestionsEngine.class);

    private final Map<String, UserWorkflowPattern> userPatterns = new HashMap<>();
    private final WorkflowTemplateLibrary templates = new WorkflowTemplateLibrary();
    private final FeedbackCollector feedback = new FeedbackCollector();
    private final BestPracticesLibrary bestPractices = new BestPracticesLibrary();

    /**
     * Initialize engine with templates.
     */
    public WorkflowSuggestionsEngine() {
        initializeTemplates();
        initializeBestPractices();
    }

    /**
     * Get workflow suggestions for user.
     *
     * @param userId the user ID
     * @param context the workflow context
     * @return list of suggestions
     */
    public List<WorkflowSuggestion> getSuggestions(String userId, WorkflowContext context) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        logger.info("Generating suggestions for user: {}", userId);

        List<WorkflowSuggestion> suggestions = new ArrayList<>();

        // Get pattern-based suggestions
        suggestions.addAll(getPatternBasedSuggestions(userId, context));

        // Get template-based suggestions
        suggestions.addAll(getTemplateSuggestions(context));

        // Get best practice suggestions
        suggestions.addAll(getBestPracticeSuggestions(context));

        // Sort by score and limit
        suggestions.sort(Comparator.comparingDouble(WorkflowSuggestion::score).reversed());

        List<WorkflowSuggestion> topSuggestions = suggestions.stream()
                .limit(5)
                .collect(Collectors.toList());

        logger.info("Generated {} suggestions for user {}", topSuggestions.size(), userId);
        return topSuggestions;
    }

    /**
     * Get pattern-based suggestions.
     *
     * @param userId the user ID
     * @param context the workflow context
     * @return list of suggestions
     */
    private List<WorkflowSuggestion> getPatternBasedSuggestions(String userId, WorkflowContext context) {
        List<WorkflowSuggestion> suggestions = new ArrayList<>();

        UserWorkflowPattern pattern = userPatterns.get(userId);
        if (pattern == null) {
            return suggestions;
        }

        // Suggest common workflows used by user
        pattern.getCommonWorkflows().forEach(workflow -> {
            // Calculate confidence score based on frequency (0-1 normalized)
            double frequencyScore = Math.min(1.0, workflow.frequency() / 10.0);
            suggestions.add(new WorkflowSuggestion(
                    UUID.randomUUID().toString(),
                    "Similar to your common workflow: " + workflow.name(),
                    frequencyScore * 0.9,
                    "PATTERN",
                    workflow.name()
            ));
        });

        return suggestions;
    }

    /**
     * Get template-based suggestions.
     *
     * @param context the workflow context
     * @return list of suggestions
     */
    private List<WorkflowSuggestion> getTemplateSuggestions(WorkflowContext context) {
        return templates.findMatching(context).stream()
                .map(t -> new WorkflowSuggestion(
                        UUID.randomUUID().toString(),
                        "Use template: " + t.name,
                        0.8,
                        "TEMPLATE",
                        t.id
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get best practice suggestions.
     *
     * @param context the workflow context
     * @return list of suggestions
     */
    private List<WorkflowSuggestion> getBestPracticeSuggestions(WorkflowContext context) {
        return bestPractices.findMatching(context).stream()
                .map(bp -> new WorkflowSuggestion(
                        UUID.randomUUID().toString(),
                        "Best practice: " + bp.description,
                        0.75,
                        "BEST_PRACTICE",
                        bp.id
                ))
                .collect(Collectors.toList());
    }

    /**
     * Record user workflow for learning.
     *
     * @param userId the user ID
     * @param workflow the workflow
     */
    public void recordWorkflow(String userId, Workflow workflow) {
        UserWorkflowPattern pattern = userPatterns.computeIfAbsent(
                userId,
                k -> new UserWorkflowPattern(userId)
        );

        pattern.recordWorkflow(workflow);
        logger.debug("Recorded workflow for user: {}", userId);
    }

    /**
     * Record feedback on suggestion.
     *
     * @param suggestionId the suggestion ID
     * @param helpful true if helpful
     */
    public void recordFeedback(String suggestionId, boolean helpful) {
        feedback.recordFeedback(suggestionId, helpful);
        logger.debug("Recorded feedback for suggestion: {} (helpful: {})", suggestionId, helpful);
    }

    /**
     * Initialize workflow templates.
     */
    private void initializeTemplates() {
        templates.add(new WorkflowTemplate(
                "data-pipeline",
                "Data Pipeline",
                "Load, transform, validate data",
                "data"
        ));

        templates.add(new WorkflowTemplate(
                "notification-flow",
                "Notification Flow",
                "Send notifications based on events",
                "notifications"
        ));

        templates.add(new WorkflowTemplate(
                "approval-workflow",
                "Approval Workflow",
                "Request and approval process",
                "approval"
        ));
    }

    /**
     * Initialize best practices.
     */
    private void initializeBestPractices() {
        bestPractices.add(new BestPractice(
                "bp-1",
                "Always validate inputs",
                "Add validation nodes at the beginning of workflows"
        ));

        bestPractices.add(new BestPractice(
                "bp-2",
                "Use error handlers",
                "Add error handling nodes to catch and handle failures"
        ));

        bestPractices.add(new BestPractice(
                "bp-3",
                "Monitor performance",
                "Add timing and logging nodes for debugging"
        ));
    }

    /**
     * User workflow pattern.
     */
    private static class UserWorkflowPattern {
        private final String userId;
        private final Map<String, WorkflowFrequency> workflows = new HashMap<>();
        private static final int MAX_HISTORY = 100;

        UserWorkflowPattern(String userId) {
            this.userId = userId;
        }

        void recordWorkflow(Workflow workflow) {
            workflows.merge(workflow.name,
                    new WorkflowFrequency(workflow.name, 1, workflow.avgExecutionTime),
                    (old, n) -> new WorkflowFrequency(
                            workflow.name,
                            old.frequency + 1,
                            (old.avgExecutionTime + workflow.avgExecutionTime) / 2
                    )
            );
        }

        List<WorkflowFrequency> getCommonWorkflows() {
            return workflows.values().stream()
                    .sorted(Comparator.comparingInt(WorkflowFrequency::frequency).reversed())
                    .limit(5)
                    .collect(Collectors.toList());
        }

        record WorkflowFrequency(String name, int frequency, double avgExecutionTime) {
            double score() {
                return frequency * 0.5 + (1000.0 / Math.max(avgExecutionTime, 1)) * 0.5;
            }
        }
    }

    /**
     * Workflow context for suggestions.
     */
    public record WorkflowContext(
            String type,
            List<String> nodes,
            List<String> triggers,
            String userTier
    ) {}

    /**
     * Workflow suggestion.
     */
    public record WorkflowSuggestion(
            String id,
            String description,
            double score,
            String type,
            String reference
    ) {}

    /**
     * Workflow template.
     */
    public record WorkflowTemplate(
            String id,
            String name,
            String description,
            String category
    ) {}

    /**
     * Best practice.
     */
    public record BestPractice(
            String id,
            String title,
            String description
    ) {}

    /**
     * Workflow for recording.
     */
    public record Workflow(
            String name,
            int nodeCount,
            double avgExecutionTime,
            String status
    ) {}

    /**
     * Template library.
     */
    private static class WorkflowTemplateLibrary {
        private final List<WorkflowTemplate> templates = new ArrayList<>();

        void add(WorkflowTemplate template) {
            templates.add(template);
        }

        List<WorkflowTemplate> findMatching(WorkflowContext context) {
            return templates.stream()
                    .filter(t -> matchesContext(t, context))
                    .collect(Collectors.toList());
        }

        private boolean matchesContext(WorkflowTemplate template, WorkflowContext context) {
            return context.type != null && context.type.toLowerCase()
                    .contains(template.category.toLowerCase());
        }
    }

    /**
     * Best practices library.
     */
    private static class BestPracticesLibrary {
        private final List<BestPractice> practices = new ArrayList<>();

        void add(BestPractice practice) {
            practices.add(practice);
        }

        List<BestPractice> findMatching(WorkflowContext context) {
            return practices; // Return all for now
        }
    }

    /**
     * Feedback collector.
     */
    private static class FeedbackCollector {
        private final Map<String, List<Boolean>> feedback = new HashMap<>();

        void recordFeedback(String suggestionId, boolean helpful) {
            feedback.computeIfAbsent(suggestionId, k -> new ArrayList<>())
                    .add(helpful);
        }

        double getHelpfulnessRatio(String suggestionId) {
            List<Boolean> feedbackList = feedback.get(suggestionId);
            if (feedbackList == null || feedbackList.isEmpty()) {
                return 0.5;
            }
            long helpful = feedbackList.stream().filter(b -> b).count();
            return (double) helpful / feedbackList.size();
        }
    }
}

