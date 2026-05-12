/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Classifies tasks based on mastery, version context, and risk factors.
 *
 * @doc.type interface
 * @doc.purpose Task classifier for mode selection
 * @doc.layer agent-core
 * @doc.pattern Classifier
 */
public interface TaskClassifier {

    /**
     * Classifies a task based on available mastery and environment context.
     *
     * @param taskDescription description of the task
     * @param mastery optional mastery item for the skill
     * @param env environment fingerprint
     * @return task classification
     */
    @NotNull
    TaskClass classify(
            @NotNull String taskDescription,
            @NotNull Optional<MasteryItem> mastery,
            @NotNull EnvironmentFingerprint env
    );
}
