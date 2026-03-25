/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.agent.learning.evaluation.CompositeEvaluationGate;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module providing learning-subsystem bindings.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link HumanReviewQueue} — in-memory implementation (single-node; swap
 *       with a DataCloud-backed implementation for multi-node deployments)</li>
 *   <li>{@link CompositeEvaluationGate} — default regression + safety gates</li>
 * </ul>
 *
 * <p>Note: {@link com.ghatana.aep.learning.EpisodeLearningPipeline} is wired
 * manually in {@link com.ghatana.aep.server.AepLauncher} because it requires
 * {@link com.ghatana.datacloud.DataCloudClient}, which is not part of this
 * module's DI graph.
 *
 * @doc.type class
 * @doc.purpose Learning / consolidation DI wiring
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepLearningModule extends AbstractModule {

    /**
     * Provides the {@link HumanReviewQueue}.
     * Uses the in-memory implementation (thread-safe within a single JVM).
     */
    @Provides
    HumanReviewQueue humanReviewQueue() {
        return new InMemoryHumanReviewQueue();
    }

    /**
     * Provides the default {@link CompositeEvaluationGate} (regression + safety gates).
     */
    @Provides
    CompositeEvaluationGate evaluationGate() {
        return CompositeEvaluationGate.defaultGates();
    }
}
