/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.promotion;

import com.ghatana.agent.framework.memory.MemoryNamespace;
import com.ghatana.agent.framework.memory.MemoryNamespaceRepository;
import com.ghatana.agent.framework.memory.MemoryScope;
import com.ghatana.datacloud.memory.MemoryService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultMemoryPromotionService}.
 *
 * <p>Exercises all 7 promotion steps, success path, and failure gates.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultMemoryPromotionService 7-step pipeline
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DefaultMemoryPromotionService [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class MemoryPromotionServiceTest extends EventloopTestBase {

    private static final String AGENT_ID    = "agent-promote-001";
    private static final String TENANT_ID   = "tenant-promote-test";
    private static final String MEMORY_ID   = "ep-memory-001";
    private static final String NS_ID       = "ns-procedural-001";
    private static final Instant NOW        = Instant.parse("2026-04-01T10:00:00Z [GH-90000]");

    @Mock
    private MemoryService memoryService;

    @Mock
    private MemoryNamespaceRepository namespaceRepository;

    private DefaultMemoryPromotionService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new DefaultMemoryPromotionService(memoryService, namespaceRepository); // GH-90000
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private MemoryNamespace promotableProceduralNs() { // GH-90000
        return new MemoryNamespace(NS_ID, TENANT_ID, AGENT_ID, MemoryScope.PROCEDURAL, // GH-90000
                "Procedural Skills", null, 365, true, 1000, NOW, NOW, Map.of()); // GH-90000
    }

    private MemoryService.MemoryEntry storedEntry(String id) { // GH-90000
        return new MemoryService.MemoryEntry(id, AGENT_ID, MemoryService.MemoryTier.PROCEDURAL, // GH-90000
                "[PROMOTED] content", null, 0.9, NOW.toEpochMilli(), 0, NOW.toEpochMilli()); // GH-90000
    }

    private MemoryService.MemoryEntry episodicMarkerEntry() { // GH-90000
        return new MemoryService.MemoryEntry("marker-id", AGENT_ID, MemoryService.MemoryTier.EPISODIC, // GH-90000
                "__promoted__:" + MEMORY_ID, null, 0.1, NOW.toEpochMilli(), 0, NOW.toEpochMilli()); // GH-90000
    }

    private MemoryPromotionService.PromotionRequest promotionRequest(double importance) { // GH-90000
        return MemoryPromotionService.PromotionRequest.of( // GH-90000
                AGENT_ID, TENANT_ID, MEMORY_ID, "some episodic content", importance);
    }

    // ─────────────────── constructor validation ────────────────────────────────

    @Nested
    @DisplayName("constructor [GH-90000]")
    class Constructor {

        @Test
        @DisplayName("rejects null memoryService [GH-90000]")
        void rejectsNullMemoryService() { // GH-90000
            assertThatThrownBy(() -> new DefaultMemoryPromotionService(null, namespaceRepository)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects null namespaceRepository [GH-90000]")
        void rejectsNullNamespaceRepository() { // GH-90000
            assertThatThrownBy(() -> new DefaultMemoryPromotionService(memoryService, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────── PromotionRequest record ──────────────────────────────

    @Nested
    @DisplayName("PromotionRequest [GH-90000]")
    class PromotionRequestTests {

        @Test
        @DisplayName("effectiveThreshold returns default when null [GH-90000]")
        void effectiveThresholdDefault() { // GH-90000
            MemoryPromotionService.PromotionRequest req = MemoryPromotionService.PromotionRequest.of( // GH-90000
                    AGENT_ID, TENANT_ID, MEMORY_ID, "content", 0.8);
            assertThat(req.effectiveThreshold()) // GH-90000
                    .isEqualTo(MemoryPromotionService.DEFAULT_PROMOTION_THRESHOLD); // GH-90000
        }

        @Test
        @DisplayName("effectiveThreshold returns caller-supplied value [GH-90000]")
        void effectiveThresholdCustom() { // GH-90000
            MemoryPromotionService.PromotionRequest req = new MemoryPromotionService.PromotionRequest( // GH-90000
                    AGENT_ID, TENANT_ID, MEMORY_ID, "content", 0.8, 0.60);
            assertThat(req.effectiveThreshold()).isEqualTo(0.60); // GH-90000
        }
    }

    // ─────────────────── PromotionEvidence record ─────────────────────────────

    @Nested
    @DisplayName("PromotionEvidence [GH-90000]")
    class PromotionEvidenceTests {

        @Test
        @DisplayName("passing() factory creates passed evidence with no rejection reason [GH-90000]")
        void passingFactory() { // GH-90000
            PromotionEvidence e = PromotionEvidence.passing( // GH-90000
                    "ev-1", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, "EVALUATE", 1, NOW);
            assertThat(e.passed()).isTrue(); // GH-90000
            assertThat(e.rejectedReason()).isNull(); // GH-90000
            assertThat(e.stepOrdinal()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("rejected() factory creates failed evidence with rejection reason [GH-90000]")
        void rejectedFactory() { // GH-90000
            PromotionEvidence e = PromotionEvidence.rejected( // GH-90000
                    "ev-2", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, "ASSESS_QUALITY", 2, "score too low", NOW);
            assertThat(e.passed()).isFalse(); // GH-90000
            assertThat(e.rejectedReason()).isEqualTo("score too low [GH-90000]");
        }

        @Test
        @DisplayName("stepOrdinal below 1 is rejected [GH-90000]")
        void stepOrdinalBelowOneRejected() { // GH-90000
            assertThatThrownBy(() -> PromotionEvidence.passing( // GH-90000
                    "ev", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, "STEP", 0, NOW))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("stepOrdinal [GH-90000]");
        }

        @Test
        @DisplayName("score outside [0,1] is rejected [GH-90000]")
        void scoreOutOfRangeRejected() { // GH-90000
            assertThatThrownBy(() -> new PromotionEvidence( // GH-90000
                    "ev", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, null, "STEP",
                    1, 1.5, true, null, null, null, NOW, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("score [GH-90000]");
        }
    }

    // ─────────────────── Full success path ────────────────────────────────────

    @Nested
    @DisplayName("successful promotion (7 steps) [GH-90000]")
    class SuccessPath {

        @BeforeEach
        void setUpMocks() { // GH-90000
            lenient().when(namespaceRepository.findByAgentAndScope( // GH-90000
                            eq(AGENT_ID), eq(MemoryScope.PROCEDURAL), eq(TENANT_ID))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(promotableProceduralNs()))); // GH-90000
            lenient().when(memoryService.store(eq(AGENT_ID), eq(MemoryService.MemoryTier.EPISODIC), any())) // GH-90000
                    .thenReturn(Promise.of(episodicMarkerEntry())); // GH-90000
            lenient().when(memoryService.store(eq(AGENT_ID), eq(MemoryService.MemoryTier.PROCEDURAL), any())) // GH-90000
                    .thenReturn(Promise.of(storedEntry("target-id-001 [GH-90000]")));
        }

        @Test
        @DisplayName("returns succeeded=true result [GH-90000]")
        void returnsSucceededTrue() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            assertThat(result.succeeded()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns targetMemoryId from procedural write [GH-90000]")
        void returnsTargetMemoryId() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            assertThat(result.targetMemoryId()).isEqualTo("target-id-001 [GH-90000]");
        }

        @Test
        @DisplayName("produces 7 evidence records (one per step) [GH-90000]")
        void produces7EvidenceRecords() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            assertThat(result.evidence()).hasSize(7); // GH-90000
        }

        @Test
        @DisplayName("all 7 evidence records are marked passed [GH-90000]")
        void allEvidenceRecordsPassed() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            assertThat(result.evidence()).allMatch(PromotionEvidence::passed); // GH-90000
        }

        @Test
        @DisplayName("evidence records have sequential step ordinals 1–7 [GH-90000]")
        void evidenceOrdinalsAreSequential() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            List<Integer> ordinals = result.evidence().stream() // GH-90000
                    .map(PromotionEvidence::stepOrdinal) // GH-90000
                    .toList(); // GH-90000
            assertThat(ordinals).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7); // GH-90000
        }

        @Test
        @DisplayName("procedural write is called with PROMOTED content prefix [GH-90000]")
        void proceduralWriteHasPromotedPrefix() { // GH-90000
            runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            verify(memoryService).store(eq(AGENT_ID), eq(MemoryService.MemoryTier.PROCEDURAL), // GH-90000
                    argThat(e -> e.content().startsWith("[PROMOTED] [GH-90000]")));
        }
    }

    // ─────────────────── Gate failure: ASSESS_QUALITY ─────────────────────────

    @Nested
    @DisplayName("gate failure at ASSESS_QUALITY step [GH-90000]")
    class QualityGateFailure {

        @Test
        @DisplayName("returns succeeded=false when score below threshold [GH-90000]")
        void returnsFalseWhenScoreBelowThreshold() { // GH-90000
            // importance 0.10 is below default threshold 0.75
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.10))); // GH-90000
            assertThat(result.succeeded()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("rejectedAtStep is ASSESS_QUALITY [GH-90000]")
        void rejectedAtAssessQualityStep() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.10))); // GH-90000
            assertThat(result.rejectedAtStep()).isEqualTo(DefaultMemoryPromotionService.STEP_ASSESS_QUALITY); // GH-90000
        }

        @Test
        @DisplayName("no namespace lookup when quality gate fails [GH-90000]")
        void noNamespaceLookupWhenQualityFails() { // GH-90000
            runPromise(() -> service.promote(promotionRequest(0.10))); // GH-90000
            verifyNoInteractions(namespaceRepository); // GH-90000
        }

        @Test
        @DisplayName("no memory writes when quality gate fails [GH-90000]")
        void noMemoryWritesWhenQualityFails() { // GH-90000
            runPromise(() -> service.promote(promotionRequest(0.10))); // GH-90000
            verifyNoInteractions(memoryService); // GH-90000
        }
    }

    // ─────────────────── Gate failure: CHECK_NAMESPACE ────────────────────────

    @Nested
    @DisplayName("gate failure at CHECK_NAMESPACE step [GH-90000]")
    class NamespaceGateFailure {

        @BeforeEach
        void setUpNoNamespace() { // GH-90000
            when(namespaceRepository.findByAgentAndScope(any(), eq(MemoryScope.PROCEDURAL), any())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
        }

        @Test
        @DisplayName("returns succeeded=false when no namespace found [GH-90000]")
        void returnsFalseWhenNoNamespace() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            assertThat(result.succeeded()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("rejectedAtStep is CHECK_NAMESPACE [GH-90000]")
        void rejectedAtNamespaceStep() { // GH-90000
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            assertThat(result.rejectedAtStep()).isEqualTo(DefaultMemoryPromotionService.STEP_CHECK_NAMESPACE); // GH-90000
        }

        @Test
        @DisplayName("no memory writes when namespace check fails [GH-90000]")
        void noMemoryWritesWhenNamespaceFails() { // GH-90000
            runPromise(() -> service.promote(promotionRequest(0.90))); // GH-90000
            verifyNoInteractions(memoryService); // GH-90000
        }
    }

    // ─────────────────── promote() null guard ───────────────────────────────── // GH-90000

    @Nested
    @DisplayName("null guard [GH-90000]")
    class NullGuard {

        @Test
        @DisplayName("null request throws NullPointerException [GH-90000]")
        void nullRequestThrows() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.promote(null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
