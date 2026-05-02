/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("DefaultMemoryPromotionService")
@ExtendWith(MockitoExtension.class) 
class MemoryPromotionServiceTest extends EventloopTestBase {

    private static final String AGENT_ID    = "agent-promote-001";
    private static final String TENANT_ID   = "tenant-promote-test";
    private static final String MEMORY_ID   = "ep-memory-001";
    private static final String NS_ID       = "ns-procedural-001";
    private static final Instant NOW        = Instant.parse("2026-04-01T10:00:00Z");

    @Mock
    private MemoryService memoryService;

    @Mock
    private MemoryNamespaceRepository namespaceRepository;

    private DefaultMemoryPromotionService service;

    @BeforeEach
    void setUp() { 
        service = new DefaultMemoryPromotionService(memoryService, namespaceRepository); 
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private MemoryNamespace promotableProceduralNs() { 
        return new MemoryNamespace(NS_ID, TENANT_ID, AGENT_ID, MemoryScope.PROCEDURAL, 
                "Procedural Skills", null, 365, true, 1000, NOW, NOW, Map.of()); 
    }

    private MemoryService.MemoryEntry storedEntry(String id) { 
        return new MemoryService.MemoryEntry(id, AGENT_ID, MemoryService.MemoryTier.PROCEDURAL, 
                "[PROMOTED] content", null, 0.9, NOW.toEpochMilli(), 0, NOW.toEpochMilli()); 
    }

    private MemoryService.MemoryEntry episodicMarkerEntry() { 
        return new MemoryService.MemoryEntry("marker-id", AGENT_ID, MemoryService.MemoryTier.EPISODIC, 
                "__promoted__:" + MEMORY_ID, null, 0.1, NOW.toEpochMilli(), 0, NOW.toEpochMilli()); 
    }

    private MemoryPromotionService.PromotionRequest promotionRequest(double importance) { 
        return MemoryPromotionService.PromotionRequest.of( 
                AGENT_ID, TENANT_ID, MEMORY_ID, "some episodic content", importance);
    }

    // ─────────────────── constructor validation ────────────────────────────────

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null memoryService")
        void rejectsNullMemoryService() { 
            assertThatThrownBy(() -> new DefaultMemoryPromotionService(null, namespaceRepository)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("rejects null namespaceRepository")
        void rejectsNullNamespaceRepository() { 
            assertThatThrownBy(() -> new DefaultMemoryPromotionService(memoryService, null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    // ─────────────────── PromotionRequest record ──────────────────────────────

    @Nested
    @DisplayName("PromotionRequest")
    class PromotionRequestTests {

        @Test
        @DisplayName("effectiveThreshold returns default when null")
        void effectiveThresholdDefault() { 
            MemoryPromotionService.PromotionRequest req = MemoryPromotionService.PromotionRequest.of( 
                    AGENT_ID, TENANT_ID, MEMORY_ID, "content", 0.8);
            assertThat(req.effectiveThreshold()) 
                    .isEqualTo(MemoryPromotionService.DEFAULT_PROMOTION_THRESHOLD); 
        }

        @Test
        @DisplayName("effectiveThreshold returns caller-supplied value")
        void effectiveThresholdCustom() { 
            MemoryPromotionService.PromotionRequest req = new MemoryPromotionService.PromotionRequest( 
                    AGENT_ID, TENANT_ID, MEMORY_ID, "content", 0.8, 0.60);
            assertThat(req.effectiveThreshold()).isEqualTo(0.60); 
        }
    }

    // ─────────────────── PromotionEvidence record ─────────────────────────────

    @Nested
    @DisplayName("PromotionEvidence")
    class PromotionEvidenceTests {

        @Test
        @DisplayName("passing() factory creates passed evidence with no rejection reason")
        void passingFactory() { 
            PromotionEvidence e = PromotionEvidence.passing( 
                    "ev-1", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, "EVALUATE", 1, NOW);
            assertThat(e.passed()).isTrue(); 
            assertThat(e.rejectedReason()).isNull(); 
            assertThat(e.stepOrdinal()).isEqualTo(1); 
        }

        @Test
        @DisplayName("rejected() factory creates failed evidence with rejection reason")
        void rejectedFactory() { 
            PromotionEvidence e = PromotionEvidence.rejected( 
                    "ev-2", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, "ASSESS_QUALITY", 2, "score too low", NOW);
            assertThat(e.passed()).isFalse(); 
            assertThat(e.rejectedReason()).isEqualTo("score too low");
        }

        @Test
        @DisplayName("stepOrdinal below 1 is rejected")
        void stepOrdinalBelowOneRejected() { 
            assertThatThrownBy(() -> PromotionEvidence.passing( 
                    "ev", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, "STEP", 0, NOW))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("stepOrdinal");
        }

        @Test
        @DisplayName("score outside [0,1] is rejected")
        void scoreOutOfRangeRejected() { 
            assertThatThrownBy(() -> new PromotionEvidence( 
                    "ev", TENANT_ID, AGENT_ID, NS_ID, MEMORY_ID, null, "STEP",
                    1, 1.5, true, null, null, null, NOW, Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("score");
        }
    }

    // ─────────────────── Full success path ────────────────────────────────────

    @Nested
    @DisplayName("successful promotion (7 steps)")
    class SuccessPath {

        @BeforeEach
        void setUpMocks() { 
            lenient().when(namespaceRepository.findByAgentAndScope( 
                            eq(AGENT_ID), eq(MemoryScope.PROCEDURAL), eq(TENANT_ID))) 
                    .thenReturn(Promise.of(Optional.of(promotableProceduralNs()))); 
            lenient().when(memoryService.store(eq(AGENT_ID), eq(MemoryService.MemoryTier.EPISODIC), any())) 
                    .thenReturn(Promise.of(episodicMarkerEntry())); 
            lenient().when(memoryService.store(eq(AGENT_ID), eq(MemoryService.MemoryTier.PROCEDURAL), any())) 
                    .thenReturn(Promise.of(storedEntry("target-id-001")));
        }

        @Test
        @DisplayName("returns succeeded=true result")
        void returnsSucceededTrue() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            assertThat(result.succeeded()).isTrue(); 
        }

        @Test
        @DisplayName("returns targetMemoryId from procedural write")
        void returnsTargetMemoryId() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            assertThat(result.targetMemoryId()).isEqualTo("target-id-001");
        }

        @Test
        @DisplayName("produces 7 evidence records (one per step)")
        void produces7EvidenceRecords() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            assertThat(result.evidence()).hasSize(7); 
        }

        @Test
        @DisplayName("all 7 evidence records are marked passed")
        void allEvidenceRecordsPassed() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            assertThat(result.evidence()).allMatch(PromotionEvidence::passed); 
        }

        @Test
        @DisplayName("evidence records have sequential step ordinals 1–7")
        void evidenceOrdinalsAreSequential() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            List<Integer> ordinals = result.evidence().stream() 
                    .map(PromotionEvidence::stepOrdinal) 
                    .toList(); 
            assertThat(ordinals).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7); 
        }

        @Test
        @DisplayName("procedural write is called with PROMOTED content prefix")
        void proceduralWriteHasPromotedPrefix() { 
            runPromise(() -> service.promote(promotionRequest(0.90))); 
            verify(memoryService).store(eq(AGENT_ID), eq(MemoryService.MemoryTier.PROCEDURAL), 
                    argThat(e -> e.content().startsWith("[PROMOTED]")));
        }
    }

    // ─────────────────── Gate failure: ASSESS_QUALITY ─────────────────────────

    @Nested
    @DisplayName("gate failure at ASSESS_QUALITY step")
    class QualityGateFailure {

        @Test
        @DisplayName("returns succeeded=false when score below threshold")
        void returnsFalseWhenScoreBelowThreshold() { 
            // importance 0.10 is below default threshold 0.75
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.10))); 
            assertThat(result.succeeded()).isFalse(); 
        }

        @Test
        @DisplayName("rejectedAtStep is ASSESS_QUALITY")
        void rejectedAtAssessQualityStep() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.10))); 
            assertThat(result.rejectedAtStep()).isEqualTo(DefaultMemoryPromotionService.STEP_ASSESS_QUALITY); 
        }

        @Test
        @DisplayName("no namespace lookup when quality gate fails")
        void noNamespaceLookupWhenQualityFails() { 
            runPromise(() -> service.promote(promotionRequest(0.10))); 
            verifyNoInteractions(namespaceRepository); 
        }

        @Test
        @DisplayName("no memory writes when quality gate fails")
        void noMemoryWritesWhenQualityFails() { 
            runPromise(() -> service.promote(promotionRequest(0.10))); 
            verifyNoInteractions(memoryService); 
        }
    }

    // ─────────────────── Gate failure: CHECK_NAMESPACE ────────────────────────

    @Nested
    @DisplayName("gate failure at CHECK_NAMESPACE step")
    class NamespaceGateFailure {

        @BeforeEach
        void setUpNoNamespace() { 
            when(namespaceRepository.findByAgentAndScope(any(), eq(MemoryScope.PROCEDURAL), any())) 
                    .thenReturn(Promise.of(Optional.empty())); 
        }

        @Test
        @DisplayName("returns succeeded=false when no namespace found")
        void returnsFalseWhenNoNamespace() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            assertThat(result.succeeded()).isFalse(); 
        }

        @Test
        @DisplayName("rejectedAtStep is CHECK_NAMESPACE")
        void rejectedAtNamespaceStep() { 
            MemoryPromotionService.PromotionResult result =
                    runPromise(() -> service.promote(promotionRequest(0.90))); 
            assertThat(result.rejectedAtStep()).isEqualTo(DefaultMemoryPromotionService.STEP_CHECK_NAMESPACE); 
        }

        @Test
        @DisplayName("no memory writes when namespace check fails")
        void noMemoryWritesWhenNamespaceFails() { 
            runPromise(() -> service.promote(promotionRequest(0.90))); 
            verifyNoInteractions(memoryService); 
        }
    }

    // ─────────────────── promote() null guard ───────────────────────────────── 

    @Nested
    @DisplayName("null guard")
    class NullGuard {

        @Test
        @DisplayName("null request throws NullPointerException")
        void nullRequestThrows() { 
            assertThatThrownBy(() -> runPromise(() -> service.promote(null))) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
