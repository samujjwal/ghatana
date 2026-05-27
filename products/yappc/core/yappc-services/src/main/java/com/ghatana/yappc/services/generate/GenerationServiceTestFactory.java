package com.ghatana.yappc.services.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.GenerationRunRepository;

/**
 * Dev/test-only factory for GenerationService instances with explicit test collaborators.
 */
public final class GenerationServiceTestFactory {

    private GenerationServiceTestFactory() {
    }

    public static GenerationService create(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            GenerationRunRepository generationRunRepository,
            ObjectMapper objectMapper
    ) {
        return new GenerationServiceImpl(
                aiService,
                auditLogger,
                metrics,
                generationRunRepository,
                objectMapper,
                AiHealthProvider.alwaysHealthy(),
                new GenerationAssuranceService());
    }

    public static GenerationService create(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            GenerationRunRepository generationRunRepository,
            ObjectMapper objectMapper,
            AiHealthProvider aiHealthProvider
    ) {
        return new GenerationServiceImpl(
                aiService,
                auditLogger,
                metrics,
                generationRunRepository,
                objectMapper,
                aiHealthProvider,
                new GenerationAssuranceService());
    }
}