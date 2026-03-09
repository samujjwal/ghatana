/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.attention.AttentionManager;
import com.ghatana.datacloud.attention.DefaultSalienceScorer;
import com.ghatana.datacloud.attention.SalienceScorer;
import com.ghatana.datacloud.brain.BrainConfig;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.brain.DefaultDataCloudBrain;
import com.ghatana.datacloud.memory.DefaultMemoryTierRouter;
import com.ghatana.datacloud.memory.MemoryTierRouter;
import com.ghatana.datacloud.pattern.DefaultPatternCatalog;
import com.ghatana.datacloud.pattern.PatternCatalog;
import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.reflex.DefaultReflexEngine;
import com.ghatana.datacloud.reflex.ReflexEngine;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability;
import com.ghatana.datacloud.spi.ai.PredictionCapability;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module for data-cloud brain, attention, and cognitive components.
 *
 * <p>Provides the cognitive architecture — the brain, reflex engine, pattern
 * catalog, global workspace, and attention management:
 * <ul>
 *   <li>{@link DataCloudBrain} — central cognitive processor with salience-driven
 *       attention, memory tier routing, pattern recognition, and reflex responses</li>
 *   <li>{@link ReflexEngine} — fast, rule-based reflex responses</li>
 *   <li>{@link PatternCatalog} — pattern storage and retrieval</li>
 *   <li>{@link GlobalWorkspace} — shared workspace for cognitive broadcasting</li>
 *   <li>{@link SalienceScorer} — attention-driven salience scoring</li>
 *   <li>{@link AttentionManager} — spotlight management for global workspace</li>
 *   <li>{@link MemoryTierRouter} — salience-based memory tier routing</li>
 *   <li>{@link BrainConfig} — brain configuration with learning and reflex toggles</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Requires external bindings for AI capabilities:
 * {@link AnomalyDetectionCapability}, {@link PredictionCapability},
 * {@link LearningSignalStore}, and {@link MetricsCollector}.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new ObservabilityModule(),      // provides MetricsCollector
 *     aiCapabilitiesModule,           // provides AI SPIs
 *     new DataCloudBrainModule()
 * );
 * DataCloudBrain brain = injector.getInstance(DataCloudBrain.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for data-cloud cognitive architecture
 * @doc.layer product
 * @doc.pattern Module, Facade
 * @see DataCloudBrain
 * @see ReflexEngine
 * @see PatternCatalog
 * @see GlobalWorkspace
 */
public class DataCloudBrainModule extends AbstractModule {

    // ═══════════════════════════════════════════════════════════════
    //  Self-Contained Components (no external deps)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the brain configuration with production defaults.
     *
     * <p>Defaults: learning enabled, reflexes enabled, salience threshold 0.5,
     * brain ID {@code "dc-brain-default"}.
     *
     * @return brain config
     */
    @Provides
    BrainConfig brainConfig() {
        return BrainConfig.builder()
                .brainId("dc-brain-default")
                .learningEnabled(true)
                .reflexesEnabled(true)
                .salienceThreshold(0.5f)
                .build();
    }

    /**
     * Provides the reflex engine.
     *
     * <p>Self-contained rule-based engine for fast reflex responses.
     * Maintains reflex rules in a thread-safe map with enum-dispatched handlers.
     *
     * @return reflex engine
     */
    @Provides
    ReflexEngine reflexEngine() {
        return new DefaultReflexEngine();
    }

    /**
     * Provides the pattern catalog.
     *
     * <p>In-memory pattern storage with CRUD operations, tagging, and
     * search by type/tag. Thread-safe via ConcurrentHashMap.
     *
     * @return pattern catalog
     */
    @Provides
    PatternCatalog patternCatalog() {
        return new DefaultPatternCatalog();
    }

    /**
     * Provides the memory tier router.
     *
     * <p>Routes data records to appropriate memory tiers (working, short-term,
     * long-term) based on salience scores. In-memory implementation with
     * EnumMap-based tier storage.
     *
     * @return memory tier router for data records
     */
    @Provides
    MemoryTierRouter<DataRecord> memoryTierRouter() {
        return new DefaultMemoryTierRouter<>();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Components with External Dependencies
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the salience scorer.
     *
     * <p>Scores incoming data records based on anomaly detection,
     * prediction confidence, and learning signals. Combines three
     * AI capabilities into a unified salience score.
     *
     * @param anomalyDetector anomaly detection SPI
     * @param predictor       prediction SPI
     * @param signalStore     learning signal store
     * @return salience scorer
     */
    @Provides
    SalienceScorer salienceScorer(AnomalyDetectionCapability anomalyDetector,
                                   PredictionCapability predictor,
                                   LearningSignalStore signalStore) {
        return new DefaultSalienceScorer(anomalyDetector, predictor, signalStore);
    }

    /**
     * Provides the global workspace.
     *
     * <p>Shared cognitive workspace for broadcasting high-salience items.
     * Optionally integrates with a learning signal store and metrics collector.
     * The context gateway (LLM integration) is not wired by default — provide
     * a {@code ContextGateway} binding and override this method if needed.
     *
     * @param signalStore learning signal store for feedback integration
     * @param metrics     metrics collector for workspace operation tracking
     * @return global workspace
     */
    @Provides
    GlobalWorkspace globalWorkspace(LearningSignalStore signalStore,
                                    MetricsCollector metrics) {
        return GlobalWorkspace.builder()
                .signalStore(signalStore)
                .metricsCollector(metrics)
                .maxSpotlightSize(100)
                .build();
    }

    /**
     * Provides the attention manager.
     *
     * <p>Manages the spotlight — the set of items currently in focus.
     * Uses the salience scorer to evaluate items and the global workspace
     * to broadcast high-salience discoveries.
     *
     * @param scorer    salience scorer for item evaluation
     * @param workspace global workspace for broadcasting
     * @param metrics   metrics collector for attention tracking
     * @return attention manager
     */
    @Provides
    AttentionManager attentionManager(SalienceScorer scorer,
                                       GlobalWorkspace workspace,
                                       MetricsCollector metrics) {
        return AttentionManager.builder()
                .salienceScorer(scorer)
                .globalWorkspace(workspace)
                .metricsCollector(metrics)
                .elevationThreshold(0.7)
                .emergencyThreshold(0.95)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Composite Brain
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the data-cloud brain — the top-level cognitive facade.
     *
     * <p>Assembles the full cognitive pipeline: salience scoring → attention
     * management → memory tier routing → pattern recognition → reflex response.
     * The {@code SimilaritySearchCapability} (vector memory) is not wired by
     * default; provide it as a binding and override if needed.
     *
     * @param config       brain configuration
     * @param scorer       salience scorer
     * @param workspace    global workspace
     * @param memoryRouter memory tier router
     * @param catalog      pattern catalog
     * @param reflex       reflex engine
     * @return data-cloud brain
     */
    @Provides
    DataCloudBrain dataCloudBrain(BrainConfig config,
                                   SalienceScorer scorer,
                                   GlobalWorkspace workspace,
                                   MemoryTierRouter<DataRecord> memoryRouter,
                                   PatternCatalog catalog,
                                   ReflexEngine reflex) {
        return new DefaultDataCloudBrain(
                config, scorer, workspace, memoryRouter, catalog, reflex, null);
    }
}
