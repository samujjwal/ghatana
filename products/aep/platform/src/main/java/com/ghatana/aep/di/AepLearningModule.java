/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.config.EnvConfig;
import com.ghatana.agent.learning.consolidation.ConflictResolver;
import com.ghatana.agent.learning.consolidation.ConsolidationPipeline;
import com.ghatana.agent.learning.consolidation.ConsolidationScheduler;
import com.ghatana.agent.learning.consolidation.ConsolidationStage;
import com.ghatana.agent.learning.consolidation.EntrenchmentConflictResolver;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * ActiveJ DI module for AEP learning-loop components.
 *
 * <p>Wires the GAA learning plane into AEP:
 * <ul>
 *   <li>{@link HumanReviewQueue} → {@link InMemoryHumanReviewQueue} with a
 *       silent {@link ReviewNotificationSpi#NOOP} notification sink. Production
 *       deployments should replace this with a persistent, notification-capable
 *       implementation.</li>
 *   <li>{@link ConflictResolver} → {@link EntrenchmentConflictResolver} —
 *       entrenchment-based conflict resolution for cross-tier memory conflicts.</li>
 *   <li>{@link ConsolidationPipeline} — pluggable pipeline; wired with a
 *       no-op passthrough stage until Track 0D ({@code PersistentMemoryPlane})
 *       supplies the real episodic/semantic/procedural stores.</li>
 *   <li>{@link ConsolidationScheduler} — kicks off periodic consolidation runs
 *       against the {@code AEP_SYSTEM} virtual agent at the interval configured
 *       by {@code AEP_CONSOLIDATION_INTERVAL_HOURS} (default: 6 h).</li>
 * </ul>
 *
 * <h2>Required Modules</h2>
 * <ul>
 *   <li>{@link AepCoreModule} — for the shared {@link ScheduledExecutorService}</li>
 * </ul>
 *
 * <h2>Environment Variables</h2>
 * <table border="1">
 *   <tr><th>Variable</th><th>Default</th><th>Description</th></tr>
 *   <tr>
 *     <td>AEP_CONSOLIDATION_INTERVAL_HOURS</td>
 *     <td>6</td>
 *     <td>How often the consolidation scheduler runs (in hours)</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepObservabilityModule(),
 *     new AepLearningModule()
 * );
 * HumanReviewQueue queue = injector.getInstance(HumanReviewQueue.class);
 * ConsolidationScheduler scheduler = injector.getInstance(ConsolidationScheduler.class);
 * scheduler.start();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for AEP learning-loop (HumanReviewQueue, ConsolidationPipeline, ConsolidationScheduler)
 * @doc.layer product
 * @doc.pattern Module
 * @doc.gaa.lifecycle reflect
 * @see HumanReviewQueue
 * @see ConsolidationScheduler
 * @see ConsolidationPipeline
 */
public class AepLearningModule extends AbstractModule {

    /**
     * The virtual agent ID used for system-level consolidation runs.
     * Individual per-agent consolidation is scheduled separately once
     * Track 0D provides per-agent MemoryPlane support.
     */
    static final String AEP_SYSTEM_AGENT_ID = "AEP_SYSTEM";

    /**
     * Provides the {@link HumanReviewQueue} backed by an in-memory store.
     *
     * <p>Uses {@link ReviewNotificationSpi#NOOP} since AEP does not (yet)
     * integrate with an external notification service. The queue is safe for
     * concurrent access and is suitable for staging and low-volume production.
     *
     * @return in-memory human-review queue
     */
    @Provides
    HumanReviewQueue humanReviewQueue() {
        return new InMemoryHumanReviewQueue(ReviewNotificationSpi.NOOP);
    }

    /**
     * Provides the {@link ConflictResolver} using entrenchment ordering.
     *
     * <p>When two memory items conflict (same subject/predicate), the item
     * with higher entrenchment (usage + confirmation count) wins.
     *
     * @return entrenchment conflict resolver
     */
    @Provides
    ConflictResolver conflictResolver() {
        return new EntrenchmentConflictResolver();
    }

    /**
     * Provides the {@link ConsolidationPipeline} for AEP.
     *
     * <p>Wired with a single no-op passthrough stage that logs a trace
     * and immediately returns 0. This placeholder will be superseded once
     * Track 0D ({@code PersistentMemoryPlane}) is wired into AEP and real
     * {@code EpisodicToSemanticConsolidator} / {@code EpisodicToProceduralConsolidator}
     * instances become available.
     *
     * @param conflictResolver conflict resolver for cross-tier conflicts
     * @return consolidation pipeline
     */
    @Provides
    ConsolidationPipeline consolidationPipeline(ConflictResolver conflictResolver) {
        ConsolidationStage noOpStage = new ConsolidationStage() {
            @Override
            public @NotNull String name() {
                return "no-op-passthrough";
            }

            @Override
            public @NotNull Promise<Integer> execute(@NotNull String agentId, @NotNull Instant since) {
                // Track 0D: replace with real EpisodicToSemanticConsolidator
                // and EpisodicToProceduralConsolidator once MemoryPlane is wired.
                return Promise.of(0);
            }
        };
        return new ConsolidationPipeline(List.of(noOpStage), conflictResolver);
    }

    /**
     * Provides the {@link ConsolidationScheduler} for periodic learning runs.
     *
     * <p>The scheduler targets the {@value #AEP_SYSTEM_AGENT_ID} virtual agent
     * and runs at the interval configured via {@code AEP_CONSOLIDATION_INTERVAL_HOURS}.
     * Callers must invoke {@link ConsolidationScheduler#start()} to begin scheduling;
     * the {@link com.ghatana.aep.launcher.AepLauncher} handles this at startup.
     *
     * @param pipeline  consolidation pipeline
     * @param scheduler shared scheduled-executor from {@link AepCoreModule}
     * @return configured consolidation scheduler (not yet started)
     */
    @Provides
    ConsolidationScheduler consolidationScheduler(
            ConsolidationPipeline pipeline,
            ScheduledExecutorService scheduler) {
        EnvConfig env = EnvConfig.fromSystem();
        Duration interval = Duration.ofHours(env.consolidationIntervalHours());
        return new ConsolidationScheduler(pipeline, scheduler, AEP_SYSTEM_AGENT_ID, interval);
    }
}
