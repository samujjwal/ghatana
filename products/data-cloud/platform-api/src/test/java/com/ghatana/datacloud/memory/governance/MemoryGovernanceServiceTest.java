/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("MemoryGovernanceService and RetrievalQualityService")
@ExtendWith(MockitoExtension.class)
class MemoryGovernanceServiceTest extends EventloopTestBase {

    private static final String AGENT_ID   = "agent-gov-001";
    private static final String TENANT_ID  = "tenant-gov-test";
    private static final String NS_ID      = "ns-gov-001";
    private static final Instant NOW       = Instant.parse("2026-04-01T12:00:00Z");

    // ─══════════════════════════════════════════════════════════════════════════
    //  DefaultMemoryGovernanceService
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultMemoryGovernanceService")
    class GovernanceServiceTests {

        @Mock
        private MemoryNamespaceRepository namespaceRepository;

        @Mock
        private MemoryService memoryService;

        private DefaultMemoryGovernanceService service;

        @BeforeEach
        void setUp() {
            service = new DefaultMemoryGovernanceService(namespaceRepository, memoryService);
        }

        private MemoryNamespace namespace(Instant now) {
            return new MemoryNamespace(NS_ID, TENANT_ID, AGENT_ID, MemoryScope.EPISODIC,
                    "Test NS", null, 30, false, 500, now, now, Map.of());
        }

        // ── constructor ───────────────────────────────────────────────────────

        @Nested
        @DisplayName("constructor")
        class Constructor {

            @Test
            @DisplayName("rejects null namespaceRepository")
            void rejectsNullRepo() {
                assertThatThrownBy(() -> new DefaultMemoryGovernanceService(null, memoryService))
                        .isInstanceOf(NullPointerException.class);
            }

            @Test
            @DisplayName("rejects null memoryService")
            void rejectsNullMemoryService() {
                assertThatThrownBy(() -> new DefaultMemoryGovernanceService(namespaceRepository, null))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        // ── enforceRetention ──────────────────────────────────────────────────

        @Nested
        @DisplayName("enforceRetention")
        class EnforceRetention {

            @Test
            @DisplayName("returns result with matching agentId")
            void returnsResultWithAgentId() {
                when(namespaceRepository.findByAgent(AGENT_ID, TENANT_ID))
                        .thenReturn(Promise.of(List.of()));
                MemoryGovernanceService.RetentionEnforcementResult result =
                        runPromise(() -> service.enforceRetention(AGENT_ID, TENANT_ID));
                assertThat(result.agentId()).isEqualTo(AGENT_ID);
            }

            @Test
            @DisplayName("returns zero eviction for namespace with no retention policy")
            void zeroEvictionForNoRetentionPolicy() {
                MemoryNamespace ns = new MemoryNamespace(NS_ID, TENANT_ID, AGENT_ID,
                        MemoryScope.EPISODIC, "NS", null, null /* no retention */,
                        false, 500, NOW, NOW, Map.of());
                when(namespaceRepository.findByAgent(AGENT_ID, TENANT_ID))
                        .thenReturn(Promise.of(List.of(ns)));
                MemoryGovernanceService.RetentionEnforcementResult r =
                        runPromise(() -> service.enforceRetention(AGENT_ID, TENANT_ID));
                assertThat(r.entriesEvicted()).isZero();
            }

            @Test
            @DisplayName("null agentId throws NullPointerException")
            void nullAgentIdThrows() {
                assertThatThrownBy(() -> runPromise(() -> service.enforceRetention(null, TENANT_ID)))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        // ── evaluateAccess ────────────────────────────────────────────────────

        @Nested
        @DisplayName("evaluateAccess")
        class EvaluateAccess {

            @Test
            @DisplayName("permits when namespace found and tenant matches")
            void permitsOnMatch() {
                when(namespaceRepository.findById(NS_ID))
                        .thenReturn(Promise.of(Optional.of(namespace(NOW))));
                MemoryGovernanceService.AccessDecision d =
                        runPromise(() -> service.evaluateAccess(NS_ID, "principal-1", TENANT_ID));
                assertThat(d.permitted()).isTrue();
            }

            @Test
            @DisplayName("denies when namespace not found")
            void deniesWhenNotFound() {
                when(namespaceRepository.findById(NS_ID))
                        .thenReturn(Promise.of(Optional.empty()));
                MemoryGovernanceService.AccessDecision d =
                        runPromise(() -> service.evaluateAccess(NS_ID, "principal-1", TENANT_ID));
                assertThat(d.permitted()).isFalse();
                assertThat(d.reason()).containsIgnoringCase("not found");
            }

            @Test
            @DisplayName("denies when tenant does not match")
            void deniesOnTenantMismatch() {
                when(namespaceRepository.findById(NS_ID))
                        .thenReturn(Promise.of(Optional.of(namespace(NOW))));
                MemoryGovernanceService.AccessDecision d =
                        runPromise(() -> service.evaluateAccess(NS_ID, "principal-1", "other-tenant"));
                assertThat(d.permitted()).isFalse();
                assertThat(d.reason()).containsIgnoringCase("tenant");
            }
        }

        // ── setRetentionPolicy ────────────────────────────────────────────────

        @Nested
        @DisplayName("setRetentionPolicy")
        class SetRetentionPolicy {

            @Test
            @DisplayName("saves updated namespace with new retentionDays")
            void savesWithNewRetentionDays() {
                MemoryNamespace ns = namespace(NOW);
                when(namespaceRepository.findById(NS_ID))
                        .thenReturn(Promise.of(Optional.of(ns)));
                when(namespaceRepository.save(any()))
                        .thenAnswer(inv -> Promise.of(inv.getArgument(0)));
                MemoryNamespace updated =
                        runPromise(() -> service.setRetentionPolicy(NS_ID, 90, TENANT_ID));
                assertThat(updated.retentionDays()).isEqualTo(90);
                verify(namespaceRepository).save(argThat(n -> n.retentionDays() == 90));
            }

            @Test
            @DisplayName("fails with IllegalArgumentException when namespace not found")
            void failsWhenNotFound() {
                when(namespaceRepository.findById(NS_ID))
                        .thenReturn(Promise.of(Optional.empty()));
                assertThatThrownBy(() -> runPromise(() ->
                        service.setRetentionPolicy(NS_ID, 30, TENANT_ID)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("not found");
            }
        }

        // ── auditLog ──────────────────────────────────────────────────────────

        @Nested
        @DisplayName("auditLog")
        class AuditLog {

            @Test
            @DisplayName("returns empty list (baseline implementation)")
            void returnsEmptyList() {
                List<MemoryGovernanceService.GovernanceEvent> events =
                        runPromise(() -> service.auditLog(NS_ID, TENANT_ID, NOW));
                assertThat(events).isEmpty();
            }
        }
    }

    // ─══════════════════════════════════════════════════════════════════════════
    //  DefaultRetrievalQualityService
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DefaultRetrievalQualityService")
    class RetrievalQualityServiceTests {

        private DefaultRetrievalQualityService service;

        @BeforeEach
        void setUp() {
            service = new DefaultRetrievalQualityService();
        }

        @Test
        @DisplayName("perfect recall: precision=1.0, recall=1.0, f1=1.0")
        void perfectRecall() {
            RetrievalQualityService.QualityReport report = runPromise(() ->
                    service.score(AGENT_ID, TENANT_ID, List.of("a", "b"), List.of("a", "b")));
            assertThat(report.precision()).isEqualTo(1.0);
            assertThat(report.recall()).isEqualTo(1.0);
            assertThat(report.f1Score()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("zero overlap: precision=0, recall=0, f1=0")
        void zeroOverlap() {
            RetrievalQualityService.QualityReport report = runPromise(() ->
                    service.score(AGENT_ID, TENANT_ID, List.of("x"), List.of("a", "b")));
            assertThat(report.precision()).isZero();
            assertThat(report.recall()).isZero();
            assertThat(report.f1Score()).isZero();
        }

        @Test
        @DisplayName("partial recall: computes correct precision, recall, f1")
        void partialRecall() {
            // recalled: {a, b, c}  expected: {a, b, d}
            // TP=2 FP=1 FN=1 → p=0.667  r=0.667  f1=0.667
            RetrievalQualityService.QualityReport report = runPromise(() ->
                    service.score(AGENT_ID, TENANT_ID,
                            List.of("a", "b", "c"), List.of("a", "b", "d")));
            assertThat(report.truePositiveCount()).isEqualTo(2);
            assertThat(report.falsePositiveCount()).isEqualTo(1);
            assertThat(report.falseNegativeCount()).isEqualTo(1);
            assertThat(report.precision()).isCloseTo(0.667, within(0.001));
            assertThat(report.recall()).isCloseTo(0.667, within(0.001));
        }

        @Test
        @DisplayName("meetsThreshold returns true when precision and recall both pass")
        void meetsThresholdTrue() {
            RetrievalQualityService.QualityReport report = runPromise(() ->
                    service.score(AGENT_ID, TENANT_ID, List.of("a"), List.of("a")));
            assertThat(report.meetsThreshold(0.8)).isTrue();
        }

        @Test
        @DisplayName("meetsThreshold returns false when precision is below threshold")
        void meetsThresholdFalse() {
            // recalled {a, b}  expected {a} → TP=1  FP=1 → p=0.5 r=1.0
            RetrievalQualityService.QualityReport report = runPromise(() ->
                    service.score(AGENT_ID, TENANT_ID, List.of("a", "b"), List.of("a")));
            assertThat(report.meetsThreshold(0.8)).isFalse();
        }

        @Test
        @DisplayName("rollingAverage returns zero summary when no reports recorded")
        void rollingAverageEmptyHistory() {
            RetrievalQualityService.QualitySummary summary = runPromise(() ->
                    service.rollingAverage(AGENT_ID, TENANT_ID, 10));
            assertThat(summary.reportsConsidered()).isEqualTo(0);
            assertThat(summary.averageF1Score()).isZero();
        }

        @Test
        @DisplayName("rollingAverage aggregates recorded reports")
        void rollingAverageAggregates() {
            runPromise(() -> service.score(AGENT_ID, TENANT_ID, List.of("a"), List.of("a")));
            runPromise(() -> service.score(AGENT_ID, TENANT_ID, List.of("x"), List.of("a")));
            RetrievalQualityService.QualitySummary summary = runPromise(() ->
                    service.rollingAverage(AGENT_ID, TENANT_ID, 10));
            assertThat(summary.reportsConsidered()).isEqualTo(2);
            assertThat(summary.averagePrecision()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("rollingAverage respects window limit")
        void rollingAverageRespectsWindow() {
            // Record 5 reports
            for (int i = 0; i < 5; i++) {
                runPromise(() -> service.score(AGENT_ID, TENANT_ID, List.of("a"), List.of("a")));
            }
            RetrievalQualityService.QualitySummary summary = runPromise(() ->
                    service.rollingAverage(AGENT_ID, TENANT_ID, 3));
            assertThat(summary.reportsConsidered()).isEqualTo(3);
        }

        @Test
        @DisplayName("constructor rejects historyLimit <= 0")
        void constructorInvalidHistoryLimit() {
            assertThatThrownBy(() -> new DefaultRetrievalQualityService(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rollingAverage rejects window <= 0")
        void rollingAverageInvalidWindow() {
            assertThatThrownBy(() -> runPromise(() ->
                    service.rollingAverage(AGENT_ID, TENANT_ID, 0)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─══════════════════════════════════════════════════════════════════════════
    //  AccessDecision and GovernanceEvent value types
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AccessDecision value type")
    class AccessDecisionTests {

        @Test
        @DisplayName("permit() factory marks decision as permitted")
        void permitFactoryIsPermitted() {
            MemoryGovernanceService.AccessDecision d =
                    MemoryGovernanceService.AccessDecision.permit("user-1", NS_ID);
            assertThat(d.permitted()).isTrue();
        }

        @Test
        @DisplayName("deny() factory marks decision as not permitted with reason")
        void denyFactoryHasReason() {
            MemoryGovernanceService.AccessDecision d =
                    MemoryGovernanceService.AccessDecision.deny("user-1", NS_ID, "No access");
            assertThat(d.permitted()).isFalse();
            assertThat(d.reason()).isEqualTo("No access");
        }
    }
}
