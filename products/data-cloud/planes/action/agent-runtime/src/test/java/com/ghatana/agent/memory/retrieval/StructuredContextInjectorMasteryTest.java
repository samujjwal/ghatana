/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for mastery-aware rendering and ordering in StructuredContextInjector.
 *
 * @doc.type class
 * @doc.purpose Validate current injector API and mastery markers
 * @doc.layer test
 */
@DisplayName("StructuredContextInjector Mastery Rendering Tests")
class StructuredContextInjectorMasteryTest {

    private final StructuredContextInjector injector = new StructuredContextInjector();
    private final InjectionConfig config = InjectionConfig.builder()
            .format(InjectionConfig.Format.MARKDOWN)
            .groupByTier(false)
            .includeConfidence(false)
            .includeProvenance(false)
            .includeConflictMarkers(false)
            .maxTokens(4000)
            .build();

    @Test
    @DisplayName("orders negative knowledge before mastered and practiced procedures")
    void ordersByMasteryPriority() {
        EnhancedFact negativeKnowledge = EnhancedFact.builder()
                .id("fact-negative")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("api")
                .predicate("status")
                .object("deprecated")
                .confidence(0.9)
                .labels(Map.of("negativeKnowledge", "true"))
                .build();

        EnhancedProcedure mastered = EnhancedProcedure.builder()
                .id("proc-mastered")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("mastered-situation")
                .action("mastered-action")
                .confidence(0.9)
                .successRate(0.9)
                .labels(Map.of("masteryState", "MASTERED"))
                .build();

        EnhancedProcedure practiced = EnhancedProcedure.builder()
                .id("proc-practiced")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("practiced-situation")
                .action("practiced-action")
                .confidence(0.8)
                .successRate(0.8)
                .labels(Map.of("masteryState", "PRACTICED"))
                .build();

        String result = injector.formatForInjection(List.of(
                new ScoredMemoryItem(practiced, 0.50, Map.of()),
                new ScoredMemoryItem(negativeKnowledge, 0.10, Map.of()),
                new ScoredMemoryItem(mastered, 0.60, Map.of())
        ), config);

        int negativeIndex = result.indexOf("NEGATIVE_KNOWLEDGE");
        int masteredIndex = result.indexOf("MASTERED");
        int practicedIndex = result.indexOf("PRACTICED");

        assertTrue(negativeIndex >= 0);
        assertTrue(masteredIndex >= 0);
        assertTrue(practicedIndex >= 0);
        assertTrue(negativeIndex < masteredIndex);
        assertTrue(masteredIndex < practicedIndex);
    }

    @Test
    @DisplayName("renders obsolete marker for obsolete procedures")
    void rendersObsoleteMarker() {
        EnhancedProcedure obsolete = EnhancedProcedure.builder()
                .id("proc-obsolete")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("obsolete-situation")
                .action("obsolete-action")
                .confidence(0.5)
                .successRate(0.5)
                .labels(Map.of("masteryState", "OBSOLETE"))
                .build();

        String result = injector.formatForInjection(
                List.of(new ScoredMemoryItem(obsolete, 0.4, Map.of())),
                config
        );

        assertTrue(result.contains("OBSOLETE - DO NOT USE"));
    }

    @Test
    @DisplayName("returns empty output for empty input")
    void handlesEmptyInput() {
        String result = injector.formatForInjection(List.of(), config);
        assertEquals("", result);
    }
}
