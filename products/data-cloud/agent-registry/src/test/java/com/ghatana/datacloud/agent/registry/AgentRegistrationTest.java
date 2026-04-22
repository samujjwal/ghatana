/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration lifecycle tests for {@link DataCloudAgentRegistry}.
 *
 * <p>Validates agent registration, deregistration, lookup by ID, lookup by
 * capability, and versioning semantics.
 *
 * @doc.type    class
 * @doc.purpose Agent registration, deregistration, capability-based lookup, versioning
 * @doc.layer   registry
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AgentRegistrationTest [GH-90000]")
class AgentRegistrationTest extends EventloopTestBase {

    private static final String TENANT = "platform";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new DataCloudAgentRegistry(dataCloud, TENANT); // GH-90000

        lenient().when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
        lenient().when(dataCloud.createEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), anyMap())) // GH-90000
                .thenReturn(Promise.of(mockEntity)); // GH-90000
        lenient().when(dataCloud.appendEvent(eq(TENANT), anyString(), any())) // GH-90000
                .thenReturn(Promise.of(0L)); // GH-90000
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked [GH-90000]")
    private static TypedAgent<String, String> mockAgent(String id, String version, Set<String> capabilities) { // GH-90000
        TypedAgent<String, String> agent = org.mockito.Mockito.mock(TypedAgent.class); // GH-90000
        AgentDescriptor descriptor = AgentDescriptor.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.DETERMINISTIC) // GH-90000
                .determinism(DeterminismGuarantee.FULL) // GH-90000
                .capabilities(capabilities) // GH-90000
                .build(); // GH-90000
        lenient().when(agent.descriptor()).thenReturn(descriptor); // GH-90000
        return agent;
    }

    private static AgentConfig configFor(String id) { // GH-90000
        return AgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .timeout(Duration.ofSeconds(30)) // GH-90000
                .build(); // GH-90000
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register() [GH-90000]")
    class RegisterTests {

        @Test
        @DisplayName("registers an agent and makes it resolvable by ID [GH-90000]")
        void registersAgentAndMakesItResolvable() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("agent-001", "1.0.0", Set.of("search [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("agent-001 [GH-90000]")));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.<String, String>resolve("agent-001 [GH-90000]"));

            assertThat(resolved).isPresent().contains(agent); // GH-90000
        }

        @Test
        @DisplayName("persists agent to Data-Cloud on registration [GH-90000]")
        void persistsAgentToDataCloud() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("agent-dc", "2.0.0", Set.of("ingest [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("agent-dc [GH-90000]")));

            verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), anyMap()); // GH-90000
        }

        @Test
        @DisplayName("agent appears in listAgentIds() after registration [GH-90000]")
        void appearsInListAfterRegistration() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("agent-list", "1.0.0", Set.of("classify [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("agent-list [GH-90000]")));

            Set<String> ids = runPromise(() -> registry.listAgentIds()); // GH-90000

            assertThat(ids).contains("agent-list [GH-90000]");
        }

        @Test
        @DisplayName("multiple agents can be registered concurrently [GH-90000]")
        void multipleAgentsCanBeRegistered() { // GH-90000
            TypedAgent<String, String> a1 = mockAgent("agent-a", "1.0.0", Set.of("cap-a [GH-90000]"));
            TypedAgent<String, String> a2 = mockAgent("agent-b", "1.0.0", Set.of("cap-b [GH-90000]"));
            TypedAgent<String, String> a3 = mockAgent("agent-c", "1.0.0", Set.of("cap-c [GH-90000]"));

            runPromise(() -> registry.register(a1, configFor("agent-a [GH-90000]")));
            runPromise(() -> registry.register(a2, configFor("agent-b [GH-90000]")));
            runPromise(() -> registry.register(a3, configFor("agent-c [GH-90000]")));

            Set<String> ids = runPromise(() -> registry.listAgentIds()); // GH-90000
            assertThat(ids).containsExactlyInAnyOrder("agent-a", "agent-b", "agent-c"); // GH-90000
        }

        @Test
        @DisplayName("throws NullPointerException for null agent [GH-90000]")
        void throwsOnNullAgent() { // GH-90000
            assertThatThrownBy(() -> registry.register(null, configFor("x [GH-90000]")))
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("throws NullPointerException for null config [GH-90000]")
        void throwsOnNullConfig() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("agent-nc", "1.0.0", Set.of()); // GH-90000
            assertThatThrownBy(() -> registry.register(agent, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ── Deregistration ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deregister() [GH-90000]")
    class DeregisterTests {

        @Test
        @DisplayName("removes agent from cache after deregistration [GH-90000]")
        void removesAgentFromCache() { // GH-90000
            when(dataCloud.deleteEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any(UUID.class))) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000

            TypedAgent<String, String> agent = mockAgent("agent-del", "1.0.0", Set.of("delete-cap [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("agent-del [GH-90000]")));
            runPromise(() -> registry.deregister("agent-del [GH-90000]"));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.<String, String>resolve("agent-del [GH-90000]"));

            assertThat(resolved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("deregistering unknown agent completes without error [GH-90000]")
        void deregisterUnknownAgentSucceeds() { // GH-90000
            // should not throw
            runPromise(() -> registry.deregister("non-existent-agent [GH-90000]"));
        }

        @Test
        @DisplayName("agent absent from listAgentIds() after deregistration [GH-90000]")
        void absentFromListAfterDeregistration() { // GH-90000
            when(dataCloud.deleteEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any(UUID.class))) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000

            TypedAgent<String, String> agent = mockAgent("agent-gone", "1.0.0", Set.of("cap [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("agent-gone [GH-90000]")));
            runPromise(() -> registry.deregister("agent-gone [GH-90000]"));

            Set<String> ids = runPromise(() -> registry.listAgentIds()); // GH-90000
            assertThat(ids).doesNotContain("agent-gone [GH-90000]");
        }
    }

    // ── Lookup by capability ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability() [GH-90000]")
    class CapabilityLookupTests {

        @Test
        @DisplayName("returns agents matching a given capability [GH-90000]")
        void returnsAgentsWithMatchingCapability() { // GH-90000
            TypedAgent<String, String> a1 = mockAgent("cap-agent-1", "1.0.0", Set.of("fraud-detection", "scoring")); // GH-90000
            TypedAgent<String, String> a2 = mockAgent("cap-agent-2", "1.0.0", Set.of("fraud-detection [GH-90000]"));
            TypedAgent<String, String> a3 = mockAgent("cap-agent-3", "1.0.0", Set.of("classification [GH-90000]"));

            runPromise(() -> registry.register(a1, configFor("cap-agent-1 [GH-90000]")));
            runPromise(() -> registry.register(a2, configFor("cap-agent-2 [GH-90000]")));
            runPromise(() -> registry.register(a3, configFor("cap-agent-3 [GH-90000]")));

            List<String> results = runPromise(() -> registry.findByCapability("fraud-detection [GH-90000]"));

            assertThat(results).containsExactlyInAnyOrder("cap-agent-1", "cap-agent-2"); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when no agents have the requested capability [GH-90000]")
        void returnsEmptyForUnknownCapability() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("no-cap-agent", "1.0.0", Set.of("something-else [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("no-cap-agent [GH-90000]")));

            List<String> results = runPromise(() -> registry.findByCapability("nonexistent-cap [GH-90000]"));

            assertThat(results).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("results are sorted alphabetically by agent ID [GH-90000]")
        void resultsAreSorted() { // GH-90000
            TypedAgent<String, String> b = mockAgent("b-agent", "1.0.0", Set.of("search [GH-90000]"));
            TypedAgent<String, String> a = mockAgent("a-agent", "1.0.0", Set.of("search [GH-90000]"));
            TypedAgent<String, String> c = mockAgent("c-agent", "1.0.0", Set.of("search [GH-90000]"));

            runPromise(() -> registry.register(b, configFor("b-agent [GH-90000]")));
            runPromise(() -> registry.register(a, configFor("a-agent [GH-90000]")));
            runPromise(() -> registry.register(c, configFor("c-agent [GH-90000]")));

            List<String> results = runPromise(() -> registry.findByCapability("search [GH-90000]"));

            assertThat(results).containsExactly("a-agent", "b-agent", "c-agent"); // GH-90000
        }

        @Test
        @DisplayName("deregistered agents no longer appear in capability search [GH-90000]")
        void deregisteredAgentExcludedFromCapabilitySearch() { // GH-90000
            when(dataCloud.deleteEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any(UUID.class))) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000

            TypedAgent<String, String> agent = mockAgent("removed-cap-agent", "1.0.0", Set.of("cap-x [GH-90000]"));
            runPromise(() -> registry.register(agent, configFor("removed-cap-agent [GH-90000]")));
            runPromise(() -> registry.deregister("removed-cap-agent [GH-90000]"));

            List<String> results = runPromise(() -> registry.findByCapability("cap-x [GH-90000]"));

            assertThat(results).doesNotContain("removed-cap-agent [GH-90000]");
        }
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("versioning [GH-90000]")
    class VersioningTests {

        @Test
        @DisplayName("re-registering an agent with a new version replaces the old instance [GH-90000]")
        void reRegisteringReplacesOldInstance() { // GH-90000
            TypedAgent<String, String> v1 = mockAgent("versioned-agent", "1.0.0", Set.of("cap [GH-90000]"));
            TypedAgent<String, String> v2 = mockAgent("versioned-agent", "2.0.0", Set.of("cap [GH-90000]"));

            runPromise(() -> registry.register(v1, configFor("versioned-agent [GH-90000]")));
            runPromise(() -> registry.register(v2, configFor("versioned-agent [GH-90000]")));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.<String, String>resolve("versioned-agent [GH-90000]"));

            assertThat(resolved).isPresent().contains(v2); // GH-90000
        }

        @Test
        @DisplayName("agent descriptor version is reflected in registry stats [GH-90000]")
        void statsReflectRegisteredCount() { // GH-90000
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(3L)); // GH-90000

            TypedAgent<String, String> a1 = mockAgent("stat-agent-1", "1.0.0", Set.of()); // GH-90000
            TypedAgent<String, String> a2 = mockAgent("stat-agent-2", "1.0.0", Set.of()); // GH-90000
            TypedAgent<String, String> a3 = mockAgent("stat-agent-3", "1.0.0", Set.of()); // GH-90000

            runPromise(() -> registry.register(a1, configFor("stat-agent-1 [GH-90000]")));
            runPromise(() -> registry.register(a2, configFor("stat-agent-2 [GH-90000]")));
            runPromise(() -> registry.register(a3, configFor("stat-agent-3 [GH-90000]")));

            var stats = runPromise(() -> registry.getStats()); // GH-90000
            assertThat(stats).containsEntry("registeredAgents", 3); // GH-90000
            assertThat(stats).containsEntry("persistedAgents", 3L); // GH-90000
        }
    }

    // ── Null safety ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("null-safety [GH-90000]")
    class NullSafetyTests {

        @Test
        @DisplayName("resolve() throws for null agentId [GH-90000]")
        void resolveThrowsOnNullId() { // GH-90000
            assertThatThrownBy(() -> registry.resolve(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("findByCapability() throws for null capability [GH-90000]")
        void findByCapabilityThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> registry.findByCapability(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("deregister() throws for null agentId [GH-90000]")
        void deregisterThrowsOnNullId() { // GH-90000
            assertThatThrownBy(() -> registry.deregister(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
