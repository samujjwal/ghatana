/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MemoryWritePolicy mastery-aware validation.
 *
 * @doc.type class
 * @doc.purpose Test mastery validation in MemoryWritePolicy
 * @doc.layer test
 */
@DisplayName("MemoryWritePolicy Mastery Validation Tests")
public class MemoryWritePolicyMasteryTest {

    @Test
    @DisplayName("Should accept valid PROCEDURAL_SKILL with all required metadata")
    void testAcceptValidProceduralSkill() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-001")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.8)
                .successRate(0.75)
                .metadata(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "skillId", "skill-001",
                        "masteryState", "PRACTICED",
                        "provenance", "episode-001"))
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        assertDoesNotThrow(() -> policy.validateProcedure(procedure));
    }

    @Test
    @DisplayName("Should reject PROCEDURAL_SKILL without skillId")
    void testRejectProceduralSkillWithoutSkillId() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-invalid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.8)
                .successRate(0.75)
                .metadata(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "masteryState", "PRACTICED"))
                // Missing skillId
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> policy.validateProcedure(procedure));
        assertTrue(exception.getMessage().contains("skillId"));
    }

    @Test
    @DisplayName("Should reject PROCEDURAL_SKILL without masteryState")
    void testRejectProceduralSkillWithoutMasteryState() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-invalid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.8)
                .successRate(0.75)
                .metadata(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "skillId", "skill-001"))
                // Missing masteryState
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> policy.validateProcedure(procedure));
        assertTrue(exception.getMessage().contains("masteryState"));
    }

    @Test
    @DisplayName("Should reject PROCEDURAL_SKILL with invalid masteryState")
    void testRejectProceduralSkillWithInvalidMasteryState() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-invalid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.8)
                .successRate(0.75)
                .metadata(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "skillId", "skill-001",
                        "masteryState", "INVALID_STATE"))
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> policy.validateProcedure(procedure));
        assertTrue(exception.getMessage().contains("masteryState"));
    }

    @Test
    @DisplayName("Should reject PROCEDURAL_SKILL without provenance when required")
    void testRejectProceduralSkillWithoutProvenance() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("skill-invalid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.8)
                .successRate(0.75)
                .metadata(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "skillId", "skill-001",
                        "masteryState", "PRACTICED",
                        "provenanceRequired", "true"))
                // Missing provenance
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> policy.validateProcedure(procedure));
        assertTrue(exception.getMessage().contains("provenance"));
    }

    @Test
    @DisplayName("Should accept NEGATIVE_KNOWLEDGE with justification")
    void testAcceptNegativeKnowledgeWithJustification() {
        EnhancedFact fact = EnhancedFact.builder()
                .id("fact-001")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("API endpoint")
                .predicate("is not available")
                .object("/api/weather")
                .confidence(0.9)
                .metadata(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "justification", "Endpoint deprecated in v2.0",
                        "evidenceRef", "ticket-12345"))
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        assertDoesNotThrow(() -> policy.validateFact(fact));
    }

    @Test
    @DisplayName("Should reject NEGATIVE_KNOWLEDGE without justification")
    void testRejectNegativeKnowledgeWithoutJustification() {
        EnhancedFact fact = EnhancedFact.builder()
                .id("fact-invalid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("API endpoint")
                .predicate("is not available")
                .object("/api/weather")
                .confidence(0.9)
                .metadata(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE"))
                // Missing justification
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> policy.validateFact(fact));
        assertTrue(exception.getMessage().contains("justification"));
    }

    @Test
    @DisplayName("Should accept facts without NEGATIVE_KNOWLEDGE target")
    void testAcceptFactWithoutNegativeKnowledgeTarget() {
        EnhancedFact fact = EnhancedFact.builder()
                .id("fact-001")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("User")
                .predicate("is")
                .object("admin")
                .confidence(0.9)
                .metadata(Map.of())
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        assertDoesNotThrow(() -> policy.validateFact(fact));
    }

    @Test
    @DisplayName("Should accept artifact with typed metadata")
    void testAcceptArtifactWithTypedMetadata() {
        TypedArtifact artifact = TypedArtifact.builder()
                .id("artifact-001")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .artifactType("MODEL_ADAPTER")
                .content("model-parameters")
                .metadata(Map.of(
                        "learningTarget", "MODEL_ADAPTER",
                        "modelVersion", "v2.0",
                        "framework", "tensorflow"))
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        assertDoesNotThrow(() -> policy.validateArtifact(artifact));
    }

    @Test
    @DisplayName("Should accept procedure without PROCEDURAL_SKILL target")
    void testAcceptProcedureWithoutProceduralSkillTarget() {
        EnhancedProcedure procedure = EnhancedProcedure.builder()
                .id("proc-001")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("Test situation")
                .action("Test action")
                .confidence(0.8)
                .successRate(0.75)
                .metadata(Map.of())
                .build();

        MemoryWritePolicy policy = new MemoryWritePolicy();
        assertDoesNotThrow(() -> policy.validateProcedure(procedure));
    }
}
