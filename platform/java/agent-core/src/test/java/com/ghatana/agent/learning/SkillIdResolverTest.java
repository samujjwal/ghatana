/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkillIdResolver.
 *
 * @doc.type class
 * @doc.purpose Tests for stable skill ID generation
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("SkillIdResolver Tests")
class SkillIdResolverTest {

    @Test
    @DisplayName("Same agent and situation always produce same skill ID")
    void sameAgentAndSituationProduceSameSkillId() {
        String agentId = "agent-123";
        String situation = "Handle HTTP requests for user authentication";
        
        String id1 = SkillIdResolver.resolveSkillId(agentId, situation);
        String id2 = SkillIdResolver.resolveSkillId(agentId, situation);
        
        assertEquals(id1, id2, "Same input should produce same skill ID");
    }

    @Test
    @DisplayName("Different agents produce different skill IDs")
    void differentAgentsProduceDifferentSkillIds() {
        String situation = "Handle HTTP requests for user authentication";
        
        String id1 = SkillIdResolver.resolveSkillId("agent-123", situation);
        String id2 = SkillIdResolver.resolveSkillId("agent-456", situation);
        
        assertNotEquals(id1, id2, "Different agents should produce different skill IDs");
    }

    @Test
    @DisplayName("Different situations produce different skill IDs")
    void differentSituationsProduceDifferentSkillIds() {
        String agentId = "agent-123";
        
        String id1 = SkillIdResolver.resolveSkillId(agentId, "Handle HTTP requests");
        String id2 = SkillIdResolver.resolveSkillId(agentId, "Handle database queries");
        
        assertNotEquals(id1, id2, "Different situations should produce different skill IDs");
    }

    @Test
    @DisplayName("Normalization handles case insensitivity")
    void normalizationHandlesCaseInsensitivity() {
        String agentId = "agent-123";
        
        String id1 = SkillIdResolver.resolveSkillId(agentId, "Handle HTTP requests");
        String id2 = SkillIdResolver.resolveSkillId(agentId, "handle http requests");
        
        assertEquals(id1, id2, "Case differences should be normalized");
    }

    @Test
    @DisplayName("Normalization handles extra whitespace")
    void normalizationHandlesExtraWhitespace() {
        String agentId = "agent-123";
        
        String id1 = SkillIdResolver.resolveSkillId(agentId, "Handle HTTP requests");
        String id2 = SkillIdResolver.resolveSkillId(agentId, "Handle   HTTP   requests");
        
        assertEquals(id1, id2, "Extra whitespace should be normalized");
    }

    @Test
    @DisplayName("Skill ID format is consistent")
    void skillIdFormatIsConsistent() {
        String agentId = "agent-123";
        String situation = "Handle HTTP requests";
        
        String skillId = SkillIdResolver.resolveSkillId(agentId, situation);
        
        assertTrue(skillId.startsWith("skill-"), "Skill ID should start with 'skill-'");
        assertTrue(skillId.contains(agentId), "Skill ID should contain agent ID");
        assertTrue(skillId.length() > 20, "Skill ID should be sufficiently long for uniqueness");
    }

    @Test
    @DisplayName("Skill ID is deterministic across calls")
    void skillIdIsDeterministicAcrossCalls() {
        String agentId = "agent-123";
        String situation = "Handle HTTP requests for user authentication";
        
        String id1 = SkillIdResolver.resolveSkillId(agentId, situation);
        String id2 = SkillIdResolver.resolveSkillId(agentId, situation);
        String id3 = SkillIdResolver.resolveSkillId(agentId, situation);
        
        assertEquals(id1, id2);
        assertEquals(id2, id3);
    }
}
