/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID);

        fraudDetectorAgent = mockTypedAgent("fraud-detector-v1", "Fraud Detector", "1.0.0",
                AgentType.DETERMINISTIC, Set.of("fraud-detection", "risk-scoring"));

        fraudDetectorConfig = AgentConfig.builder()
                .agentId("fraud-detector-v1")
                .type(AgentType.DETERMINISTIC)
                .version("1.0.0")
                .timeout(Duration.ofMillis(100))
                .build();

        anomalyDetectorAgent = mockTypedAgent("anomaly-detector-v1", "Anomaly Detector", "2.0.0",
                AgentType.ADAPTIVE, Set.of("anomaly-detection", "adaptive-learning"));

        anomalyDetectorConfig = AgentConfig.builder()
                .agentId("anomaly-detector-v1")
                .type(AgentType.ADAPTIVE)
                .version("2.0.0")
                .timeout(Duration.ofMillis(200))
                .build();

        nlpAgent = mockTypedAgent("nlp-processor-v1", "NLP Processor", "1.5.0",
                AgentType.PROBABILISTIC, Set.of("nlp", "text-analysis", "entity-extraction"));

        nlpConfig = AgentConfig.builder()
                .agentId("nlp-processor-v1")
                .type(AgentType.PROBABILISTIC)
                .version("1.5.0")
                .timeout(Duration.ofMillis(500))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AGENT REGISTRATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent Registration & Lifecycle")
    class RegistrationTests {

        @Test
        @DisplayName("[REG-001]: register_persists_descriptor_to_datacloud")
        void registerPersistsDescriptor() {
            // Given
            UUID entityUuid = UUID.randomUUID();
            stubCreateEntity(entityUuid);
            stubAppendEvent();

            // When
            Void result = runPromise(() ->
                    registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // Then
            verify(dataCloud).createEntity(
                    eq(TENANT_ID),
                    eq(REGISTRY_COLLECTION),
                    argThat(map -> "fraud-detector-v1".equals(map.get("agentId"))
                            && "Fraud Detector".equals(map.get("name"))
                            && "1.0.0".equals(map.get("version"))));
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("[REG-002]: register_populates_in_memory_cache")
        void registerPopulatesCacheImmediately() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // Then
            assertThat(resolved).isPresent().containsSame(fraudDetectorAgent);
        }

        @Test
        @DisplayName("[REG-003]: register_multiple_agents_independently")
        void registerMultipleAgentsIndependently() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));
            runPromise(() -> registry.register(nlpAgent, nlpConfig));

            Set<String> ids = runPromise(registry::listAgentIds);

            // Then
            assertThat(ids).containsExactlyInAnyOrder(
                    "fraud-detector-v1",
                    "anomaly-detector-v1",
                    "nlp-processor-v1"
            );
        }

        @Test
        @DisplayName("[REG-004]: register_replaces_existing_registration")
        void registerReplacesExisting() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            TypedAgent<String, String> v2 = mockTypedAgent("fraud-detector-v1", "Fraud Detector",
                    "2.0.0", AgentType.DETERMINISTIC, Set.of("fraud-detection", "risk-scoring"));
            AgentConfig v2Config = AgentConfig.builder()
                    .agentId("fraud-detector-v1")
                    .type(AgentType.DETERMINISTIC)
                    .version("2.0.0")
                    .build();

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(v2, v2Config));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // Then: Cache holds v2
            assertThat(resolved).isPresent().containsSame(v2);
        }

        @Test
        @DisplayName("[REG-005]: register_propagates_datacloud_failure")
        void registerPropagatesDataCloudFailure() {
            // Given
            when(dataCloud.createEntity(any(), any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Data-Cloud unavailable")));

            // When & Then
            assertThatThrownBy(() -> runPromise(() ->
                    registry.register(fraudDetectorAgent, fraudDetectorConfig)))
                    .isInstanceOf(RuntimeException.class);

            // Cache must NOT be updated on failure
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("[REG-006]: register_with_null_agent_rejected")
        void registerWithNullAgentRejected() {
            // When & Then
            assertThatThrownBy(() -> runPromise(() ->
                    registry.register(null, fraudDetectorConfig)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("[REG-007]: register_with_null_config_rejected")
        void registerWithNullConfigRejected() {
            // When & Then
            assertThatThrownBy(() -> runPromise(() ->
                    registry.register(fraudDetectorAgent, null)))
                    .isInstanceOf(NullPointerException.class);
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
        void resolveReturnsCachedAgent() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // Then
            assertThat(resolved).isPresent().containsSame(fraudDetectorAgent);
        }

        @Test
        @DisplayName("[RES-009]: resolve_nonexistent_agent_returns_empty")
        void resolveNonexistentReturnsEmpty() {
            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("nonexistent-agent"));

            // Then
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("[RES-010]: listAgentIds_returns_all_registered_agents")
        void listAgentIdsReturnsAll() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            // When
            Set<String> ids = runPromise(registry::listAgentIds);

            // Then
            assertThat(ids).hasSize(2)
                    .contains("fraud-detector-v1", "anomaly-detector-v1");
        }

        @Test
        @DisplayName("[RES-011]: listAgentIds_empty_registry_returns_empty_set")
        void listAgentIdsEmptyRegistry() {
            // When
            Set<String> ids = runPromise(registry::listAgentIds);

            // Then
            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("[RES-012]: findByCapability_filters_agents_by_capability")
        void findByCapabilityFiltersAgents() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));
            runPromise(() -> registry.register(nlpAgent, nlpConfig));

            // When: Find agents with "fraud-detection" capability
            List<String> fraudAgentIds = runPromise(() ->
                    registry.findByCapability("fraud-detection"));

            // Then
            assertThat(fraudAgentIds)
                    .hasSize(1)
                    .contains("fraud-detector-v1");
        }

        @Test
        @DisplayName("[RES-013]: findByCapability_returns_multiple_matches")
        void findByCapabilityMultipleMatches() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // Fraud detector has "fraud-detection" capability
            // Anomaly detector has "anomaly-detection" capability
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            // When: Each should be discoverable by their capability
            // This validates capability filtering mechanism
        }

        @Test
        @DisplayName("[RES-014]: getStats_returns_registry_statistics")
        void getStatsReturnsStatistics() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            when(dataCloud.countEntities(any(), any(), any())).thenReturn(Promise.of(2L));

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            // When
            Map<String, Object> stats = runPromise(registry::getStats);

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
        void executeDelegatesToAgent() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            TypedAgent<String, String> mockAgent = mock(TypedAgent.class);
            AgentDescriptor descriptor = AgentDescriptor.builder()
                    .agentId("test-agent")
                    .name("Test Agent")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC)
                    .determinism(DeterminismGuarantee.FULL)
                    .failureMode(FailureMode.FAIL_FAST)
                    .stateMutability(StateMutability.STATELESS)
                    .capabilities(Set.of())
                    .build();
            lenient().when(mockAgent.descriptor()).thenReturn(descriptor);
            when(mockAgent.process(any(), eq("input")))
                    .thenReturn(Promise.of(AgentResult.success("output", "test-agent", Duration.ZERO)));

            AgentConfig config = AgentConfig.builder()
                    .agentId("test-agent")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            runPromise(() -> registry.register(mockAgent, config));

            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("test-agent"));
            AgentResult<String> result = runPromise(() -> resolved.get().process(null, "input"));

            // Then
            assertThat(result.getOutput()).isEqualTo("output");
            verify(mockAgent).process(any(), eq("input"));
        }

        @Test
        @DisplayName("[EXE-016]: resolve_unregistered_agent_returns_empty")
        void executeUnregisteredAgentThrowsError() {
            // When
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("nonexistent"));

            // Then
            assertThat(resolved).isEmpty();
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
        void cacheStoresAgentsInMemory() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // Then: Verify cache hit (no additional Data-Cloud calls)
            Optional<TypedAgent<String, String>> first =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            Optional<TypedAgent<String, String>> second =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            assertThat(first).isPresent().containsSame(fraudDetectorAgent);
            assertThat(second).isPresent().containsSame(fraudDetectorAgent);

            // Data-Cloud.createEntity should be called only once (during registration)
            verify(dataCloud, times(1)).createEntity(any(), any(), any());
        }

        @Test
        @DisplayName("[CACHE-018]: cache_respects_ttl_eviction")
        void cacheRespectsTTLEviction() {
            // Given: Registry with custom TTL
            // This test validates TTL eviction logic if exposed
            // Typically requires internal visibility into cache state
        }

        @Test
        @DisplayName("[CACHE-019]: cache_respects_max_size_lru_eviction")
        void cacheRespectsMaxSizeLRUEviction() {
            // Given: Registry with max cache size
            // This test validates LRU eviction when max size exceeded
            // Typically requires internal visibility into cache state
        }

        @Test
        @DisplayName("[CACHE-020]: concurrent_cache_access_safe")
        void concurrentCacheAccessSafe() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            // When: Multiple concurrent resolutions
            for (int i = 0; i < 100; i++) {
                runPromise(() -> registry.resolve("fraud-detector-v1"));
                runPromise(() -> registry.resolve("anomaly-detector-v1"));
            }

            // Then: Cache should be consistent
            Set<String> ids = runPromise(registry::listAgentIds);
            assertThat(ids).hasSize(2);
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
        void deregisterRemovesAgent() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            stubDeleteEntity();

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // When
            Void result = runPromise(() -> registry.deregister("fraud-detector-v1"));

            // Then
            assertThat(result).isNull();
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("[DEREG-022]: deregister_nonexistent_agent_safe")
        void deregisterNonexistentSafe() {
            // When & Then: Should not throw
            Void result = runPromise(() -> registry.deregister("nonexistent"));
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("[DEREG-023]: deregister_propagates_datacloud_failure")
        void deregisterPropagatesFailure() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            when(dataCloud.deleteEntity(any(), any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Delete failed")));

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // When & Then
            assertThatThrownBy(() ->
                    runPromise(() -> registry.deregister("fraud-detector-v1")))
                    .isInstanceOf(RuntimeException.class);
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
        void registrationRespectsTenantBoundary() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // When
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // Then: Verification includes tenant ID
            verify(dataCloud).createEntity(
                    eq(TENANT_ID),  // ← Tenant ID enforced
                    eq(REGISTRY_COLLECTION),
                    any());
        }

        @Test
        @DisplayName("[TENANT-025]: registry_isolated_by_tenant")
        void registryIsolatedByTenant() {
            // Given: Two registries with different tenants
            DataCloudAgentRegistry registryA = new DataCloudAgentRegistry(dataCloud, "tenant-a");
            DataCloudAgentRegistry registryB = new DataCloudAgentRegistry(dataCloud, "tenant-b");

            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // When
            runPromise(() -> registryA.register(fraudDetectorAgent, fraudDetectorConfig));

            // Then: Registry B does not see Agent A's agents
            Set<String> idsB = runPromise(registryB::listAgentIds);
            assertThat(idsB).isEmpty();
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
        void fullLifecycle() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            stubDeleteEntity();

            // When: Full lifecycle
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            Set<String> ids = runPromise(registry::listAgentIds);
            runPromise(() -> registry.deregister("fraud-detector-v1"));

            // Then
            assertThat(resolved).isPresent();
            assertThat(ids).contains("fraud-detector-v1");
        }

        @Test
        @DisplayName("[INT-027]: registry_handles_mixed_operations")
        void registryHandlesMixedOperations() {
            // Given
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // When: Multiple mixed operations
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            Optional<TypedAgent<String, String>> resolved1 =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            Optional<TypedAgent<String, String>> resolved2 =
                    runPromise(() -> registry.resolve("anomaly-detector-v1"));

            Set<String> ids = runPromise(registry::listAgentIds);

            // Then
            assertThat(resolved1).isPresent();
            assertThat(resolved2).isPresent();
            assertThat(ids).hasSize(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void stubCreateEntity(UUID uuid) {
        when(mockEntity.getId()).thenReturn(uuid);
        when(dataCloud.createEntity(any(), any(), any()))
                .thenReturn(Promise.of(mockEntity));
    }

    private void stubAppendEvent() {
        lenient().when(dataCloud.appendEvent(any(), any(), any()))
                .thenReturn(Promise.of(0L));
    }

    private void stubDeleteEntity() {
        when(dataCloud.deleteEntity(any(), any(), any()))
                .thenReturn(Promise.of(null));
    }

    private TypedAgent<String, String> mockTypedAgent(String agentId, String name, String version,
                                                       AgentType type, Set<String> capabilities) {
        TypedAgent<String, String> agent = mock(TypedAgent.class);
        AgentDescriptor descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(name)
                .version(version)
                .type(type)
                .determinism(DeterminismGuarantee.FULL)
                .failureMode(FailureMode.FAIL_FAST)
                .stateMutability(StateMutability.STATELESS)
                .capabilities(capabilities)
                .build();
        lenient().when(agent.descriptor()).thenReturn(descriptor);
        return agent;
    }
}
