/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.governance;

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
 * Unit tests for {@link DefaultMemoryGovernanceService} and {@link DefaultRetrievalQualityService}.
 *
 * @doc.type class
 * @doc.purpose Tests for governance service and retrieval quality computation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MemoryGovernanceService and RetrievalQualityService [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class MemoryGovernanceServiceTest extends EventloopTestBase {

    private static final String AGENT_ID   = "agent-gov-001";
    private static final String TENANT_ID  = "tenant-gov-test";
    private static final String NS_ID      = "ns-gov-001";
    private static final Instant NOW       = Instant.parse("2026-04-01T12:00:00Z [GH-90000]");

    // ─══════════════════════════════════════════════════════════════════════════
    //  DefaultMemoryGovernanceService
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultMemoryGovernanceService [GH-90000]")
    class GovernanceServiceTests {

        @Mock
        private MemoryNamespaceRepository namespaceRepository;

        @Mock
        private MemoryService memoryService;

        private DefaultMemoryGovernanceService service;

        @BeforeEach
        void setUp() { // GH-90000
            service = new DefaultMemoryGovernanceService(namespaceRepository, memoryService); // GH-90000
        }

        private MemoryNamespace namespace(Instant now) { // GH-90000
            return new MemoryNamespace(NS_ID, TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, // GH-90000
                    "Test NS", null, 30, false, 500, now, now, Map.of()); // GH-90000
        }

        // ── constructor ───────────────────────────────────────────────────────

        @Nested
        @DisplayName("constructor [GH-90000]")
        class Constructor {

            @Test
            @DisplayName("rejects null namespaceRepository [GH-90000]")
            void rejectsNullRepo() { // GH-90000
                assertThatThrownBy(() -> new DefaultMemoryGovernanceService(null, memoryService)) // GH-90000
                        .isInstanceOf(NullPointerException.class); // GH-90000
            }

            @Test
            @DisplayName("rejects null memoryService [GH-90000]")
            void rejectsNullMemoryService() { // GH-90000
                assertThatThrownBy(() -> new DefaultMemoryGovernanceService(namespaceRepository, null)) // GH-90000
                        .isInstanceOf(NullPointerException.class); // GH-90000
            }
        }

        // ── enforceRetention ──────────────────────────────────────────────────

        @Nested
        @DisplayName("enforceRetention [GH-90000]")
        class EnforceRetention {

            @Test
            @DisplayName("returns result with matching agentId [GH-90000]")
            void returnsResultWithAgentId() { // GH-90000
                when(namespaceRepository.findByAgent(AGENT_ID, TENANT_ID)) // GH-90000
                        .thenReturn(Promise.of(List.of())); // GH-90000
                MemoryGovernanceService.RetentionEnforcementResult result =
                        runPromise(() -> service.enforceRetention(AGENT_ID, TENANT_ID)); // GH-90000
                assertThat(result.agentId()).isEqualTo(AGENT_ID); // GH-90000
            }

            @Test
            @DisplayName("returns zero eviction for namespace with no retention policy [GH-90000]")
            void zeroEvictionForNoRetentionPolicy() { // GH-90000
                MemoryNamespace ns = new MemoryNamespace(NS_ID, TENANT_ID, AGENT_ID, // GH-90000
                        MemoryScope.EPISODIC, "NS", null, null /* no retention */,
                        false, 500, NOW, NOW, Map.of()); // GH-90000
                when(namespaceRepository.findByAgent(AGENT_ID, TENANT_ID)) // GH-90000
                        .thenReturn(Promise.of(List.of(ns))); // GH-90000
                MemoryGovernanceService.RetentionEnforcementResult r =
                        runPromise(() -> service.enforceRetention(AGENT_ID, TENANT_ID)); // GH-90000
                assertThat(r.entriesEvicted()).isZero(); // GH-90000
            }

            @Test
            @DisplayName("null agentId throws NullPointerException [GH-90000]")
            void nullAgentIdThrows() { // GH-90000
                assertThatThrownBy(() -> runPromise(() -> service.enforceRetention(null, TENANT_ID))) // GH-90000
                        .isInstanceOf(NullPointerException.class); // GH-90000
            }
        }

        // ── evaluateAccess ────────────────────────────────────────────────────

        @Nested
        @DisplayName("evaluateAccess [GH-90000]")
        class EvaluateAccess {

            @Test
            @DisplayName("permits when namespace found and tenant matches [GH-90000]")
            void permitsOnMatch() { // GH-90000
                when(namespaceRepository.findById(NS_ID)) // GH-90000
                        .thenReturn(Promise.of(Optional.of(namespace(NOW)))); // GH-90000
                MemoryGovernanceService.AccessDecision d =
                        runPromise(() -> service.evaluateAccess(NS_ID, "principal-1", TENANT_ID)); // GH-90000
                assertThat(d.permitted()).isTrue(); // GH-90000
            }

            @Test
            @DisplayName("denies when namespace not found [GH-90000]")
            void deniesWhenNotFound() { // GH-90000
                when(namespaceRepository.findById(NS_ID)) // GH-90000
                        .thenReturn(Promise.of(Optional.empty())); // GH-90000
                MemoryGovernanceService.AccessDecision d =
                        runPromise(() -> service.evaluateAccess(NS_ID, "principal-1", TENANT_ID)); // GH-90000
                assertThat(d.permitted()).isFalse(); // GH-90000
                assertThat(d.reason()).containsIgnoringCase("not found [GH-90000]");
            }

            @Test
            @DisplayName("denies when tenant does not match [GH-90000]")
            void deniesOnTenantMismatch() { // GH-90000
                when(namespaceRepository.findById(NS_ID)) // GH-90000
                        .thenReturn(Promise.of(Optional.of(namespace(NOW)))); // GH-90000
                MemoryGovernanceService.AccessDecision d =
                        runPromise(() -> service.evaluateAccess(NS_ID, "principal-1", "other-tenant")); // GH-90000
                assertThat(d.permitted()).isFalse(); // GH-90000
                assertThat(d.reason()).containsIgnoringCase("tenant [GH-90000]");
            }
        }

        // ── setRetentionPolicy ────────────────────────────────────────────────

        @Nested
        @DisplayName("setRetentionPolicy [GH-90000]")
        class SetRetentionPolicy {

            @Test
            @DisplayName("saves updated namespace with new retentionDays [GH-90000]")
            void savesWithNewRetentionDays() { // GH-90000
                MemoryNamespace ns = namespace(NOW); // GH-90000
                when(namespaceRepository.findById(NS_ID)) // GH-90000
                        .thenReturn(Promise.of(Optional.of(ns))); // GH-90000
                when(namespaceRepository.save(any())) // GH-90000
                        .thenAnswer(inv -> Promise.of(inv.getArgument(0))); // GH-90000
                MemoryNamespace updated =
                        runPromise(() -> service.setRetentionPolicy(NS_ID, 90, TENANT_ID)); // GH-90000
                assertThat(updated.retentionDays()).isEqualTo(90); // GH-90000
                verify(namespaceRepository).save(argThat(n -> n.retentionDays() == 90)); // GH-90000
            }

            @Test
            @DisplayName("fails with IllegalArgumentException when namespace not found [GH-90000]")
            void failsWhenNotFound() { // GH-90000
                when(namespaceRepository.findById(NS_ID)) // GH-90000
                        .thenReturn(Promise.of(Optional.empty())); // GH-90000
                assertThatThrownBy(() -> runPromise(() -> // GH-90000
                        service.setRetentionPolicy(NS_ID, 30, TENANT_ID))) // GH-90000
                        .isInstanceOf(IllegalArgumentException.class) // GH-90000
                        .hasMessageContaining("not found [GH-90000]");
            }
        }

        // ── auditLog ──────────────────────────────────────────────────────────

        @Nested
        @DisplayName("auditLog [GH-90000]")
        class AuditLog {

            @Test
            @DisplayName("returns empty list (baseline implementation) [GH-90000]")
            void returnsEmptyList() { // GH-90000
                List<MemoryGovernanceService.GovernanceEvent> events =
                        runPromise(() -> service.auditLog(NS_ID, TENANT_ID, NOW)); // GH-90000
                assertThat(events).isEmpty(); // GH-90000
            }
        }
    }

    // ─══════════════════════════════════════════════════════════════════════════
    //  DefaultRetrievalQualityService
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultRetrievalQualityService [GH-90000]")
    class RetrievalQualityServiceTests {

        private DefaultRetrievalQualityService service;

        @BeforeEach
        void setUp() { // GH-90000
            service = new DefaultRetrievalQualityService(); // GH-90000
        }

        @Test
        @DisplayName("perfect recall: precision=1.0, recall=1.0, f1=1.0 [GH-90000]")
        void perfectRecall() { // GH-90000
            RetrievalQualityService.QualityReport report = runPromise(() -> // GH-90000
                    service.score(AGENT_ID, TENANT_ID, List.of("a", "b"), List.of("a", "b"))); // GH-90000
            assertThat(report.precision()).isEqualTo(1.0); // GH-90000
            assertThat(report.recall()).isEqualTo(1.0); // GH-90000
            assertThat(report.f1Score()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("zero overlap: precision=0, recall=0, f1=0 [GH-90000]")
        void zeroOverlap() { // GH-90000
            RetrievalQualityService.QualityReport report = runPromise(() -> // GH-90000
                    service.score(AGENT_ID, TENANT_ID, List.of("x [GH-90000]"), List.of("a", "b")));
            assertThat(report.precision()).isZero(); // GH-90000
            assertThat(report.recall()).isZero(); // GH-90000
            assertThat(report.f1Score()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("partial recall: computes correct precision, recall, f1 [GH-90000]")
        void partialRecall() { // GH-90000
            // recalled: {a, b, c}  expected: {a, b, d}
            // TP=2 FP=1 FN=1 → p=0.667  r=0.667  f1=0.667
            RetrievalQualityService.QualityReport report = runPromise(() -> // GH-90000
                    service.score(AGENT_ID, TENANT_ID, // GH-90000
                            List.of("a", "b", "c"), List.of("a", "b", "d"))); // GH-90000
            assertThat(report.truePositiveCount()).isEqualTo(2); // GH-90000
            assertThat(report.falsePositiveCount()).isEqualTo(1); // GH-90000
            assertThat(report.falseNegativeCount()).isEqualTo(1); // GH-90000
            assertThat(report.precision()).isCloseTo(0.667, within(0.001)); // GH-90000
            assertThat(report.recall()).isCloseTo(0.667, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("meetsThreshold returns true when precision and recall both pass [GH-90000]")
        void meetsThresholdTrue() { // GH-90000
            RetrievalQualityService.QualityReport report = runPromise(() -> // GH-90000
                    service.score(AGENT_ID, TENANT_ID, List.of("a [GH-90000]"), List.of("a [GH-90000]")));
            assertThat(report.meetsThreshold(0.8)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("meetsThreshold returns false when precision is below threshold [GH-90000]")
        void meetsThresholdFalse() { // GH-90000
            // recalled {a, b}  expected {a} → TP=1  FP=1 → p=0.5 r=1.0
            RetrievalQualityService.QualityReport report = runPromise(() -> // GH-90000
                    service.score(AGENT_ID, TENANT_ID, List.of("a", "b"), List.of("a [GH-90000]")));
            assertThat(report.meetsThreshold(0.8)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("rollingAverage returns zero summary when no reports recorded [GH-90000]")
        void rollingAverageEmptyHistory() { // GH-90000
            RetrievalQualityService.QualitySummary summary = runPromise(() -> // GH-90000
                    service.rollingAverage(AGENT_ID, TENANT_ID, 10)); // GH-90000
            assertThat(summary.reportsConsidered()).isEqualTo(0); // GH-90000
            assertThat(summary.averageF1Score()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("rollingAverage aggregates recorded reports [GH-90000]")
        void rollingAverageAggregates() { // GH-90000
            runPromise(() -> service.score(AGENT_ID, TENANT_ID, List.of("a [GH-90000]"), List.of("a [GH-90000]")));
            runPromise(() -> service.score(AGENT_ID, TENANT_ID, List.of("x [GH-90000]"), List.of("a [GH-90000]")));
            RetrievalQualityService.QualitySummary summary = runPromise(() -> // GH-90000
                    service.rollingAverage(AGENT_ID, TENANT_ID, 10)); // GH-90000
            assertThat(summary.reportsConsidered()).isEqualTo(2); // GH-90000
            assertThat(summary.averagePrecision()).isCloseTo(0.5, within(0.001)); // GH-90000
        }

        @Test
        @DisplayName("rollingAverage respects window limit [GH-90000]")
        void rollingAverageRespectsWindow() { // GH-90000
            // Record 5 reports
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> service.score(AGENT_ID, TENANT_ID, List.of("a [GH-90000]"), List.of("a [GH-90000]")));
            }
            RetrievalQualityService.QualitySummary summary = runPromise(() -> // GH-90000
                    service.rollingAverage(AGENT_ID, TENANT_ID, 3)); // GH-90000
            assertThat(summary.reportsConsidered()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("constructor rejects historyLimit <= 0 [GH-90000]")
        void constructorInvalidHistoryLimit() { // GH-90000
            assertThatThrownBy(() -> new DefaultRetrievalQualityService(0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("rollingAverage rejects window <= 0 [GH-90000]")
        void rollingAverageInvalidWindow() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    service.rollingAverage(AGENT_ID, TENANT_ID, 0))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─══════════════════════════════════════════════════════════════════════════
    //  AccessDecision and GovernanceEvent value types
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AccessDecision value type [GH-90000]")
    class AccessDecisionTests {

        @Test
        @DisplayName("permit() factory marks decision as permitted [GH-90000]")
        void permitFactoryIsPermitted() { // GH-90000
            MemoryGovernanceService.AccessDecision d =
                    MemoryGovernanceService.AccessDecision.permit("user-1", NS_ID); // GH-90000
            assertThat(d.permitted()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("deny() factory marks decision as not permitted with reason [GH-90000]")
        void denyFactoryHasReason() { // GH-90000
            MemoryGovernanceService.AccessDecision d =
                    MemoryGovernanceService.AccessDecision.deny("user-1", NS_ID, "No access"); // GH-90000
            assertThat(d.permitted()).isFalse(); // GH-90000
            assertThat(d.reason()).isEqualTo("No access [GH-90000]");
        }
    }
}
