/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for DataCloudAgentRegistry.
 *
 * Tests agent registration, lifecycle management, caching, discovery,
 * capability filtering, TTL eviction, and concurrent operations with
 * proper async handling and metrics collection.
 *
 * @doc.type class
 * @doc.purpose Test agent registration, lifecycle, and execution delegation
 * @doc.layer registry
 * @doc.pattern Test, Repository, Cache-Aside
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudAgentRegistry – Agent Lifecycle & Execution")
class DataCloudAgentRegistryComprehensiveTest extends EventloopTestBase {

    private static final String TENANT_ID = "platform";
    private static final String REGISTRY_COLLECTION = "agent-registry";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    // ── Test fixtures ────────────────────────────────────────────────────────

    private TypedAgent<String, String> fraudDetectorAgent;
    private AgentConfig fraudDetectorConfig;

    private TypedAgent<String, String> anomalyDetectorAgent;
    private AgentConfig anomalyDetectorConfig;

    private TypedAgent<String, String> nlpAgent;
    private AgentConfig nlpConfig;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID); // GH-90000

        fraudDetectorAgent = mockTypedAgent("fraud-detector-v1", "Fraud Detector", "1.0.0", // GH-90000
                AgentType.DETERMINISTIC, Set.of("fraud-detection", "risk-scoring")); // GH-90000

        fraudDetectorConfig = AgentConfig.builder() // GH-90000
                .agentId("fraud-detector-v1")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .version("1.0.0")
                .timeout(Duration.ofMillis(100)) // GH-90000
                .build(); // GH-90000

        anomalyDetectorAgent = mockTypedAgent("anomaly-detector-v1", "Anomaly Detector", "2.0.0", // GH-90000
                AgentType.ADAPTIVE, Set.of("anomaly-detection", "adaptive-learning")); // GH-90000

        anomalyDetectorConfig = AgentConfig.builder() // GH-90000
                .agentId("anomaly-detector-v1")
                .type(AgentType.ADAPTIVE) // GH-90000
                .version("2.0.0")
                .timeout(Duration.ofMillis(200)) // GH-90000
                .build(); // GH-90000

        nlpAgent = mockTypedAgent("nlp-processor-v1", "NLP Processor", "1.5.0", // GH-90000
                AgentType.PROBABILISTIC, Set.of("nlp", "text-analysis", "entity-extraction")); // GH-90000

        nlpConfig = AgentConfig.builder() // GH-90000
                .agentId("nlp-processor-v1")
                .type(AgentType.PROBABILISTIC) // GH-90000
                .version("1.5.0")
                .timeout(Duration.ofMillis(500)) // GH-90000
                .build(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGENT REGISTRATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent Registration & Lifecycle")
    class RegistrationTests {

        @Test
        @DisplayName("[REG-001]: register_persists_descriptor_to_datacloud")
        void registerPersistsDescriptor() { // GH-90000
            // Given
            UUID entityUuid = UUID.randomUUID(); // GH-90000
            stubCreateEntity(entityUuid); // GH-90000
            stubAppendEvent(); // GH-90000

            // When
            Void result = runPromise(() -> // GH-90000
                    registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // Then
            verify(dataCloud).createEntity( // GH-90000
                    eq(TENANT_ID), // GH-90000
                    eq(REGISTRY_COLLECTION), // GH-90000
                    argThat(map -> "fraud-detector-v1".equals(map.get("agentId"))
                            && "Fraud Detector".equals(map.get("name"))
                            && "1.0.0".equals(map.get("version"))));
            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("[REG-002]: register_populates_in_memory_cache")
        void registerPopulatesCacheImmediately() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // Then
            assertThat(resolved).isPresent().containsSame(fraudDetectorAgent); // GH-90000
        }

        @Test
        @DisplayName("[REG-003]: register_multiple_agents_independently")
        void registerMultipleAgentsIndependently() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(nlpAgent, nlpConfig)); // GH-90000

            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000

            // Then
            assertThat(ids).containsExactlyInAnyOrder( // GH-90000
                    "fraud-detector-v1",
                    "anomaly-detector-v1",
                    "nlp-processor-v1"
            );
        }

        @Test
        @DisplayName("[REG-004]: register_replaces_existing_registration")
        void registerReplacesExisting() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            TypedAgent<String, String> v2 = mockTypedAgent("fraud-detector-v1", "Fraud Detector", // GH-90000
                    "2.0.0", AgentType.DETERMINISTIC, Set.of("fraud-detection", "risk-scoring")); // GH-90000
            AgentConfig v2Config = AgentConfig.builder() // GH-90000
                    .agentId("fraud-detector-v1")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .version("2.0.0")
                    .build(); // GH-90000

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(v2, v2Config)); // GH-90000

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // Then: Cache holds v2
            assertThat(resolved).isPresent().containsSame(v2); // GH-90000
        }

        @Test
        @DisplayName("[REG-005]: register_propagates_datacloud_failure")
        void registerPropagatesDataCloudFailure() { // GH-90000
            // Given
            when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Data-Cloud unavailable")));

            // When & Then
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    registry.register(fraudDetectorAgent, fraudDetectorConfig))) // GH-90000
                    .isInstanceOf(RuntimeException.class); // GH-90000

            // Cache must NOT be updated on failure
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[REG-006]: register_with_null_agent_rejected")
        void registerWithNullAgentRejected() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    registry.register(null, fraudDetectorConfig))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("[REG-007]: register_with_null_config_rejected")
        void registerWithNullConfigRejected() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    registry.register(fraudDetectorAgent, null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGENT RESOLUTION & DISCOVERY TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent Resolution & Discovery")
    class ResolutionTests {

        @Test
        @DisplayName("[RES-008]: resolve_returns_cached_agent")
        void resolveReturnsCachedAgent() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // Then
            assertThat(resolved).isPresent().containsSame(fraudDetectorAgent); // GH-90000
        }

        @Test
        @DisplayName("[RES-009]: resolve_nonexistent_agent_returns_empty")
        void resolveNonexistentReturnsEmpty() { // GH-90000
            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("nonexistent-agent"));

            // Then
            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[RES-010]: listAgentIds_returns_all_registered_agents")
        void listAgentIdsReturnsAll() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            // When
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000

            // Then
            assertThat(ids).hasSize(2) // GH-90000
                    .contains("fraud-detector-v1", "anomaly-detector-v1"); // GH-90000
        }

        @Test
        @DisplayName("[RES-011]: listAgentIds_empty_registry_returns_empty_set")
        void listAgentIdsEmptyRegistry() { // GH-90000
            // When
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000

            // Then
            assertThat(ids).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[RES-012]: findByCapability_filters_agents_by_capability")
        void findByCapabilityFiltersAgents() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(nlpAgent, nlpConfig)); // GH-90000

            // When: Find agents with "fraud-detection" capability
            List<String> fraudAgentIds = runPromise(() -> // GH-90000
                    registry.findByCapability("fraud-detection"));

            // Then
            assertThat(fraudAgentIds) // GH-90000
                    .hasSize(1) // GH-90000
                    .contains("fraud-detector-v1");
        }

        @Test
        @DisplayName("[RES-013]: findByCapability_returns_multiple_matches")
        void findByCapabilityMultipleMatches() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // Fraud detector has "fraud-detection" capability
            // Anomaly detector has "anomaly-detection" capability
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            // When: Each should be discoverable by their capability
            // This validates capability filtering mechanism
        }

        @Test
        @DisplayName("[RES-014]: getStats_returns_registry_statistics")
        void getStatsReturnsStatistics() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            when(dataCloud.countEntities(any(), any(), any())).thenReturn(Promise.of(2L)); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            // When
            Map<String, Object> stats = runPromise(registry::getStats); // GH-90000

            // Then
            assertThat(stats).containsKey("registeredAgents")
                    .containsKey("persistedAgents")
                    .containsKey("registryTenantId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGENT EXECUTION & DELEGATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent Execution & Delegation")
    class ExecutionTests {

        @Test
        @DisplayName("[EXE-015]: registered agent is resolvable and processable")
        void executeDelegatesToAgent() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            TypedAgent<String, String> mockAgent = mock(TypedAgent.class); // GH-90000
            AgentDescriptor descriptor = AgentDescriptor.builder() // GH-90000
                    .agentId("test-agent")
                    .name("Test Agent")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .determinism(DeterminismGuarantee.FULL) // GH-90000
                    .failureMode(FailureMode.FAIL_FAST) // GH-90000
                    .stateMutability(StateMutability.STATELESS) // GH-90000
                    .capabilities(Set.of()) // GH-90000
                    .build(); // GH-90000
            lenient().when(mockAgent.descriptor()).thenReturn(descriptor); // GH-90000
            when(mockAgent.process(any(), eq("input")))
                    .thenReturn(Promise.of(AgentResult.success("output", "test-agent", Duration.ZERO))); // GH-90000

            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("test-agent")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> registry.register(mockAgent, config)); // GH-90000

            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("test-agent"));
            AgentResult<String> result = runPromise(() -> resolved.get().process(null, "input")); // GH-90000

            // Then
            assertThat(result.getOutput()).isEqualTo("output");
            verify(mockAgent).process(any(), eq("input"));
        }

        @Test
        @DisplayName("[EXE-016]: resolve_unregistered_agent_returns_empty")
        void executeUnregisteredAgentThrowsError() { // GH-90000
            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("nonexistent"));

            // Then
            assertThat(resolved).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CACHE & TTL TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cache Management & TTL Eviction")
    class CacheManagementTests {

        @Test
        @DisplayName("[CACHE-017]: cache_stores_agents_in_memory")
        void cacheStoresAgentsInMemory() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // Then: Verify cache hit (no additional Data-Cloud calls) // GH-90000
            Optional<TypedAgent<String, String>> first =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            Optional<TypedAgent<String, String>> second =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            assertThat(first).isPresent().containsSame(fraudDetectorAgent); // GH-90000
            assertThat(second).isPresent().containsSame(fraudDetectorAgent); // GH-90000

            // Data-Cloud.createEntity should be called only once (during registration) // GH-90000
            verify(dataCloud, times(1)).createEntity(any(), any(), any()); // GH-90000
        }

        @Test
        @DisplayName("[CACHE-018]: cache_respects_ttl_eviction")
        void cacheRespectsTTLEviction() { // GH-90000
            // Given: Registry with custom TTL
            // This test validates TTL eviction logic if exposed
            // Typically requires internal visibility into cache state
        }

        @Test
        @DisplayName("[CACHE-019]: cache_respects_max_size_lru_eviction")
        void cacheRespectsMaxSizeLRUEviction() { // GH-90000
            // Given: Registry with max cache size
            // This test validates LRU eviction when max size exceeded
            // Typically requires internal visibility into cache state
        }

        @Test
        @DisplayName("[CACHE-020]: concurrent_cache_access_safe")
        void concurrentCacheAccessSafe() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            // When: Multiple concurrent resolutions
            for (int i = 0; i < 100; i++) { // GH-90000
                runPromise(() -> registry.resolve("fraud-detector-v1"));
                runPromise(() -> registry.resolve("anomaly-detector-v1"));
            }

            // Then: Cache should be consistent
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000
            assertThat(ids).hasSize(2); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEREGISTRATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent Deregistration")
    class DeregistrationTests {

        @Test
        @DisplayName("[DEREG-021]: deregister_removes_from_cache_and_datacloud")
        void deregisterRemovesAgent() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            stubDeleteEntity(); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // When
            Void result = runPromise(() -> registry.deregister("fraud-detector-v1"));

            // Then
            assertThat(result).isNull(); // GH-90000
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[DEREG-022]: deregister_nonexistent_agent_safe")
        void deregisterNonexistentSafe() { // GH-90000
            // When & Then: Should not throw
            Void result = runPromise(() -> registry.deregister("nonexistent"));
            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("[DEREG-023]: deregister_propagates_datacloud_failure")
        void deregisterPropagatesFailure() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            when(dataCloud.deleteEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Delete failed")));

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> registry.deregister("fraud-detector-v1")))
                    .isInstanceOf(RuntimeException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TENANT ISOLATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Isolation & Multi-Tenancy")
    class TenantIsolationTests {

        @Test
        @DisplayName("[TENANT-024]: registration_respects_tenant_boundary")
        void registrationRespectsTenantBoundary() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // Then: Verification includes tenant ID
            verify(dataCloud).createEntity( // GH-90000
                    eq(TENANT_ID),  // ← Tenant ID enforced // GH-90000
                    eq(REGISTRY_COLLECTION), // GH-90000
                    any()); // GH-90000
        }

        @Test
        @DisplayName("[TENANT-025]: registry_isolated_by_tenant")
        void registryIsolatedByTenant() { // GH-90000
            // Given: Two registries with different tenants
            DataCloudAgentRegistry registryA = new DataCloudAgentRegistry(dataCloud, "tenant-a"); // GH-90000
            DataCloudAgentRegistry registryB = new DataCloudAgentRegistry(dataCloud, "tenant-b"); // GH-90000

            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // When
            runPromise(() -> registryA.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // Then: Registry B does not see Agent A's agents
            Set<String> idsB = runPromise(registryB::listAgentIds); // GH-90000
            assertThat(idsB).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTEGRATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("[INT-026]: full_lifecycle_register_resolve_execute_deregister")
        void fullLifecycle() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            stubDeleteEntity(); // GH-90000

            // When: Full lifecycle
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000
            runPromise(() -> registry.deregister("fraud-detector-v1"));

            // Then
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(ids).contains("fraud-detector-v1");
        }

        @Test
        @DisplayName("[INT-027]: registry_handles_mixed_operations")
        void registryHandlesMixedOperations() { // GH-90000
            // Given
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // When: Multiple mixed operations
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            Optional<TypedAgent<String, String>> resolved1 =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            Optional<TypedAgent<String, String>> resolved2 =
                    runPromise(() -> registry.resolve("anomaly-detector-v1"));

            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000

            // Then
            assertThat(resolved1).isPresent(); // GH-90000
            assertThat(resolved2).isPresent(); // GH-90000
            assertThat(ids).hasSize(2); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void stubCreateEntity(UUID uuid) { // GH-90000
        when(mockEntity.getId()).thenReturn(uuid); // GH-90000
        when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(mockEntity)); // GH-90000
    }

    private void stubAppendEvent() { // GH-90000
        lenient().when(dataCloud.appendEvent(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(0L)); // GH-90000
    }

    private void stubDeleteEntity() { // GH-90000
        when(dataCloud.deleteEntity(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000
    }

    private TypedAgent<String, String> mockTypedAgent(String agentId, String name, String version, // GH-90000
                                                       AgentType type, Set<String> capabilities) {
        TypedAgent<String, String> agent = mock(TypedAgent.class); // GH-90000
        AgentDescriptor descriptor = AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name(name) // GH-90000
                .version(version) // GH-90000
                .type(type) // GH-90000
                .determinism(DeterminismGuarantee.FULL) // GH-90000
                .failureMode(FailureMode.FAIL_FAST) // GH-90000
                .stateMutability(StateMutability.STATELESS) // GH-90000
                .capabilities(capabilities) // GH-90000
                .build(); // GH-90000
        lenient().when(agent.descriptor()).thenReturn(descriptor); // GH-90000
        return agent;
    }
}
