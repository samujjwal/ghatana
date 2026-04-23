/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.learning;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureStep;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.domain.LearnedPolicy;
import com.ghatana.yappc.api.repository.LearnedPolicyRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * @doc.purpose Tests PolicyLearningService extraction, confidence filtering, and persistence (9.5.6) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PolicyLearningService")
class PolicyLearningServiceTest extends EventloopTestBase {

    // In-memory repository for tests (no DB required) // GH-90000
    private MapLearnedPolicyRepository repository;
    private PolicyLearningService      service;

    private static final String TENANT_ID = "tenant-test";
    private static final String AGENT_ID  = "requirements-analyst-v2";

    @BeforeEach
    void setUp() { // GH-90000
        repository = new MapLearnedPolicyRepository(); // GH-90000
        service    = new PolicyLearningService(repository); // GH-90000
    }

    // ─── 9.5.3 — High-confidence filtering ───────────────────────────────────

    @Nested
    @DisplayName("High-confidence filtering (9.5.3)")
    class HighConfidenceFiltering {

        @Test
        @DisplayName("procedure at 0.95 confidence → stored as learned policy")
        void highConfidenceProcedureIsPersisted() { // GH-90000
            EnhancedProcedure proc = buildProcedure("proc-1", 0.95, List.of("step-1", "step-2")); // GH-90000

            Integer saved = runSync(service.persistHighConfidence(List.of(proc), TENANT_ID)); // GH-90000

            assertThat(saved).isEqualTo(1); // GH-90000
            List<LearnedPolicy> stored = runSync(repository.findByAgent(TENANT_ID, AGENT_ID)); // GH-90000
            assertThat(stored).hasSize(1); // GH-90000
            assertThat(stored.get(0).getConfidence()).isEqualTo(0.95); // GH-90000
        }

        @Test
        @DisplayName("procedure below threshold (0.85) → NOT stored")
        void lowConfidenceProcedureIsSkipped() { // GH-90000
            EnhancedProcedure proc = buildProcedure("proc-low", 0.85, List.of("step-1"));

            Integer saved = runSync(service.persistHighConfidence(List.of(proc), TENANT_ID)); // GH-90000

            assertThat(saved).isEqualTo(0); // GH-90000
            List<LearnedPolicy> stored = runSync(repository.findByAgent(TENANT_ID, AGENT_ID)); // GH-90000
            assertThat(stored).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("mixed list: only above-threshold procedures are saved")
        void mixedConfidenceListFilteredCorrectly() { // GH-90000
            List<EnhancedProcedure> procs = List.of( // GH-90000
                    buildProcedure("proc-high-1", 0.95, List.of("a")),
                    buildProcedure("proc-low-1",  0.80, List.of("b")),
                    buildProcedure("proc-high-2", 0.92, List.of("c", "d")), // GH-90000
                    buildProcedure("proc-low-2",  0.50, List.of("e"))
            );

            Integer saved = runSync(service.persistHighConfidence(procs, TENANT_ID)); // GH-90000

            assertThat(saved).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("empty input → returns 0 without error")
        void emptyInputReturnsZero() { // GH-90000
            Integer saved = runSync(service.persistHighConfidence(List.of(), TENANT_ID)); // GH-90000

            assertThat(saved).isEqualTo(0); // GH-90000
        }
    }

    // ─── 9.5.6 — Full flow: high-confidence turn → stored → queried ──────────

    @Nested
    @DisplayName("9.5.6 Full flow: high-confidence turn → policy active → queryable")
    class FullFlow {

        @Test
        @DisplayName("high-confidence turn → policy stored → getPoliciesForAgent returns it")
        void policyStoredAndQueryable() { // GH-90000
            // GIVEN: A high-confidence procedure (simulates a successful agent turn at reflect phase) // GH-90000
            EnhancedProcedure highConfidenceResult = EnhancedProcedure.builder() // GH-90000
                    .id("turn-episode-001")
                    .agentId(AGENT_ID) // GH-90000
                    .situation("Extract functional requirements from user story")
                    .action("Analyze user story → identify actors → list acceptance criteria")
                    .confidence(0.94) // GH-90000
                    .version(1) // GH-90000
                    .steps(List.of( // GH-90000
                            ProcedureStep.builder().ordinal(1).description("Parse user story text").build(),
                            ProcedureStep.builder().ordinal(2).description("Identify actors").build(),
                            ProcedureStep.builder().ordinal(3).description("List acceptance criteria").build()
                    ))
                    .provenance(Provenance.builder().source("agent_reflection").build())
                    .build(); // GH-90000

            // WHEN: PolicyLearningService processes the turn result
            Integer saved = runSync(service.persistHighConfidence( // GH-90000
                    List.of(highConfidenceResult), TENANT_ID)); // GH-90000

            // THEN: policy is stored
            assertThat(saved).isEqualTo(1); // GH-90000

            // AND: retrievable via getPoliciesForAgent (used by GET /api/v1/agents/{id}/policies) // GH-90000
            List<LearnedPolicy> policies = runSync( // GH-90000
                    service.getPoliciesForAgent(TENANT_ID, AGENT_ID)); // GH-90000

            assertThat(policies).hasSize(1); // GH-90000
            LearnedPolicy stored = policies.get(0); // GH-90000
            assertThat(stored.getAgentId()).isEqualTo(AGENT_ID); // GH-90000
            assertThat(stored.getTenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(stored.getConfidence()).isEqualTo(0.94); // GH-90000
            assertThat(stored.getProcedure()).contains("Extract functional requirements");
            assertThat(stored.getProcedure()).contains("steps");
        }

        @Test
        @DisplayName("getHighConfidencePolicies returns above-threshold policies only")
        void highConfidenceQueryFiltersCorrectly() { // GH-90000
            // Store one high, one medium confidence policy
            EnhancedProcedure highProc = buildProcedure("proc-very-high", 0.95, List.of("a", "b")); // GH-90000
            EnhancedProcedure medProc  = buildProcedure("proc-medium",    0.91, List.of("c"));  // just above default threshold

            runSync(service.persistHighConfidence(List.of(highProc, medProc), TENANT_ID)); // GH-90000

            // Query with higher threshold
            List<LearnedPolicy> abovePoint93 = runSync( // GH-90000
                    service.getHighConfidencePolicies(TENANT_ID, 0.93)); // GH-90000

            assertThat(abovePoint93).hasSize(1); // GH-90000
            assertThat(abovePoint93.get(0).getConfidence()).isGreaterThanOrEqualTo(0.93); // GH-90000
        }

        @Test
        @DisplayName("learned policy contains serialized procedure steps")
        void procedureStepsAreSerialized() { // GH-90000
            EnhancedProcedure proc = buildProcedure("proc-steps", 0.92, List.of("step-A", "step-B", "step-C")); // GH-90000

            runSync(service.persistHighConfidence(List.of(proc), TENANT_ID)); // GH-90000

            List<LearnedPolicy> policies = runSync(service.getPoliciesForAgent(TENANT_ID, AGENT_ID)); // GH-90000
            assertThat(policies).hasSize(1); // GH-90000

            String procedureJson = policies.get(0).getProcedure(); // GH-90000
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
    void tenantIsolation() { // GH-90000
        EnhancedProcedure proc = buildProcedure("proc-isolated", 0.95, List.of("x"));

        runSync(service.persistHighConfidence(List.of(proc), "tenant-A")); // GH-90000

        List<LearnedPolicy> tenantB = runSync(service.getPoliciesForAgent("tenant-B", AGENT_ID)); // GH-90000
        assertThat(tenantB).isEmpty(); // GH-90000
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static EnhancedProcedure buildProcedure(String id, double confidence, // GH-90000
                                                      List<String> stepActions) {
        List<ProcedureStep> steps = stepActions.stream() // GH-90000
                .map(action -> ProcedureStep.builder() // GH-90000
                        .ordinal(1) // GH-90000
                        .description(action) // GH-90000
                        .build()) // GH-90000
                .collect(Collectors.toList()); // GH-90000
        return EnhancedProcedure.builder() // GH-90000
                .id(id) // GH-90000
                .agentId(AGENT_ID) // GH-90000
                .situation("Test situation for " + id) // GH-90000
                .action("Test action for " + id) // GH-90000
                .confidence(confidence) // GH-90000
                .steps(steps) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000
    }

    /** Runs a promise synchronously using the managed event loop. */
    private <T> T runSync(Promise<T> promise) { // GH-90000
        return runPromise(() -> promise); // GH-90000
    }

    // ─── In-memory LearnedPolicyRepository ───────────────────────────────────

    /**
     * Simple ConcurrentHashMap-backed repository for tests. No DB required.
     */
    private static class MapLearnedPolicyRepository implements LearnedPolicyRepository {

        private final ConcurrentHashMap<String, LearnedPolicy> store = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public Promise<LearnedPolicy> save(LearnedPolicy policy) { // GH-90000
            store.put(policy.getId(), policy); // GH-90000
            return Promise.of(policy); // GH-90000
        }

        @Override
        public Promise<Optional<LearnedPolicy>> findById(String id) { // GH-90000
            return Promise.of(Optional.ofNullable(store.get(id))); // GH-90000
        }

        @Override
        public Promise<List<LearnedPolicy>> findByAgent(String tenantId, String agentId) { // GH-90000
            List<LearnedPolicy> result = store.values().stream() // GH-90000
                    .filter(p -> tenantId.equals(p.getTenantId()) // GH-90000
                              && agentId.equals(p.getAgentId())) // GH-90000
                    .sorted(Comparator.comparing(LearnedPolicy::getCreatedAt, // GH-90000
                            Comparator.nullsLast(Comparator.reverseOrder()))) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
            return Promise.of(result); // GH-90000
        }

        @Override
        public Promise<List<LearnedPolicy>> findAboveConfidence(String tenantId, double minConfidence) { // GH-90000
            List<LearnedPolicy> result = store.values().stream() // GH-90000
                    .filter(p -> tenantId.equals(p.getTenantId()) // GH-90000
                              && p.getConfidence() >= minConfidence) // GH-90000
                    .sorted(Comparator.comparingDouble(LearnedPolicy::getConfidence).reversed()) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
            return Promise.of(result); // GH-90000
        }

        @Override
        public Promise<Boolean> deleteById(String id) { // GH-90000
            return Promise.of(store.remove(id) != null); // GH-90000
        }

        @Override
        public Promise<List<LearnedPolicy>> findByTenantId(String tenantId) { // GH-90000
            List<LearnedPolicy> result = store.values().stream() // GH-90000
                    .filter(p -> tenantId.equals(p.getTenantId())) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
            return Promise.of(result); // GH-90000
        }

        @Override
        public Promise<List<LearnedPolicy>> findByTenantIdAndAgentType(String tenantId, String agentType) { // GH-90000
            List<LearnedPolicy> result = store.values().stream() // GH-90000
                    .filter(p -> tenantId.equals(p.getTenantId())) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
            return Promise.of(result); // GH-90000
        }

        @Override
        public Promise<List<LearnedPolicy>> findByTenantIdAndConfidenceGreaterThan(String tenantId, double minConfidence) { // GH-90000
            return findAboveConfidence(tenantId, minConfidence); // GH-90000
        }
    }
}
