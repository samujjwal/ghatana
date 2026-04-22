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
 * Unit tests for {@link DataCloudAgentRegistry}.
 *
 * <p>Uses {@link EventloopTestBase} for all async assertions and Mockito for
 * isolating the registry from Data-Cloud I/O.
 *
 * @doc.type class
 * @doc.purpose Test coverage for DataCloudAgentRegistry
 * @doc.layer registry
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudAgentRegistry [GH-90000]")
class DataCloudAgentRegistryTest extends EventloopTestBase {

    private static final String TENANT_ID = "platform";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    // ── Shared test fixtures ─────────────────────────────────────────────────

    private TypedAgent<String, String> fraudDetectorAgent;
    private AgentConfig fraudDetectorConfig;

    private TypedAgent<String, String> anomalyDetectorAgent;
    private AgentConfig anomalyDetectorConfig;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID); // GH-90000

        fraudDetectorAgent = mockTypedAgent("fraud-detector-v1", "Fraud Detector", "1.0.0", // GH-90000
                AgentType.DETERMINISTIC, Set.of("fraud-detection", "risk-scoring")); // GH-90000

        fraudDetectorConfig = AgentConfig.builder() // GH-90000
                .agentId("fraud-detector-v1 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .version("1.0.0 [GH-90000]")
                .timeout(Duration.ofMillis(100)) // GH-90000
                .build(); // GH-90000

        anomalyDetectorAgent = mockTypedAgent("anomaly-detector-v1", "Anomaly Detector", "2.0.0", // GH-90000
                AgentType.ADAPTIVE, Set.of("anomaly-detection", "adaptive-learning")); // GH-90000

        anomalyDetectorConfig = AgentConfig.builder() // GH-90000
                .agentId("anomaly-detector-v1 [GH-90000]")
                .type(AgentType.ADAPTIVE) // GH-90000
                .version("2.0.0 [GH-90000]")
                .timeout(Duration.ofMillis(200)) // GH-90000
                .build(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register() [GH-90000]")
    class Register {

        @Test
        @DisplayName("persists descriptor to Data-Cloud and populates cache [GH-90000]")
        void shouldPersistAndCache() { // GH-90000
            // GIVEN
            UUID entityUuid = UUID.randomUUID(); // GH-90000
            stubCreateEntity(entityUuid); // GH-90000
            stubAppendEvent(); // GH-90000

            // WHEN
            Void result = runPromise(() -> // GH-90000
                    registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // THEN – Data-Cloud save was called with the right collection
            verify(dataCloud).createEntity( // GH-90000
                    eq(TENANT_ID), // GH-90000
                    eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), // GH-90000
                    argThat(map -> "fraud-detector-v1".equals(map.get("agentId [GH-90000]"))
                            && "Fraud Detector".equals(map.get("name [GH-90000]"))
                            && "1.0.0".equals(map.get("version [GH-90000]"))));
            assertThat(result).isNull(); // Promise<Void> resolves to null // GH-90000
        }

        @Test
        @DisplayName("agent can be resolved from cache immediately after registration [GH-90000]")
        void shouldBeResolvableAfterRegistration() { // GH-90000
            // GIVEN
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // WHEN
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1 [GH-90000]"));

            // THEN
            assertThat(resolved).isPresent().containsSame(fraudDetectorAgent); // GH-90000
        }

        @Test
        @DisplayName("registers multiple agents independently [GH-90000]")
        void shouldRegisterMultipleAgents() { // GH-90000
            // GIVEN
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            // WHEN – register two agents
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000

            // THEN
            assertThat(ids).containsExactlyInAnyOrder("fraud-detector-v1", "anomaly-detector-v1"); // GH-90000
        }

        @Test
        @DisplayName("replaces existing registration when same agentId is re-registered [GH-90000]")
        void shouldReplaceExistingRegistration() { // GH-90000
            // GIVEN – two successive registrations with the same ID
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            TypedAgent<String, String> v2 = mockTypedAgent("fraud-detector-v1", "Fraud Detector", // GH-90000
                    "2.0.0", AgentType.DETERMINISTIC, Set.of("fraud-detection [GH-90000]"));
            AgentConfig v2Config = AgentConfig.builder() // GH-90000
                    .agentId("fraud-detector-v1 [GH-90000]").type(AgentType.DETERMINISTIC).version("2.0.0 [GH-90000]").build();

            // WHEN
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(v2, v2Config)); // GH-90000

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1 [GH-90000]"));

            // THEN – cache holds v2
            assertThat(resolved).isPresent().containsSame(v2); // GH-90000
        }

        @Test
        @DisplayName("propagates Data-Cloud failure without corrupting cache [GH-90000]")
        void shouldPropagateDataCloudFailure() { // GH-90000
            // GIVEN
            when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Data-Cloud unavailable [GH-90000]")));

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    registry.register(fraudDetectorAgent, fraudDetectorConfig))) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("Data-Cloud unavailable [GH-90000]");

            // Cache must NOT be updated on failure
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1 [GH-90000]"));
            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("rejects null agent argument [GH-90000]")
        void shouldRejectNullAgent() { // GH-90000
            assertThatThrownBy(() -> registry.register(null, fraudDetectorConfig)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects null config argument [GH-90000]")
        void shouldRejectNullConfig() { // GH-90000
            assertThatThrownBy(() -> registry.register(fraudDetectorAgent, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deregister() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deregister() [GH-90000]")
    class Deregister {

        @Test
        @DisplayName("removes agent from both Data-Cloud and cache [GH-90000]")
        void shouldRemoveFromDataCloudAndCache() { // GH-90000
            // GIVEN
            UUID entityUuid = UUID.randomUUID(); // GH-90000
            stubCreateEntity(entityUuid); // GH-90000
            stubAppendEvent(); // GH-90000
            when(dataCloud.deleteEntity(any(), any(), any())).thenReturn(Promise.complete()); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // WHEN
            runPromise(() -> registry.deregister("fraud-detector-v1 [GH-90000]"));

            // THEN – Data-Cloud delete was called with correct UUID
            verify(dataCloud).deleteEntity(TENANT_ID, DataCloudAgentRegistry.REGISTRY_COLLECTION, entityUuid); // GH-90000

            // Cache is empty
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1 [GH-90000]"));
            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("succeeds silently when agent was never registered [GH-90000]")
        void shouldSucceedSilentlyForUnknownAgent() { // GH-90000
            // No Data-Cloud calls expected when agent is unknown
            Void result = runPromise(() -> registry.deregister("nonexistent-agent [GH-90000]"));
            assertThat(result).isNull(); // GH-90000
            verifyNoInteractions(dataCloud); // GH-90000
        }

        @Test
        @DisplayName("rejects null agentId [GH-90000]")
        void shouldRejectNullAgentId() { // GH-90000
            assertThatThrownBy(() -> registry.deregister(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolve() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve() [GH-90000]")
    class Resolve {

        @Test
        @DisplayName("returns registered agent from cache [GH-90000]")
        void shouldReturnCachedAgent() { // GH-90000
            // GIVEN
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000

            // WHEN
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("fraud-detector-v1 [GH-90000]"));

            // THEN – resolved without any additional Data-Cloud call after registration
            assertThat(result).isPresent().containsSame(fraudDetectorAgent); // GH-90000
        }

        @Test
        @DisplayName("returns empty Optional for unknown agentId [GH-90000]")
        void shouldReturnEmptyForUnknownAgent() { // GH-90000
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("does-not-exist [GH-90000]"));
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty Optional after agent is deregistered [GH-90000]")
        void shouldReturnEmptyAfterDeregister() { // GH-90000
            // GIVEN
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            when(dataCloud.deleteEntity(any(), any(), any())).thenReturn(Promise.complete()); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.deregister("fraud-detector-v1 [GH-90000]"));

            // WHEN
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("fraud-detector-v1 [GH-90000]"));

            // THEN
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("rejects null agentId [GH-90000]")
        void shouldRejectNullAgentId() { // GH-90000
            assertThatThrownBy(() -> registry.resolve(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listAgentIds() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAgentIds() [GH-90000]")
    class ListAgentIds {

        @Test
        @DisplayName("returns empty set when no agents are registered [GH-90000]")
        void shouldReturnEmptySetWhenEmpty() { // GH-90000
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000
            assertThat(ids).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns IDs of all registered agents [GH-90000]")
        void shouldReturnAllRegisteredIds() { // GH-90000
            // GIVEN
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            // WHEN
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000

            // THEN
            assertThat(ids).containsExactlyInAnyOrder("fraud-detector-v1", "anomaly-detector-v1"); // GH-90000
        }

        @Test
        @DisplayName("returned set is a snapshot (immutable) [GH-90000]")
        void shouldReturnImmutableSnapshot() { // GH-90000
            Set<String> ids = runPromise(registry::listAgentIds); // GH-90000
            assertThatThrownBy(() -> ids.add("mutate-attempt [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByCapability() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability() [GH-90000]")
    class FindByCapability {

        @BeforeEach
        void registerBothAgents() { // GH-90000
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000
        }

        @Test
        @DisplayName("returns agents that have the queried capability [GH-90000]")
        void shouldReturnMatchingAgents() { // GH-90000
            List<String> ids = runPromise(() -> registry.findByCapability("fraud-detection [GH-90000]"));
            assertThat(ids).containsExactly("fraud-detector-v1 [GH-90000]");
        }

        @Test
        @DisplayName("returns multiple agents sharing the same capability [GH-90000]")
        void shouldReturnMultipleAgentsWithSharedCapability() { // GH-90000
            // Both agents have 'risk-scoring' — use a fresh agent that also has it
            TypedAgent<String, String> riskAgent = mockTypedAgent("risk-scoring-v1", // GH-90000
                    "Risk Scorer", "1.0.0", AgentType.DETERMINISTIC,
                    Set.of("fraud-detection", "risk-scoring")); // GH-90000
            AgentConfig riskConfig = AgentConfig.builder() // GH-90000
                    .agentId("risk-scoring-v1 [GH-90000]").type(AgentType.DETERMINISTIC).build();

            runPromise(() -> registry.register(riskAgent, riskConfig)); // GH-90000
            List<String> ids = runPromise(() -> registry.findByCapability("fraud-detection [GH-90000]"));

            assertThat(ids).containsExactlyInAnyOrder("fraud-detector-v1", "risk-scoring-v1"); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when no agents match the capability [GH-90000]")
        void shouldReturnEmptyForUnknownCapability() { // GH-90000
            List<String> ids = runPromise(() -> registry.findByCapability("quantum-computing [GH-90000]"));
            assertThat(ids).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("rejects null capability [GH-90000]")
        void shouldRejectNullCapability() { // GH-90000
            assertThatThrownBy(() -> registry.findByCapability(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStats() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStats() [GH-90000]")
    class GetStats {

        @Test
        @DisplayName("returns stats from in-memory cache and Data-Cloud count [GH-90000]")
        void shouldReturnCombinedStats() { // GH-90000
            // GIVEN
            when(dataCloud.countEntities(TENANT_ID, DataCloudAgentRegistry.REGISTRY_COLLECTION, null)) // GH-90000
                    .thenReturn(Promise.of(5L)); // GH-90000

            // WHEN
            Map<String, Object> stats = runPromise(registry::getStats); // GH-90000

            // THEN
            assertThat(stats).containsEntry("registeredAgents", 0)    // empty cache // GH-90000
                             .containsEntry("persistedAgents", 5L)     // from Data-Cloud // GH-90000
                             .containsEntry("registryType", "DataCloud") // GH-90000
                             .containsEntry("registryTenantId", TENANT_ID); // GH-90000
        }

        @Test
        @DisplayName("reports correct in-memory count after registrations [GH-90000]")
        void shouldReflectCacheSize() { // GH-90000
            // GIVEN
            stubCreateEntity(UUID.randomUUID()); // GH-90000
            stubAppendEvent(); // GH-90000
            when(dataCloud.countEntities(any(), any(), any())).thenReturn(Promise.of(2L)); // GH-90000

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig)); // GH-90000
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig)); // GH-90000

            // WHEN
            Map<String, Object> stats = runPromise(registry::getStats); // GH-90000

            // THEN
            assertThat(stats).containsEntry("registeredAgents", 2); // GH-90000
        }

        @Test
        @DisplayName("propagates Data-Cloud failure from getStats [GH-90000]")
        void shouldPropagateDataCloudFailureInGetStats() { // GH-90000
            // GIVEN
            when(dataCloud.countEntities(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("count failed [GH-90000]")));

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(registry::getStats)) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("count failed [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void stubCreateEntity(UUID entityUuid) { // GH-90000
        when(mockEntity.getId()).thenReturn(entityUuid); // GH-90000
        when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(mockEntity)); // GH-90000
    }

    private void stubAppendEvent() { // GH-90000
        when(dataCloud.appendEvent(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(0L)); // GH-90000
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private TypedAgent<String, String> mockTypedAgent(String agentId, String name, String version, // GH-90000
                                                       AgentType type, Set<String> capabilities) {
        AgentDescriptor descriptor = AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name(name) // GH-90000
                .version(version) // GH-90000
                .type(type) // GH-90000
                .determinism(DeterminismGuarantee.FULL) // GH-90000
                .stateMutability(StateMutability.STATELESS) // GH-90000
                .failureMode(FailureMode.FAIL_FAST) // GH-90000
                .capabilities(capabilities) // GH-90000
                .inputEventTypes(Set.of("test.input [GH-90000]"))
                .outputEventTypes(Set.of("test.output [GH-90000]"))
                .build(); // GH-90000

        TypedAgent<String, String> agent = mock(TypedAgent.class); // GH-90000
        // lenient() prevents UnnecessaryStubbingException for @BeforeEach fixtures // GH-90000
        // that are not consumed by every individual test
        lenient().when(agent.descriptor()).thenReturn(descriptor); // GH-90000
        return agent;
    }
}
