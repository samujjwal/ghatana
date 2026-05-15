/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.extractor;

import com.ghatana.agent.framework.memory.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tests for FactExtractor.
 * Phase 5 FIX: Tests for semantic fact extraction.
 *
 * @doc.type class
 * @doc.purpose Tests for FactExtractor
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("FactExtractor Tests")
class FactExtractorTest {

    private final FactExtractor extractor = new FactExtractor();

    @Test
    @DisplayName("Should extract semantic facts from episodes")
    void shouldExtractSemanticFacts() {
        List<Episode> episodes = List.of(
                Episode.builder().id("ep1").agentId("agent1").turnId("task1").input("input1").action("action1").output("obs1").reward(1.0).timestamp(Instant.now()).context(Map.of()).build(),
                Episode.builder().id("ep2").agentId("agent1").turnId("task1").input("input2").action("action1").output("obs1").reward(0.9).timestamp(Instant.now()).context(Map.of()).build(),
                Episode.builder().id("ep3").agentId("agent1").turnId("task1").input("input3").action("action1").output("obs1").reward(0.8).timestamp(Instant.now()).context(Map.of()).build()
        );

        List<LearningCandidate> candidates = extractor.extract("agent1", episodes);

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().anyMatch(c -> c.type() == LearningType.SEMANTIC_FACT));
    }

    @Test
    @DisplayName("Should not extract facts from insufficient episodes")
    void shouldNotExtractFromInsufficientEpisodes() {
        List<Episode> episodes = List.of(
                Episode.builder().id("ep1").agentId("agent1").turnId("task1").input("input1").action("action1").output("obs1").reward(1.0).timestamp(Instant.now()).context(Map.of()).build()
        );

        List<LearningCandidate> candidates = extractor.extract("agent1", episodes);

        assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("Should return SEMANTIC_FACT as type")
    void shouldReturnSemanticFactType() {
        assertEquals(LearningType.SEMANTIC_FACT, extractor.type());
    }
}
