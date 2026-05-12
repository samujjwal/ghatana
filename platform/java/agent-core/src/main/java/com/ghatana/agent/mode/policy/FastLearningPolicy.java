/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode.policy;

import com.ghatana.agent.mastery.MasteryItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Policy for fast-learning execution mode.
 *
 * <p>Enables aggressive exploration to learn new patterns:
 * <ul>
 *   <li>High exploration rate</li>
 *   <li>Lower confidence thresholds for action selection</li>
 *   <li>More aggressive hypothesis generation</li>
 *   <li>Increased episode capture</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Policy for fast-learning execution mode
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class FastLearningPolicy {

    /**
     * Returns the exploration rate for fast-learning mode.
     *
     * @return exploration rate (0.0 to 1.0)
     */
    public double getExplorationRate() {
        return 0.7; // High exploration rate
    }

    /**
     * Returns the confidence threshold for action selection.
     *
     * @return confidence threshold (0.0 to 1.0)
     */
    public double getConfidenceThreshold() {
        return 0.3; // Lower threshold for exploration
    }

    /**
     * Returns the episode capture rate (percentage of turns to capture).
     *
     * @return episode capture rate (0.0 to 1.0)
     */
    public double getEpisodeCaptureRate() {
        return 1.0; // Capture all episodes for learning
    }

    /**
     * Returns the constraints for fast-learning mode.
     *
     * @return map of constraints
     */
    @NotNull
    public Map<String, String> getConstraints() {
        return Map.of(
                "exploration_rate", "0.7",
                "confidence_threshold", "0.3",
                "episode_capture_rate", "1.0",
                "allow_exploration", "true",
                "allow_hypothesis_generation", "true",
                "allow_risk_experiments", "true"
        );
    }

    /**
     * Determines if an action should be explored based on confidence.
     *
     * @param confidence action confidence
     * @return true if action should be explored
     */
    public boolean shouldExplore(double confidence) {
        return confidence < getConfidenceThreshold();
    }
}
