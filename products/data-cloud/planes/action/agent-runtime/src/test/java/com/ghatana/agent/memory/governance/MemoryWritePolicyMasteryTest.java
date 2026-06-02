/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.ValidityStatus;
import com.ghatana.agent.memory.model.artifact.Decision;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for mastery-aware memory write validation.
 *
 * @doc.type class
 * @doc.purpose Verify MemoryWritePolicy static validation rules
 * @doc.layer test
 */
@DisplayName("MemoryWritePolicy Mastery")
class MemoryWritePolicyMasteryTest {

    @Test
    @DisplayName("accepts valid PROCEDURAL_SKILL labels")
    void acceptsValidProceduralSkill() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-001")
                .tenantId("tenant-a")
                .agentId("agent-a")
                .situation("situation")
                .action("action")
                .confidence(0.8)
                .successRate(0.75)
                .labels(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "skillId", "skill-001",
                        "masteryState", "PRACTICED",
                        "provenanceRequired", "true",
                        "provenance", "episode-1"
                ))
                .build();

        assertDoesNotThrow(() -> MemoryWritePolicy.validateProcedure(procedure));
    }

    @Test
    @DisplayName("rejects PROCEDURAL_SKILL missing skillId")
    void rejectsProceduralSkillWithoutSkillId() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-002")
                .tenantId("tenant-a")
                .agentId("agent-a")
                .situation("situation")
                .action("action")
                .confidence(0.8)
                .successRate(0.75)
                .labels(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "masteryState", "PRACTICED",
                        "provenanceRequired", "true",
                        "provenance", "episode-1"
                ))
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MemoryWritePolicy.validateProcedure(procedure));
        assertTrue(error.getMessage().contains("skillId"));
    }

    @Test
    @DisplayName("rejects NEGATIVE_KNOWLEDGE without justification")
    void rejectsNegativeKnowledgeWithoutJustification() {
        EnhancedFact fact = EnhancedFact.builder()
                .id("fact-1")
                .tenantId("tenant-a")
                .agentId("agent-a")
                .subject("api")
                .predicate("status")
                .object("deprecated")
                .confidence(0.9)
                .labels(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "validationState", "VALIDATED",
                        "evidenceRef", "ticket-1"
                ))
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MemoryWritePolicy.validateFact(fact));
        assertTrue(error.getMessage().contains("justification"));
    }

    @Test
    @DisplayName("accepts NEGATIVE_KNOWLEDGE with validation evidence and justification")
    void acceptsNegativeKnowledgeWithJustification() {
        EnhancedFact fact = EnhancedFact.builder()
                .id("fact-2")
                .tenantId("tenant-a")
                .agentId("agent-a")
                .subject("api")
                .predicate("status")
                .object("deprecated")
                .confidence(0.9)
                .labels(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "validationState", "VALIDATED",
                        "evidenceRef", "ticket-1",
                        "justification", "deprecated endpoint"
                ))
                .build();

        assertDoesNotThrow(() -> MemoryWritePolicy.validateFact(fact));
    }

    @Test
    @DisplayName("policy artifacts require approvedBy")
    void policyArtifactsRequireApprovedBy() {
        Decision decision = decisionArtifact(Map.of(
                "learningTarget", "RETRIEVAL_POLICY"
        ));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MemoryWritePolicy.validateArtifact(decision));
        assertTrue(error.getMessage().contains("approvedBy"));
    }

    @Test
    @DisplayName("MODEL_ADAPTER artifacts cannot self-activate")
    void modelAdapterArtifactsAreRejected() {
        Decision decision = decisionArtifact(Map.of(
                "learningTarget", "MODEL_ADAPTER"
        ));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MemoryWritePolicy.validateArtifact(decision));
        assertTrue(error.getMessage().contains("cannot self-activate"));
    }

    private Decision decisionArtifact(Map<String, String> labels) {
        return Decision.builder()
                .id("artifact-1")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .provenance(Provenance.builder()
                        .source("test")
                        .confidenceSource(Provenance.ConfidenceSource.TOOL_OUTPUT)
                        .agentId("agent-a")
                        .build())
                .validity(Validity.builder()
                        .confidence(1.0)
                        .decayRate(0.0)
                        .lastVerified(Instant.parse("2026-01-01T00:00:00Z"))
                        .status(ValidityStatus.ACTIVE)
                        .build())
                .tenantId("tenant-a")
                .summary("summary")
                .rationale("rationale")
                .chosenOption("option-a")
                .confidence(0.8)
                .labels(labels)
                .build();
    }
}
