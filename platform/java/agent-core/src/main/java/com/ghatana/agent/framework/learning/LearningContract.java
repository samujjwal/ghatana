/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning;

import org.jetbrains.annotations.NotNull;

/**
 * Learning contract defining the learning behavior for an agent.
 *
 * @doc.type class
 * @doc.purpose Learning contract for agent learning behavior
 * @doc.layer agent-core
 * @doc.pattern Contract
 */
public class LearningContract {
    private final Level level;
    private final int maxEpisodes;
    private final double confidenceThreshold;

    public LearningContract(@NotNull Level level, int maxEpisodes, double confidenceThreshold) {
        this.level = level;
        this.maxEpisodes = maxEpisodes;
        this.confidenceThreshold = confidenceThreshold;
    }

    public Level level() {
        return level;
    }

    public int maxEpisodes() {
        return maxEpisodes;
    }

    public double confidenceThreshold() {
        return confidenceThreshold;
    }

    public enum Level {
        NONE,
        BASIC,
        INTERMEDIATE,
        ADVANCED,
        EXPLORATORY
    }
}
