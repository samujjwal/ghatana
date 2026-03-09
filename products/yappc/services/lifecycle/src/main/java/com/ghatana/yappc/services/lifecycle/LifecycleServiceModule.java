/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service Module
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.generate.GenerationServiceImpl;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.run.RunServiceImpl;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.shape.ShapeServiceImpl;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.validate.ValidationServiceImpl;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC Lifecycle services.
 *
 * <p>Provides bindings for all 8 SDLC phase services:
 * <ol>
 *   <li>{@link IntentService} — AI-assisted intent capture and analysis</li>
 *   <li>{@link ShapeService} — Architecture and domain modeling</li>
 *   <li>{@link GenerationService} — Code and artifact generation</li>
 *   <li>{@link RunService} — Build execution and task orchestration</li>
 *   <li>{@link ObserveService} — Runtime monitoring and observability</li>
 *   <li>{@link EvolutionService} — Progressive evolution planning</li>
 *   <li>{@link LearningService} — Pattern extraction and learning</li>
 *   <li>{@link ValidationService} — Security and policy compliance</li>
 * </ol>
 *
 * <p>Each service requires:
 * <ul>
 *   <li>{@link CompletionService} — LLM integration (from platform:ai-integration)</li>
 *   <li>{@link AuditLogger} — Audit logging (from platform:observability)</li>
 *   <li>{@link MetricsCollector} — Metrics (from platform:observability)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DI module for lifecycle phase services
 * @doc.layer product
 * @doc.pattern Module
 */
public class LifecycleServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Lifecycle Service DI bindings (8 phase services)");
    }

    // ========== Phase 1: Intent ==========

    /** Provides IntentService for AI-assisted intent capture and analysis. */
    @Provides
    IntentService intentService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating IntentService");
        return new IntentServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 2: Shape ==========

    /** Provides ShapeService for architecture and domain modeling. */
    @Provides
    ShapeService shapeService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating ShapeService");
        return new ShapeServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 3: Generate ==========

    /** Provides GenerationService for code and artifact generation. */
    @Provides
    GenerationService generationService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating GenerationService");
        return new GenerationServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 4: Run ==========

    /** Provides RunService for build execution and orchestration. */
    @Provides
    RunService runService(
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating RunService");
        return new RunServiceImpl(auditLogger, metrics);
    }

    // ========== Phase 5: Observe ==========

    /** Provides ObserveService for runtime monitoring. */
    @Provides
    ObserveService observeService(
            MetricsCollector metrics,
            AuditLogger auditLogger) {
        logger.info("Creating ObserveService");
        return new ObserveServiceImpl(metrics, auditLogger);
    }

    // ========== Phase 6: Evolve ==========

    /** Provides EvolutionService for progressive evolution planning. */
    @Provides
    EvolutionService evolutionService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating EvolutionService");
        return new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 7: Learn ==========

    /** Provides LearningService for pattern extraction and learning. */
    @Provides
    LearningService learningService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating LearningService");
        return new LearningServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 8: Validate ==========

    /**
     * Provides ValidationService for security and policy compliance.
     *
     * <p>PolicyEngine is injected from platform governance modules.</p>
     */
    @Provides
    ValidationService validationService(
            com.ghatana.governance.PolicyEngine policyEngine,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating ValidationService");
        return new ValidationServiceImpl(policyEngine, auditLogger, metrics);
    }
}
