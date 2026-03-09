package com.ghatana.yappc.api;

import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
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

/**
 * @doc.type class
 * @doc.purpose Dependency injection module for YAPPC API
 * @doc.layer api
 * @doc.pattern Module
 */
public class YappcApiModule extends AbstractModule {
    
    @Provides
    IntentService intentService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new IntentServiceImpl(aiService, auditLogger, metrics);
    }
    
    @Provides
    ShapeService shapeService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new ShapeServiceImpl(aiService, auditLogger, metrics);
    }
    
    @Provides
    ValidationService validationService(
            PolicyEngine policyEngine,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new ValidationServiceImpl(policyEngine, auditLogger, metrics);
    }
    
    @Provides
    GenerationService generationService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new GenerationServiceImpl(aiService, auditLogger, metrics);
    }
    
    @Provides
    RunService runService(
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new RunServiceImpl(auditLogger, metrics);
    }
    
    @Provides
    ObserveService observeService(
            MetricsCollector metrics,
            AuditLogger auditLogger) {
        return new ObserveServiceImpl(metrics, auditLogger);
    }
    
    @Provides
    LearningService learningService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new LearningServiceImpl(aiService, auditLogger, metrics);
    }
    
    @Provides
    EvolutionService evolutionService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }
}
