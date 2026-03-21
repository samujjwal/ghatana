/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module providing learning-subsystem bindings.
 *
 * <p>Wires the {@link com.ghatana.agent.learning.consolidation.ConsolidationScheduler},
 * {@link com.ghatana.agent.learning.consolidation.ConsolidationPipeline}, and related
 * stages into the DI graph.
 *
 * @doc.type class
 * @doc.purpose Learning / consolidation DI wiring
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepLearningModule extends AbstractModule {
    // Placeholder — @Provides methods for ConsolidationScheduler,
    // ConsolidationPipeline, and ConflictResolver will be added once
    // the learning loop is wired end-to-end.
}
