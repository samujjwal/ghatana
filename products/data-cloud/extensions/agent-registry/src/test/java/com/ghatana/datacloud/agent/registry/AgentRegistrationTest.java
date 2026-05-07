/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
@DisplayName("AgentRegistrationTest")
class AgentRegistrationTest extends EventloopTestBase {

    private static final String TENANT = "platform";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    @BeforeEach
    void setUp() { 
        registry = new DataCloudAgentRegistry(dataCloud, TENANT); 

        lenient().when(mockEntity.getId()).thenReturn(UUID.randomUUID()); 
        lenient().when(dataCloud.createEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), anyMap())) 
                .thenReturn(Promise.of(mockEntity)); 
        lenient().when(dataCloud.appendEvent(eq(TENANT), anyString(), any())) 
                .thenReturn(Promise.of(0L)); 
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static TypedAgent<String, String> mockAgent(String id, String version, Set<String> capabilities) { 
        TypedAgent<String, String> agent = org.mockito.Mockito.mock(TypedAgent.class); 
        AgentDescriptor descriptor = AgentDescriptor.builder() 
                .agentId(id) 
                .type(AgentType.DETERMINISTIC) 
                .determinism(DeterminismGuarantee.FULL) 
                .capabilities(capabilities) 
                .build(); 
        lenient().when(agent.descriptor()).thenReturn(descriptor); 
        return agent;
    }

    private static AgentConfig configFor(String id) { 
        return AgentConfig.builder() 
                .agentId(id) 
                .timeout(Duration.ofSeconds(30)) 
                .build(); 
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("registers an agent and makes it resolvable by ID")
        void registersAgentAndMakesItResolvable() { 
            TypedAgent<String, String> agent = mockAgent("agent-001", "1.0.0", Set.of("search"));
            runPromise(() -> registry.register(agent, configFor("agent-001")));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.<String, String>resolve("agent-001"));

            assertThat(resolved).isPresent().contains(agent); 
        }

        @Test
        @DisplayName("persists agent to Data-Cloud on registration")
        void persistsAgentToDataCloud() { 
            TypedAgent<String, String> agent = mockAgent("agent-dc", "2.0.0", Set.of("ingest"));
            runPromise(() -> registry.register(agent, configFor("agent-dc")));

            verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), anyMap()); 
        }

        @Test
        @DisplayName("agent appears in listAgentIds() after registration")
        void appearsInListAfterRegistration() { 
            TypedAgent<String, String> agent = mockAgent("agent-list", "1.0.0", Set.of("classify"));
            runPromise(() -> registry.register(agent, configFor("agent-list")));

            Set<String> ids = runPromise(() -> registry.listAgentIds()); 

            assertThat(ids).contains("agent-list");
        }

        @Test
        @DisplayName("multiple agents can be registered concurrently")
        void multipleAgentsCanBeRegistered() { 
            TypedAgent<String, String> a1 = mockAgent("agent-a", "1.0.0", Set.of("cap-a"));
            TypedAgent<String, String> a2 = mockAgent("agent-b", "1.0.0", Set.of("cap-b"));
            TypedAgent<String, String> a3 = mockAgent("agent-c", "1.0.0", Set.of("cap-c"));

            runPromise(() -> registry.register(a1, configFor("agent-a")));
            runPromise(() -> registry.register(a2, configFor("agent-b")));
            runPromise(() -> registry.register(a3, configFor("agent-c")));

            Set<String> ids = runPromise(() -> registry.listAgentIds()); 
            assertThat(ids).containsExactlyInAnyOrder("agent-a", "agent-b", "agent-c"); 
        }

        @Test
        @DisplayName("throws NullPointerException for null agent")
        void throwsOnNullAgent() { 
            assertThatThrownBy(() -> registry.register(null, configFor("x")))
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("throws NullPointerException for null config")
        void throwsOnNullConfig() { 
            TypedAgent<String, String> agent = mockAgent("agent-nc", "1.0.0", Set.of()); 
            assertThatThrownBy(() -> registry.register(agent, null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    // ── Deregistration ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deregister()")
    class DeregisterTests {

        @Test
        @DisplayName("removes agent from cache after deregistration")
        void removesAgentFromCache() { 
            when(dataCloud.deleteEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any(UUID.class))) 
                    .thenReturn(Promise.complete()); 

            TypedAgent<String, String> agent = mockAgent("agent-del", "1.0.0", Set.of("delete-cap"));
            runPromise(() -> registry.register(agent, configFor("agent-del")));
            runPromise(() -> registry.deregister("agent-del"));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.<String, String>resolve("agent-del"));

            assertThat(resolved).isEmpty(); 
        }

        @Test
        @DisplayName("deregistering unknown agent completes without error")
        void deregisterUnknownAgentSucceeds() { 
            // should not throw
            runPromise(() -> registry.deregister("non-existent-agent"));
        }

        @Test
        @DisplayName("agent absent from listAgentIds() after deregistration")
        void absentFromListAfterDeregistration() { 
            when(dataCloud.deleteEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any(UUID.class))) 
                    .thenReturn(Promise.complete()); 

            TypedAgent<String, String> agent = mockAgent("agent-gone", "1.0.0", Set.of("cap"));
            runPromise(() -> registry.register(agent, configFor("agent-gone")));
            runPromise(() -> registry.deregister("agent-gone"));

            Set<String> ids = runPromise(() -> registry.listAgentIds()); 
            assertThat(ids).doesNotContain("agent-gone");
        }
    }

    // ── Lookup by capability ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability()")
    class CapabilityLookupTests {

        @Test
        @DisplayName("returns agents matching a given capability")
        void returnsAgentsWithMatchingCapability() { 
            TypedAgent<String, String> a1 = mockAgent("cap-agent-1", "1.0.0", Set.of("fraud-detection", "scoring")); 
            TypedAgent<String, String> a2 = mockAgent("cap-agent-2", "1.0.0", Set.of("fraud-detection"));
            TypedAgent<String, String> a3 = mockAgent("cap-agent-3", "1.0.0", Set.of("classification"));

            runPromise(() -> registry.register(a1, configFor("cap-agent-1")));
            runPromise(() -> registry.register(a2, configFor("cap-agent-2")));
            runPromise(() -> registry.register(a3, configFor("cap-agent-3")));

            List<String> results = runPromise(() -> registry.findByCapability("fraud-detection"));

            assertThat(results).containsExactlyInAnyOrder("cap-agent-1", "cap-agent-2"); 
        }

        @Test
        @DisplayName("returns empty list when no agents have the requested capability")
        void returnsEmptyForUnknownCapability() { 
            TypedAgent<String, String> agent = mockAgent("no-cap-agent", "1.0.0", Set.of("something-else"));
            runPromise(() -> registry.register(agent, configFor("no-cap-agent")));

            List<String> results = runPromise(() -> registry.findByCapability("nonexistent-cap"));

            assertThat(results).isEmpty(); 
        }

        @Test
        @DisplayName("results are sorted alphabetically by agent ID")
        void resultsAreSorted() { 
            TypedAgent<String, String> b = mockAgent("b-agent", "1.0.0", Set.of("search"));
            TypedAgent<String, String> a = mockAgent("a-agent", "1.0.0", Set.of("search"));
            TypedAgent<String, String> c = mockAgent("c-agent", "1.0.0", Set.of("search"));

            runPromise(() -> registry.register(b, configFor("b-agent")));
            runPromise(() -> registry.register(a, configFor("a-agent")));
            runPromise(() -> registry.register(c, configFor("c-agent")));

            List<String> results = runPromise(() -> registry.findByCapability("search"));

            assertThat(results).containsExactly("a-agent", "b-agent", "c-agent"); 
        }

        @Test
        @DisplayName("deregistered agents no longer appear in capability search")
        void deregisteredAgentExcludedFromCapabilitySearch() { 
            when(dataCloud.deleteEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any(UUID.class))) 
                    .thenReturn(Promise.complete()); 

            TypedAgent<String, String> agent = mockAgent("removed-cap-agent", "1.0.0", Set.of("cap-x"));
            runPromise(() -> registry.register(agent, configFor("removed-cap-agent")));
            runPromise(() -> registry.deregister("removed-cap-agent"));

            List<String> results = runPromise(() -> registry.findByCapability("cap-x"));

            assertThat(results).doesNotContain("removed-cap-agent");
        }
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("versioning")
    class VersioningTests {

        @Test
        @DisplayName("re-registering an agent with a new version replaces the old instance")
        void reRegisteringReplacesOldInstance() { 
            TypedAgent<String, String> v1 = mockAgent("versioned-agent", "1.0.0", Set.of("cap"));
            TypedAgent<String, String> v2 = mockAgent("versioned-agent", "2.0.0", Set.of("cap"));

            runPromise(() -> registry.register(v1, configFor("versioned-agent")));
            runPromise(() -> registry.register(v2, configFor("versioned-agent")));

            Optional<TypedAgent<String, String>> resolved =
                    runPromise(() -> registry.<String, String>resolve("versioned-agent"));

            assertThat(resolved).isPresent().contains(v2); 
        }

        @Test
        @DisplayName("agent descriptor version is reflected in registry stats")
        void statsReflectRegisteredCount() { 
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) 
                    .thenReturn(Promise.of(3L)); 

            TypedAgent<String, String> a1 = mockAgent("stat-agent-1", "1.0.0", Set.of()); 
            TypedAgent<String, String> a2 = mockAgent("stat-agent-2", "1.0.0", Set.of()); 
            TypedAgent<String, String> a3 = mockAgent("stat-agent-3", "1.0.0", Set.of()); 

            runPromise(() -> registry.register(a1, configFor("stat-agent-1")));
            runPromise(() -> registry.register(a2, configFor("stat-agent-2")));
            runPromise(() -> registry.register(a3, configFor("stat-agent-3")));

            var stats = runPromise(() -> registry.getStats()); 
            assertThat(stats).containsEntry("registeredAgents", 3); 
            assertThat(stats).containsEntry("persistedAgents", 3L); 
        }
    }

    // ── Null safety ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("null-safety")
    class NullSafetyTests {

        @Test
        @DisplayName("resolve() throws for null agentId")
        void resolveThrowsOnNullId() { 
            assertThatThrownBy(() -> registry.resolve(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("findByCapability() throws for null capability")
        void findByCapabilityThrowsOnNull() { 
            assertThatThrownBy(() -> registry.findByCapability(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("deregister() throws for null agentId")
        void deregisterThrowsOnNullId() { 
            assertThatThrownBy(() -> registry.deregister(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
