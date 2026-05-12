/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructuredContextInjector mastery-aware rendering.
 *
 * @doc.type class
 * @doc.purpose Test mastery rendering markers and ordering in StructuredContextInjector
 * @doc.layer test
 */
@DisplayName("StructuredContextInjector Mastery Rendering Tests")
public class StructuredContextInjectorMasteryTest {

    @Test
    @DisplayName("Should order procedures by mastery priority")
    void testOrderProceduresByMasteryPriority() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedProcedure obsoleteProcedure = EnhancedProcedure.builder()
                .id("proc-obsolete")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Obsolete situation")
                .action("Obsolete action")
                .confidence(0.5)
                .successRate(0.5)
                .metadata(Map.of("masteryState", "OBSOLETE"))
                .build();

        EnhancedProcedure masteredProcedure = EnhancedProcedure.builder()
                .id("proc-mastered")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Mastered situation")
                .action("Mastered action")
                .confidence(0.95)
                .successRate(0.95)
                .metadata(Map.of("masteryState", "MASTERED"))
                .build();

        EnhancedProcedure practicedProcedure = EnhancedProcedure.builder()
                .id("proc-practiced")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Practiced situation")
                .action("Practiced action")
                .confidence(0.75)
                .successRate(0.75)
                .metadata(Map.of("masteryState", "PRACTICED"))
                .build();

        List<EnhancedProcedure> procedures = List.of(
                obsoleteProcedure, masteredProcedure, practicedProcedure);

        String result = injector.formatForInjection(
                List.of(), List.of(), procedures, Map.of());

        // MASTERED should appear before PRACTICED, and OBSOLETE should be last
        int masteredIndex = result.indexOf("MASTERED");
        int practicedIndex = result.indexOf("PRACTICED");
        int obsoleteIndex = result.indexOf("OBSOLETE");

        assertTrue(masteredIndex < practicedIndex,
                "MASTERED should appear before PRACTICED");
        assertTrue(practicedIndex < obsoleteIndex,
                "PRACTICED should appear before OBSOLETE");
    }

    @Test
    @DisplayName("Should render mastery state markers in markdown")
    void testRenderMasteryStateMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedProcedure masteredProcedure = EnhancedProcedure.builder()
                .id("proc-mastered")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.95)
                .successRate(0.95)
                .metadata(Map.of("masteryState", "MASTERED"))
                .build();

        List<EnhancedProcedure> procedures = List.of(masteredProcedure);

        String result = injector.formatForInjection(
                List.of(), List.of(), procedures, Map.of());

        assertTrue(result.contains("MASTERED"),
                "Result should contain mastery state marker");
        assertTrue(result.contains("✓"),
                "Result should contain checkmark for MASTERED state");
    }

    @Test
    @DisplayName("Should render negative knowledge markers")
    void testRenderNegativeKnowledgeMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedFact negativeFact = EnhancedFact.builder()
                .id("fact-negative")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("API endpoint")
                .predicate("is not available")
                .object("/api/weather")
                .confidence(0.9)
                .metadata(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "justification", "Endpoint deprecated"))
                .build();

        List<EnhancedFact> facts = List.of(negativeFact);

        String result = injector.formatForInjection(
                List.of(), facts, List.of(), Map.of());

        assertTrue(result.contains("NEGATIVE_KNOWLEDGE"),
                "Result should contain negative knowledge marker");
        assertTrue(result.contains("⚠"),
                "Result should contain warning symbol for negative knowledge");
    }

    @Test
    @DisplayName("Should render obsolete markers")
    void testRenderObsoleteMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedProcedure obsoleteProcedure = EnhancedProcedure.builder()
                .id("proc-obsolete")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Obsolete situation")
                .action("Obsolete action")
                .confidence(0.5)
                .successRate(0.5)
                .metadata(Map.of("masteryState", "OBSOLETE"))
                .build();

        List<EnhancedProcedure> procedures = List.of(obsoleteProcedure);

        String result = injector.formatForInjection(
                List.of(), List.of(), procedures, Map.of());

        assertTrue(result.contains("OBSOLETE"),
                "Result should contain obsolete marker");
        assertTrue(result.contains("⊘"),
                "Result should contain obsolete symbol");
    }

    @Test
    @DisplayName("Should render maintenance-only markers")
    void testRenderMaintenanceOnlyMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedProcedure maintenanceProcedure = EnhancedProcedure.builder()
                .id("proc-maintenance")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Maintenance situation")
                .action("Maintenance action")
                .confidence(0.8)
                .successRate(0.8)
                .metadata(Map.of("masteryState", "MAINTENANCE_ONLY"))
                .build();

        List<EnhancedProcedure> procedures = List.of(maintenanceProcedure);

        String result = injector.formatForInjection(
                List.of(), List.of(), procedures, Map.of());

        assertTrue(result.contains("MAINTENANCE_ONLY"),
                "Result should contain maintenance-only marker");
    }

    @Test
    @DisplayName("Should render tentative markers for procedures without mastery state")
    void testRenderTentativeMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedProcedure tentativeProcedure = EnhancedProcedure.builder()
                .id("proc-tentative")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Tentative situation")
                .action("Tentative action")
                .confidence(0.6)
                .successRate(0.6)
                .metadata(Map.of()) // No mastery state
                .build();

        List<EnhancedProcedure> procedures = List.of(tentativeProcedure);

        String result = injector.formatForInjection(
                List.of(), List.of(), procedures, Map.of());

        assertTrue(result.contains("TENTATIVE"),
                "Result should contain tentative marker");
        assertTrue(result.contains("·"),
                "Result should contain tentative symbol");
    }

    @Test
    @DisplayName("Should prioritize negative knowledge in rendering order")
    void testPrioritizeNegativeKnowledge() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedFact negativeFact = EnhancedFact.builder()
                .id("fact-negative")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("API endpoint")
                .predicate("is not available")
                .object("/api/weather")
                .confidence(0.9)
                .metadata(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "justification", "Endpoint deprecated"))
                .build();

        EnhancedFact regularFact = EnhancedFact.builder()
                .id("fact-regular")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("User")
                .predicate("is")
                .object("admin")
                .confidence(0.9)
                .metadata(Map.of())
                .build();

        List<EnhancedFact> facts = List.of(regularFact, negativeFact);

        String result = injector.formatForInjection(
                List.of(), facts, List.of(), Map.of());

        int negativeIndex = result.indexOf("NEGATIVE_KNOWLEDGE");
        int regularIndex = result.indexOf("User is admin");

        assertTrue(negativeIndex < regularIndex,
                "Negative knowledge should appear before regular facts");
    }

    @Test
    @DisplayName("Should handle empty memory items without errors")
    void testHandleEmptyMemoryItems() {
        StructuredContextInjector injector = new StructuredContextInjector();

        String result = injector.formatForInjection(
                List.of(), List.of(), List.of(), Map.of());

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Should include version mismatch markers when applicable")
    void testIncludeVersionMismatchMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedProcedure versionMismatchProcedure = EnhancedProcedure.builder()
                .id("proc-version")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Version mismatch situation")
                .action("Version mismatch action")
                .confidence(0.8)
                .successRate(0.8)
                .metadata(Map.of(
                        "masteryState", "PRACTICED",
                        "versionMismatch", "true"))
                .build();

        List<EnhancedProcedure> procedures = List.of(versionMismatchProcedure);

        String result = injector.formatForInjection(
                List.of(), List.of(), procedures, Map.of());

        assertTrue(result.contains("VERSION_MISMATCH"),
                "Result should contain version mismatch marker");
    }

    @Test
    @DisplayName("Should include verification markers when applicable")
    void testIncludeVerificationMarkers() {
        StructuredContextInjector injector = new StructuredContextInjector();

        EnhancedFact verificationFact = EnhancedFact.builder()
                .id("fact-verify")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("Fact")
                .predicate("requires")
                .object("verification")
                .confidence(0.7)
                .metadata(Map.of("requiresVerification", "true"))
                .build();

        List<EnhancedFact> facts = List.of(verificationFact);

        String result = injector.formatForInjection(
                List.of(), facts, List.of(), Map.of());

        assertTrue(result.contains("REQUIRES_VERIFICATION"),
                "Result should contain verification marker");
    }
}
