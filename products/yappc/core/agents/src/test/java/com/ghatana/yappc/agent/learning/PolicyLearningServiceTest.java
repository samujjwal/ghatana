/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.learning;

import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureStep;
import com.ghatana.yappc.api.domain.LearnedPolicy;
import com.ghatana.yappc.api.repository.LearnedPolicyRepository;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PolicyLearningService}.
 *
 * <p>Uses an in-memory {@link LearnedPolicyRepository} so no database is required.
 * The test for plan item 9.5.6 verifies the full flow: a high-confidence procedure
 * is extracted and becomes queryable through the repository.
 *
 * @doc.type class
 * @doc.purpose Tests PolicyLearningService extraction, confidence filtering, and persistence (9.5.6)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolicyLearningService")
class PolicyLearningServiceTest {

    // In-memory repository for tests (no DB required)
    private MapLearnedPolicyRepository repository;
    private PolicyLearningService      service;

    private static final String TENANT_ID = "tenant-test";
    private static final String AGENT_ID  = "requirements-analyst-v2";

    @BeforeEach
    void setUp() {
        repository = new MapLearnedPolicyRepository();
        service    = new PolicyLearningService(repository);
    }

    // ─── 9.5.3 — High-confidence filtering ───────────────────────────────────

    @Nested
    @DisplayName("High-confidence filtering (9.5.3)")
    class HighConfidenceFiltering {

        @Test
        @DisplayName("procedure at 0.95 confidence → stored as learned policy")
        void highConfidenceProcedureIsPersisted() {
            EnhancedProcedure proc = buildProcedure("proc-1", 0.95, List.of("step-1", "step-2"));

            Integer saved = runSync(service.persistHighConfidence(List.of(proc), TENANT_ID));

            assertThat(saved).isEqualTo(1);
            List<LearnedPolicy> stored = runSync(repository.findByAgent(TENANT_ID, AGENT_ID));
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).getConfidence()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("procedure below threshold (0.85) → NOT stored")
        void lowConfidenceProcedureIsSkipped() {
            EnhancedProcedure proc = buildProcedure("proc-low", 0.85, List.of("step-1"));

            Integer saved = runSync(service.persistHighConfidence(List.of(proc), TENANT_ID));

            assertThat(saved).isEqualTo(0);
            List<LearnedPolicy> stored = runSync(repository.findByAgent(TENANT_ID, AGENT_ID));
            assertThat(stored).isEmpty();
        }

        @Test
        @DisplayName("mixed list: only above-threshold procedures are saved")
        void mixedConfidenceListFilteredCorrectly() {
            List<EnhancedProcedure> procs = List.of(
                    buildProcedure("proc-high-1", 0.95, List.of("a")),
                    buildProcedure("proc-low-1",  0.80, List.of("b")),
                    buildProcedure("proc-high-2", 0.92, List.of("c", "d")),
                    buildProcedure("proc-low-2",  0.50, List.of("e"))
            );

            Integer saved = runSync(service.persistHighConfidence(procs, TENANT_ID));

            assertThat(saved).isEqualTo(2);
        }

        @Test
        @DisplayName("empty input → returns 0 without error")
        void emptyInputReturnsZero() {
            Integer saved = runSync(service.persistHighConfidence(List.of(), TENANT_ID));

            assertThat(saved).isEqualTo(0);
        }
    }

    // ─── 9.5.6 — Full flow: high-confidence turn → stored → queried ──────────

    @Nested
    @DisplayName("9.5.6 Full flow: high-confidence turn → policy active → queryable")
    class FullFlow {

        @Test
        @DisplayName("high-confidence turn → policy stored → getPoliciesForAgent returns it")
        void policyStoredAndQueryable() {
            // GIVEN: A high-confidence procedure (simulates a successful agent turn at reflect phase)
            EnhancedProcedure highConfidenceResult = EnhancedProcedure.builder()
                    .id("turn-episode-001")
                    .agentId(AGENT_ID)
                    .tenantId(TENANT_ID)
                    .situation("Extract functional requirements from user story")
                    .action("Analyze user story → identify actors → list acceptance criteria")
                    .confidence(0.94)
                    .version(1)
                    .steps(List.of(
                            ProcedureStep.builder().action("Parse user story text").build(),
                            ProcedureStep.builder().action("Identify actors").build(),
                            ProcedureStep.builder().action("List acceptance criteria").build()
                    ))
                    .provenance(Provenance.builder().source("agent_reflection").build())
                    .validity(Validity.builder().confidence(0.94).build())
                    .learnedFromEpisodes(List.of("episode-001"))
                    .build();

            // WHEN: PolicyLearningService processes the turn result
            Integer saved = runSync(service.persistHighConfidence(
                    List.of(highConfidenceResult), TENANT_ID));

            // THEN: policy is stored
            assertThat(saved).isEqualTo(1);

            // AND: retrievable via getPoliciesForAgent (used by GET /api/v1/agents/{id}/policies)
            List<LearnedPolicy> policies = runSync(
                    service.getPoliciesForAgent(TENANT_ID, AGENT_ID));

            assertThat(policies).hasSize(1);
            LearnedPolicy stored = policies.get(0);
            assertThat(stored.getAgentId()).isEqualTo(AGENT_ID);
            assertThat(stored.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(stored.getConfidence()).isEqualTo(0.94);
            assertThat(stored.getProcedure()).contains("Extract functional requirements");
            assertThat(stored.getProcedure()).contains("steps");
        }

        @Test
        @DisplayName("getHighConfidencePolicies returns above-threshold policies only")
        void highConfidenceQueryFiltersCorrectly() {
            // Store one high, one medium confidence policy
            EnhancedProcedure highProc = buildProcedure("proc-very-high", 0.95, List.of("a", "b"));
            EnhancedProcedure medProc  = buildProcedure("proc-medium",    0.91, List.of("c"));  // just above default threshold

            runSync(service.persistHighConfidence(List.of(highProc, medProc), TENANT_ID));

            // Query with higher threshold
            List<LearnedPolicy> abovePoint93 = runSync(
                    service.getHighConfidencePolicies(TENANT_ID, 0.93));

            assertThat(abovePoint93).hasSize(1);
            assertThat(abovePoint93.get(0).getConfidence()).isGreaterThanOrEqualTo(0.93);
        }

        @Test
        @DisplayName("learned policy contains serialized procedure steps")
        void procedureStepsAreSerialized() {
            EnhancedProcedure proc = buildProcedure("proc-steps", 0.92, List.of("step-A", "step-B", "step-C"));

            runSync(service.persistHighConfidence(List.of(proc), TENANT_ID));

            List<LearnedPolicy> policies = runSync(service.getPoliciesForAgent(TENANT_ID, AGENT_ID));
            assertThat(policies).hasSize(1);

            String procedureJson = policies.get(0).getProcedure();
            assertThat(procedureJson).contains("steps");
            // All three step actions must be serialized
            assertThat(procedureJson).contains("step-A");
            assertThat(procedureJson).contains("step-B");
            assertThat(procedureJson).contains("step-C");
        }
    }

    // ─── Tenant isolation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("policies are isolated by tenant — tenant-B cannot see tenant-A policies")
    void tenantIsolation() {
        EnhancedProcedure proc = buildProcedure("proc-isolated", 0.95, List.of("x"));

        runSync(service.persistHighConfidence(List.of(proc), "tenant-A"));

        List<LearnedPolicy> tenantB = runSync(service.getPoliciesForAgent("tenant-B", AGENT_ID));
        assertThat(tenantB).isEmpty();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static EnhancedProcedure buildProcedure(String id, double confidence,
                                                      List<String> stepActions) {
        List<ProcedureStep> steps = stepActions.stream()
                .map(a -> ProcedureStep.builder().action(a).build())
                .collect(Collectors.toList());
        return EnhancedProcedure.builder()
                .id(id)
                .agentId(AGENT_ID)
                .tenantId(TENANT_ID)
                .situation("Test situation for " + id)
                .action("Test action for " + id)
                .confidence(confidence)
                .steps(steps)
                .version(1)
                .provenance(Provenance.builder().source("test_reflection").build())
                .validity(Validity.builder().confidence(confidence).build())
                .build();
    }

    /** Runs a promise synchronously on a fresh eventloop. */
    private static <T> T runSync(Promise<T> promise) {
        Eventloop eventloop = Eventloop.builder().build();
        T[] result = (T[]) new Object[1];
        Throwable[] error = new Throwable[1];
        eventloop.execute(() -> promise.whenComplete((v, e) -> {
            result[0] = v;
            error[0]  = e;
        }));
        eventloop.run();
        if (error[0] != null) throw new RuntimeException(error[0]);
        return result[0];
    }

    // ─── In-memory LearnedPolicyRepository ───────────────────────────────────

    /**
     * Simple ConcurrentHashMap-backed repository for tests. No DB required.
     */
    private static class MapLearnedPolicyRepository implements LearnedPolicyRepository {

        private final ConcurrentHashMap<String, LearnedPolicy> store = new ConcurrentHashMap<>();

        @Override
        public Promise<LearnedPolicy> save(LearnedPolicy policy) {
            store.put(policy.getId(), policy);
            return Promise.of(policy);
        }

        @Override
        public Promise<Optional<LearnedPolicy>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<LearnedPolicy>> findByAgent(String tenantId, String agentId) {
            List<LearnedPolicy> result = store.values().stream()
                    .filter(p -> tenantId.equals(p.getTenantId())
                              && agentId.equals(p.getAgentId()))
                    .sorted(Comparator.comparing(LearnedPolicy::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
            return Promise.of(result);
        }

        @Override
        public Promise<List<LearnedPolicy>> findAboveConfidence(String tenantId, double minConfidence) {
            List<LearnedPolicy> result = store.values().stream()
                    .filter(p -> tenantId.equals(p.getTenantId())
                              && p.getConfidence() >= minConfidence)
                    .sorted(Comparator.comparingDouble(LearnedPolicy::getConfidence).reversed())
                    .collect(Collectors.toList());
            return Promise.of(result);
        }

        @Override
        public Promise<Void> delete(String id) {
            store.remove(id);
            return Promise.of(null);
        }
    }
}
