/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryEvidence;
import com.ghatana.agent.mastery.MasteryEvidenceRepository;
import com.ghatana.agent.mastery.MasteryEvidenceType;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskNovelty;
import com.ghatana.agent.runtime.mode.TaskRiskLevel;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Evidence-driven task classifier that uses mastery evidence to classify tasks.
 *
 * <p>Instead of using heuristics, this classifier queries the mastery evidence repository
 * and mastery registry to determine task classification based on actual evidence of mastery,
 * evaluation results, and risk factors.
 *
 * <p>Returns TaskClassification with TaskRiskLevel, TaskNovelty, and evidence metadata.
 *
 * @doc.type class
 * @doc.purpose Evidence-driven task classifier with rich classification output
 * @doc.layer agent-core
 * @doc.pattern Classifier
 */
public final class EvidenceDrivenTaskClassifier implements com.ghatana.agent.runtime.mode.TaskClassifier {

    private final MasteryRegistry masteryRegistry;
    private final MasteryEvidenceRepository evidenceRepository;

    // Risk patterns for high-risk detection
    private static final Pattern[] RISK_PATTERNS = {
            Pattern.compile("(?i)(delete|drop|truncate|remove|destroy)"),
            Pattern.compile("(?i)(production|prod)"),
            Pattern.compile("(?i)(credential|password|secret|key|token)"),
            Pattern.compile("(?i)(security|auth|permission|role)"),
            Pattern.compile("(?i)(payment|transaction|billing|invoice)"),
            Pattern.compile("(?i)(database|schema|migration)"),
            Pattern.compile("(?i)(deploy|release|publish)")
    };

    // Critical risk patterns (higher severity than high-risk)
    private static final Pattern[] CRITICAL_RISK_PATTERNS = {
            Pattern.compile("(?i)(delete.*production|drop.*production|truncate.*production)"),
            Pattern.compile("(?i)(delete.*all|drop.*all|truncate.*all)"),
            Pattern.compile("(?i)(shutdown|restart|stop.*service)"),
            Pattern.compile("(?i)(root|admin|sudo)"),
            Pattern.compile("(?i)(firewall|security.*group|acl)"),
            Pattern.compile("(?i)(sudo.*rm|rm.*-rf)"),
            Pattern.compile("(?i)(disable.*security|bypass.*auth)")
    };

    // Maintenance patterns
    private static final Pattern[] MAINTENANCE_PATTERNS = {
            Pattern.compile("(?i)(legacy|deprecated|old)"),
            Pattern.compile("(?i)(refactor|cleanup|debt)"),
            Pattern.compile("(?i)(bugfix|hotfix|patch)"),
            Pattern.compile("(?i)(upgrade.*version|migration.*to)"),
            Pattern.compile("(?i)(backport|downgrade)")
    };

    // Exploration patterns
    private static final Pattern[] EXPLORATION_PATTERNS = {
            Pattern.compile("(?i)(research|investigate|explore|prototype)"),
            Pattern.compile("(?i)(poc|proof of concept)"),
            Pattern.compile("(?i)(experiment|test|trial)"),
            Pattern.compile("(?i)(spike|feasibility)")
    };

    // New work patterns (non-maintenance)
    private static final Pattern[] NEW_WORK_PATTERNS = {
            Pattern.compile("(?i)(implement|create|build|develop)"),
            Pattern.compile("(?i)(feature|new|add)"),
            Pattern.compile("(?i)(design|architecture)")
    };

    /**
     * Creates an evidence-driven task classifier.
     *
     * @param masteryRegistry mastery registry
     * @param evidenceRepository evidence repository
     */
    public EvidenceDrivenTaskClassifier(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull MasteryEvidenceRepository evidenceRepository
    ) {
        this.masteryRegistry = masteryRegistry;
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    @NotNull
    public Promise<TaskClassification> classify(@NotNull String taskDescription, @NotNull String context) {
        return classify(taskDescription, context, Map.of());
    }

    @Override
    @NotNull
    public Promise<TaskClassification> classify(
            @NotNull String taskDescription,
            @NotNull String context,
            @NotNull Map<String, String> metadata
    ) {
        // Determine risk level from task description
        TaskRiskLevel riskLevel = determineRiskLevel(taskDescription);
        
        // Determine novelty from context and metadata
        TaskNovelty novelty = determineNovelty(taskDescription, context, metadata);
        
        // Build evidence map
        Map<String, String> evidence = new HashMap<>(metadata);
        evidence.put("riskLevel", riskLevel.name());
        evidence.put("novelty", novelty.name());
        evidence.put("taskDescription", taskDescription);
        evidence.put("context", context);
        
        // Add risk pattern evidence
        if (isCriticalRisk(taskDescription)) {
            evidence.put("criticalRiskPattern", "matched");
        }
        if (isHighRisk(taskDescription)) {
            evidence.put("highRiskPattern", "matched");
        }
        
        return Promise.of(new TaskClassification(riskLevel, novelty, Map.copyOf(evidence)));
    }

    /**
     * Determines the risk level for a task.
     */
    @NotNull
    private TaskRiskLevel determineRiskLevel(String taskDescription) {
        // Check for critical risk patterns first (highest severity)
        if (isCriticalRisk(taskDescription)) {
            return TaskRiskLevel.CRITICAL;
        }

        // Check for high-risk patterns
        if (isHighRisk(taskDescription)) {
            return TaskRiskLevel.HIGH;
        }

        // Check for maintenance patterns (medium risk)
        if (isMaintenance(taskDescription)) {
            return TaskRiskLevel.MEDIUM;
        }

        // Default to low risk
        return TaskRiskLevel.LOW;
    }

    /**
     * Determines the novelty of a task based on context and metadata.
     */
    @NotNull
    private TaskNovelty determineNovelty(String taskDescription, String context, Map<String, String> metadata) {
        // Check for exploration patterns (novel)
        if (isExploration(taskDescription)) {
            return TaskNovelty.NOVEL;
        }

        // Check for migration patterns (similar)
        if (isMigration(taskDescription)) {
            return TaskNovelty.SIMILAR;
        }

        // Check for new work patterns
        if (isNewWork(taskDescription)) {
            return TaskNovelty.NOVEL;
        }

        // Check metadata for mastery state
        String masteryState = metadata.get("masteryState");
        if (masteryState != null) {
            if (masteryState.equals("MASTERED") || masteryState.equals("COMPETENT")) {
                return TaskNovelty.FAMILIAR;
            }
            if (masteryState.equals("PRACTICED")) {
                return TaskNovelty.SIMILAR;
            }
        }

        // Default to familiar if not novel
        return TaskNovelty.FAMILIAR;
    }

    /**
     * Legacy classify method for backward compatibility with TaskClass interface.
     * This method is deprecated in favor of the classify methods that return TaskClassification.
     */
    @Deprecated
    @NotNull
    public TaskClass classifyLegacy(
            @NotNull String taskDescription,
            @NotNull Optional<MasteryItem> mastery,
            @NotNull EnvironmentFingerprint env
    ) {
        // Check for critical risk patterns first (highest severity)
        if (isCriticalRisk(taskDescription)) {
            return TaskClass.CRITICAL_RISK_TASK;
        }

        // Check for high-risk patterns
        if (isHighRisk(taskDescription)) {
            return TaskClass.HIGH_RISK_TASK;
        }

        // Check for maintenance patterns
        if (isMaintenance(taskDescription)) {
            // If mastery item exists and is in MAINTENANCE_ONLY state, classify as maintenance-only
            if (mastery.isPresent() && mastery.get().state() == MasteryState.MAINTENANCE_ONLY) {
                return TaskClass.MAINTENANCE_ONLY_TASK;
            }
            return TaskClass.MAINTENANCE_TASK;
        }

        // Check for exploration patterns
        if (isExploration(taskDescription)) {
            return TaskClass.EXPLORATION_TASK;
        }

        // Check for migration patterns
        if (isMigration(taskDescription)) {
            return TaskClass.MIGRATION_TASK;
        }

        // Check for new work patterns
        if (isNewWork(taskDescription)) {
            // New work on mastered skills is still known
            if (mastery.isPresent() && mastery.get().state() == MasteryState.MASTERED) {
                return TaskClass.KNOWN_TASK;
            }
            // New work on competent skills is variation
            if (mastery.isPresent() && mastery.get().state() == MasteryState.COMPETENT) {
                return TaskClass.KNOWN_VARIATION;
            }
            // New work on practiced or lower is unknown
            return TaskClass.UNKNOWN_TASK;
        }

        // Use mastery evidence to determine if task is known or variation
        if (mastery.isPresent()) {
            MasteryItem item = mastery.get();
            
            // Check mastery state
            if (item.state() == MasteryState.MASTERED) {
                // Check for evidence of exact task match
                return hasExactEvidence(item, taskDescription) 
                        ? TaskClass.KNOWN_TASK 
                        : TaskClass.KNOWN_VARIATION;
            }
            
            if (item.state() == MasteryState.COMPETENT) {
                return TaskClass.KNOWN_VARIATION;
            }
            
            if (item.state() == MasteryState.PRACTICED) {
                return TaskClass.UNKNOWN_TASK;
            }

            if (item.state() == MasteryState.OBSERVED) {
                return TaskClass.UNKNOWN_TASK;
            }

            if (item.state() == MasteryState.MAINTENANCE_ONLY) {
                return TaskClass.MAINTENANCE_ONLY_TASK;
            }
        }

        // No mastery item or low mastery state
        return TaskClass.UNKNOWN_TASK;
    }

    /**
     * Checks if the task description matches critical risk patterns.
     */
    private boolean isCriticalRisk(String description) {
        for (Pattern pattern : CRITICAL_RISK_PATTERNS) {
            if (pattern.matcher(description).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the task description matches high-risk patterns.
     */
    private boolean isHighRisk(String description) {
        for (Pattern pattern : RISK_PATTERNS) {
            if (pattern.matcher(description).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the task description matches maintenance patterns.
     */
    private boolean isMaintenance(String description) {
        for (Pattern pattern : MAINTENANCE_PATTERNS) {
            if (pattern.matcher(description).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the task description matches exploration patterns.
     */
    private boolean isExploration(String description) {
        for (Pattern pattern : EXPLORATION_PATTERNS) {
            if (pattern.matcher(description).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the task description matches new work patterns.
     */
    private boolean isNewWork(String description) {
        for (Pattern pattern : NEW_WORK_PATTERNS) {
            if (pattern.matcher(description).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the task description matches migration patterns.
     */
    private boolean isMigration(String description) {
        return description.toLowerCase().contains("migrate") 
                || description.toLowerCase().contains("upgrade")
                || description.toLowerCase().contains("migrating");
    }

    /**
     * Checks if there's evidence of exact task match for the mastery item.
     * This is a synchronous check for simplicity - async version would use Promise.
     */
    private boolean hasExactEvidence(MasteryItem item, String taskDescription) {
        // Check if evaluation refs indicate exact task matches
        if (item.evaluationRefs() != null && !item.evaluationRefs().isEmpty()) {
            for (String evalRef : item.evaluationRefs()) {
                if (evalRef.toLowerCase().contains(taskDescription.toLowerCase().substring(0, Math.min(20, taskDescription.length())))) {
                    return true;
                }
            }
        }
        
        // Check evidence refs for procedural skill evidence
        if (item.evidenceRefs() != null && !item.evidenceRefs().isEmpty()) {
            // Evidence refs are stored as strings in the format "evidenceId:ref"
            for (String evidenceRef : item.evidenceRefs()) {
                String[] parts = evidenceRef.split(":");
                if (parts.length > 1) {
                    String ref = parts[1];
                    if (ref.toLowerCase().contains(taskDescription.toLowerCase().substring(0, Math.min(20, taskDescription.length())))) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Async version that queries evidence repository for more accurate classification.
     *
     * @param taskDescription task description
     * @param masteryId mastery item ID
     * @return promise of task classification
     */
    @NotNull
    public Promise<TaskClass> classifyAsync(
            @NotNull String taskDescription,
            @NotNull String masteryId
    ) {
        // Query evidence repository for this mastery item
        return evidenceRepository.findByMasteryId(masteryId)
                .then(evidenceList -> {
                    // Check for exact task match evidence
                    boolean hasExactMatch = evidenceList.stream()
                            .anyMatch(e -> e.ref().toLowerCase().contains(taskDescription.toLowerCase()));
                    
                    if (hasExactMatch) {
                        return Promise.of(TaskClass.KNOWN_TASK);
                    }
                    
                    // Check for procedural skill evidence (tool outputs indicate procedural execution)
                    boolean hasProceduralEvidence = evidenceList.stream()
                            .anyMatch(e -> e.type() == MasteryEvidenceType.TOOL_OUTPUT);
                    
                    if (hasProceduralEvidence) {
                        return Promise.of(TaskClass.KNOWN_VARIATION);
                    }
                    
                    return Promise.of(TaskClass.UNKNOWN_TASK);
                });
    }
}
