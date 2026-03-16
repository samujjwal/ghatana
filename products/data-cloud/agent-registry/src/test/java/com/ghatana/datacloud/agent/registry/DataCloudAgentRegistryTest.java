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
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudAgentRegistry")
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("persists descriptor to Data-Cloud and populates cache")
        void shouldPersistAndCache() {
            // GIVEN
            UUID entityUuid = UUID.randomUUID();
            stubCreateEntity(entityUuid);
            stubAppendEvent();

            // WHEN
            Void result = runPromise(() ->
                    registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // THEN – Data-Cloud save was called with the right collection
            verify(dataCloud).createEntity(
                    eq(TENANT_ID),
                    eq(DataCloudAgentRegistry.REGISTRY_COLLECTION),
                    argThat(map -> "fraud-detector-v1".equals(map.get("agentId"))
                            && "Fraud Detector".equals(map.get("name"))
                            && "1.0.0".equals(map.get("version"))));
            assertThat(result).isNull(); // Promise<Void> resolves to null
        }

        @Test
        @DisplayName("agent can be resolved from cache immediately after registration")
        void shouldBeResolvableAfterRegistration() {
            // GIVEN
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // WHEN
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // THEN
            assertThat(resolved).isPresent().containsSame(fraudDetectorAgent);
        }

        @Test
        @DisplayName("registers multiple agents independently")
        void shouldRegisterMultipleAgents() {
            // GIVEN
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            // WHEN – register two agents
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));
            Set<String> ids = runPromise(registry::listAgentIds);

            // THEN
            assertThat(ids).containsExactlyInAnyOrder("fraud-detector-v1", "anomaly-detector-v1");
        }

        @Test
        @DisplayName("replaces existing registration when same agentId is re-registered")
        void shouldReplaceExistingRegistration() {
            // GIVEN – two successive registrations with the same ID
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            TypedAgent<String, String> v2 = mockTypedAgent("fraud-detector-v1", "Fraud Detector",
                    "2.0.0", AgentType.DETERMINISTIC, Set.of("fraud-detection"));
            AgentConfig v2Config = AgentConfig.builder()
                    .agentId("fraud-detector-v1").type(AgentType.DETERMINISTIC).version("2.0.0").build();

            // WHEN
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(v2, v2Config));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // THEN – cache holds v2
            assertThat(resolved).isPresent().containsSame(v2);
        }

        @Test
        @DisplayName("propagates Data-Cloud failure without corrupting cache")
        void shouldPropagateDataCloudFailure() {
            // GIVEN
            when(dataCloud.createEntity(any(), any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Data-Cloud unavailable")));

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() ->
                    registry.register(fraudDetectorAgent, fraudDetectorConfig)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Data-Cloud unavailable");

            // Cache must NOT be updated on failure
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("rejects null agent argument")
        void shouldRejectNullAgent() {
            assertThatThrownBy(() -> registry.register(null, fraudDetectorConfig))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null config argument")
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> registry.register(fraudDetectorAgent, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deregister()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deregister()")
    class Deregister {

        @Test
        @DisplayName("removes agent from both Data-Cloud and cache")
        void shouldRemoveFromDataCloudAndCache() {
            // GIVEN
            UUID entityUuid = UUID.randomUUID();
            stubCreateEntity(entityUuid);
            stubAppendEvent();
            when(dataCloud.deleteEntity(any(), any(), any())).thenReturn(Promise.complete());

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // WHEN
            runPromise(() -> registry.deregister("fraud-detector-v1"));

            // THEN – Data-Cloud delete was called with correct UUID
            verify(dataCloud).deleteEntity(TENANT_ID, DataCloudAgentRegistry.REGISTRY_COLLECTION, entityUuid);

            // Cache is empty
            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("succeeds silently when agent was never registered")
        void shouldSucceedSilentlyForUnknownAgent() {
            // No Data-Cloud calls expected when agent is unknown
            Void result = runPromise(() -> registry.deregister("nonexistent-agent"));
            assertThat(result).isNull();
            verifyNoInteractions(dataCloud);
        }

        @Test
        @DisplayName("rejects null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> registry.deregister(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolve()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("returns registered agent from cache")
        void shouldReturnCachedAgent() {
            // GIVEN
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));

            // WHEN
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // THEN – resolved without any additional Data-Cloud call after registration
            assertThat(result).isPresent().containsSame(fraudDetectorAgent);
        }

        @Test
        @DisplayName("returns empty Optional for unknown agentId")
        void shouldReturnEmptyForUnknownAgent() {
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("does-not-exist"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty Optional after agent is deregistered")
        void shouldReturnEmptyAfterDeregister() {
            // GIVEN
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            when(dataCloud.deleteEntity(any(), any(), any())).thenReturn(Promise.complete());

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.deregister("fraud-detector-v1"));

            // WHEN
            Optional<TypedAgent<String, String>> result =
                    runPromise(() -> registry.resolve("fraud-detector-v1"));

            // THEN
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("rejects null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> registry.resolve(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listAgentIds()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAgentIds()")
    class ListAgentIds {

        @Test
        @DisplayName("returns empty set when no agents are registered")
        void shouldReturnEmptySetWhenEmpty() {
            Set<String> ids = runPromise(registry::listAgentIds);
            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("returns IDs of all registered agents")
        void shouldReturnAllRegisteredIds() {
            // GIVEN
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            // WHEN
            Set<String> ids = runPromise(registry::listAgentIds);

            // THEN
            assertThat(ids).containsExactlyInAnyOrder("fraud-detector-v1", "anomaly-detector-v1");
        }

        @Test
        @DisplayName("returned set is a snapshot (immutable)")
        void shouldReturnImmutableSnapshot() {
            Set<String> ids = runPromise(registry::listAgentIds);
            assertThatThrownBy(() -> ids.add("mutate-attempt"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByCapability()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability()")
    class FindByCapability {

        @BeforeEach
        void registerBothAgents() {
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));
        }

        @Test
        @DisplayName("returns agents that have the queried capability")
        void shouldReturnMatchingAgents() {
            List<String> ids = runPromise(() -> registry.findByCapability("fraud-detection"));
            assertThat(ids).containsExactly("fraud-detector-v1");
        }

        @Test
        @DisplayName("returns multiple agents sharing the same capability")
        void shouldReturnMultipleAgentsWithSharedCapability() {
            // Both agents have 'risk-scoring' — use a fresh agent that also has it
            TypedAgent<String, String> riskAgent = mockTypedAgent("risk-scoring-v1",
                    "Risk Scorer", "1.0.0", AgentType.DETERMINISTIC,
                    Set.of("fraud-detection", "risk-scoring"));
            AgentConfig riskConfig = AgentConfig.builder()
                    .agentId("risk-scoring-v1").type(AgentType.DETERMINISTIC).build();

            runPromise(() -> registry.register(riskAgent, riskConfig));
            List<String> ids = runPromise(() -> registry.findByCapability("fraud-detection"));

            assertThat(ids).containsExactlyInAnyOrder("fraud-detector-v1", "risk-scoring-v1");
        }

        @Test
        @DisplayName("returns empty list when no agents match the capability")
        void shouldReturnEmptyForUnknownCapability() {
            List<String> ids = runPromise(() -> registry.findByCapability("quantum-computing"));
            assertThat(ids).isEmpty();
        }

        @Test
        @DisplayName("rejects null capability")
        void shouldRejectNullCapability() {
            assertThatThrownBy(() -> registry.findByCapability(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStats()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("returns stats from in-memory cache and Data-Cloud count")
        void shouldReturnCombinedStats() {
            // GIVEN
            when(dataCloud.countEntities(TENANT_ID, DataCloudAgentRegistry.REGISTRY_COLLECTION, null))
                    .thenReturn(Promise.of(5L));

            // WHEN
            Map<String, Object> stats = runPromise(registry::getStats);

            // THEN
            assertThat(stats).containsEntry("registeredAgents", 0)    // empty cache
                             .containsEntry("persistedAgents", 5L)     // from Data-Cloud
                             .containsEntry("registryType", "DataCloud")
                             .containsEntry("registryTenantId", TENANT_ID);
        }

        @Test
        @DisplayName("reports correct in-memory count after registrations")
        void shouldReflectCacheSize() {
            // GIVEN
            stubCreateEntity(UUID.randomUUID());
            stubAppendEvent();
            when(dataCloud.countEntities(any(), any(), any())).thenReturn(Promise.of(2L));

            runPromise(() -> registry.register(fraudDetectorAgent, fraudDetectorConfig));
            runPromise(() -> registry.register(anomalyDetectorAgent, anomalyDetectorConfig));

            // WHEN
            Map<String, Object> stats = runPromise(registry::getStats);

            // THEN
            assertThat(stats).containsEntry("registeredAgents", 2);
        }

        @Test
        @DisplayName("propagates Data-Cloud failure from getStats")
        void shouldPropagateDataCloudFailureInGetStats() {
            // GIVEN
            when(dataCloud.countEntities(any(), any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("count failed")));

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(registry::getStats))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("count failed");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void stubCreateEntity(UUID entityUuid) {
        when(mockEntity.getId()).thenReturn(entityUuid);
        when(dataCloud.createEntity(any(), any(), any()))
                .thenReturn(Promise.of(mockEntity));
    }

    private void stubAppendEvent() {
        when(dataCloud.appendEvent(any(), any(), any()))
                .thenReturn(Promise.of(0L));
    }

    @SuppressWarnings("unchecked")
    private TypedAgent<String, String> mockTypedAgent(String agentId, String name, String version,
                                                       AgentType type, Set<String> capabilities) {
        AgentDescriptor descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(name)
                .version(version)
                .type(type)
                .determinism(DeterminismGuarantee.FULL)
                .stateMutability(StateMutability.STATELESS)
                .failureMode(FailureMode.FAIL_FAST)
                .capabilities(capabilities)
                .inputEventTypes(Set.of("test.input"))
                .outputEventTypes(Set.of("test.output"))
                .build();

        TypedAgent<String, String> agent = mock(TypedAgent.class);
        // lenient() prevents UnnecessaryStubbingException for @BeforeEach fixtures
        // that are not consumed by every individual test
        lenient().when(agent.descriptor()).thenReturn(descriptor);
        return agent;
    }
}
