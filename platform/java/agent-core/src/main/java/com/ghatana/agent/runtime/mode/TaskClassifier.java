/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Classifier for determining task risk and novelty.
 *
 * @doc.type interface
 * @doc.purpose Classifier for task risk and novelty
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface TaskClassifier {

    /**
     * Classifies a task based on its description and context.
     *
     * @param taskDescription task description
     * @param context additional context for classification
     * @return promise of task classification
     */
    @NotNull
    Promise<TaskClassification> classify(@NotNull String taskDescription, @NotNull String context);

    /**
     * Classifies a task with additional metadata.
     *
     * @param taskDescription task description
     * @param context additional context for classification
     * @param metadata additional metadata
     * @return promise of task classification
     */
    @NotNull
    Promise<TaskClassification> classify(
            @NotNull String taskDescription,
            @NotNull String context,
            @NotNull java.util.Map<String, String> metadata
    );
}
