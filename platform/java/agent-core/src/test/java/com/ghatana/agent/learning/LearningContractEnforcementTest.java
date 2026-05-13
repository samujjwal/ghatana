/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LearningContract enforcement.
 *
 * @doc.type class
 * @doc.purpose Tests for learning contract enforcement
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("LearningContract Enforcement Tests")
class LearningContractEnforcementTest {

    @Test
    @DisplayName("LearningContract should permit allowed targets")
    void learningContractShouldPermitAllowedTargets() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL, LearningTarget.SEMANTIC_FACT),
                true,
                true
        );

        assertTrue(contract.permits(LearningTarget.PROCEDURAL_SKILL));
        assertTrue(contract.permits(LearningTarget.SEMANTIC_FACT));
    }

    @Test
    @DisplayName("LearningContract should reject disallowed targets")
    void learningContractShouldRejectDisallowedTargets() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true
        );

        assertFalse(contract.permits(LearningTarget.SEMANTIC_FACT));
        assertFalse(contract.permits(LearningTarget.NEGATIVE_KNOWLEDGE));
    }

    @Test
    @DisplayName("LearningContract should enforce level restrictions")
    void learningContractShouldEnforceLevelRestrictions() {
        // L0 should not permit any learning targets
        LearningContract l0Contract = new LearningContract(
                LearningLevel.L0,
                Set.of(),
                false,
                false
        );

        assertFalse(l0Contract.permits(LearningTarget.PROCEDURAL_SKILL));
        assertFalse(l0Contract.permits(LearningTarget.SEMANTIC_FACT));

        // L5 should permit all targets if in allowed set
        LearningContract l5Contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL, LearningTarget.SEMANTIC_FACT),
                true,
                true
        );

        assertTrue(l5Contract.permits(LearningTarget.PROCEDURAL_SKILL));
        assertTrue(l5Contract.permits(LearningTarget.SEMANTIC_FACT));
    }

    @Test
    @DisplayName("LearningContract should throw on requirePermitted when not permitted")
    void learningContractShouldThrowOnRequirePermittedWhenNotPermitted() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> contract.requirePermitted(LearningTarget.SEMANTIC_FACT));
        assertTrue(ex.getMessage().contains("not permitted"));
    }

    @Test
    @DisplayName("LearningContract should not throw on requirePermitted when permitted")
    void learningContractShouldNotThrowOnRequirePermittedWhenPermitted() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true
        );

        assertDoesNotThrow(() -> contract.requirePermitted(LearningTarget.PROCEDURAL_SKILL));
    }

    @Test
    @DisplayName("LearningContract should throw on requirePermitted when not permitted")
    void learningContractShouldThrowOnRequirePermittedWhenPermitted() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true
        );

        assertThrows(IllegalStateException.class, () -> contract.requirePermitted(LearningTarget.SEMANTIC_FACT));
    }

    @Test
    @DisplayName("LearningContract should require provenance for high levels")
    void learningContractShouldRequireProvenanceForHighLevels() {
        LearningContract l3Contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,  // L3 requires provenance
                true   // L3 requires promotion
        );

        assertTrue(l3Contract.requiresProvenanceFor(LearningTarget.PROCEDURAL_SKILL));
    }

    @Test
    @DisplayName("LearningContract should require promotion for high levels")
    void learningContractShouldRequirePromotionForHighLevels() {
        LearningContract l3Contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,  // L3 requires provenance
                true   // L3 requires promotion
        );

        assertTrue(l3Contract.requiresPromotionFor(LearningTarget.PROCEDURAL_SKILL));
    }

    @Test
    @DisplayName("LearningContract should validate constructor invariants")
    void learningContractShouldValidateConstructorInvariants() {
        // L3 requires provenance
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                false, // provenanceRequired=false but L3 requires it
                false
        ));
        assertTrue(ex1.getMessage().contains("provenanceRequired=true"));

        // L3 requires promotion
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                false // promotionRequired=false but L3 requires it
        ));
        assertTrue(ex2.getMessage().contains("promotionRequired=true"));
    }

    @Test
    @DisplayName("LearningContract should permit any when at least one target matches")
    void learningContractShouldPermitAnyWhenAtLeastOneTargetMatches() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.PROCEDURAL_SKILL, LearningTarget.SEMANTIC_FACT),
                true,
                true
        );

        assertTrue(contract.permitsAny(Set.of(
                LearningTarget.PROCEDURAL_SKILL,
                LearningTarget.SEMANTIC_FACT
        )));

        assertTrue(contract.permitsAny(Set.of(
                LearningTarget.PROCEDURAL_SKILL,
                LearningTarget.NEGATIVE_KNOWLEDGE // not in allowed set
        )));

        assertFalse(contract.permitsAny(Set.of(
                LearningTarget.NEGATIVE_KNOWLEDGE,
                LearningTarget.RETRIEVAL_POLICY // neither in allowed set
        )));
    }
}
