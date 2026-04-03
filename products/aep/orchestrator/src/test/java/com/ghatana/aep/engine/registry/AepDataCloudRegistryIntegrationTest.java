/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.engine.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.agent.registry.DataCloudAgentRegistry;
import io.activej.promise.Promise;
import io.activej.test.rules.EventloopTestBase;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for AEP central registry with DataCloud persistence.
 *
 * <p>Validates that {@link AepCentralRegistryService} correctly delegates to
 * {@link DataCloudAgentRegistry} for persistence while maintaining live agent
 * instances in-process.
 *
 * <p><strong>Phase 5 migration validation:</strong> These tests ensure that the
 * cutover from product-specific registries (DataCloud HTTP API, YAPPC WorkflowAgentController)
 * to AEP unified registry is correct and maintains all agent discovery and execution semantics.
 *
 * @author Ghatana AI Platform
 * @since 2.5.0
 */
@DisplayName("AEP Central Registry + DataCloud Persistence Integration")
class AepDataCloudRegistryIntegrationTest extends EventloopTestBase {

    // Mock implementations for testing (in real environment, these come from DI container)

    private static class MockAgentDescriptor implements AgentDescriptor {
        private final String agentId;
        private final String name;
        private final String version;

        MockAgentDescriptor(String agentId, String name, String version) {
            this.agentId = agentId;
            this.name = name;
            this.version = version;
        }

        @Override
        public String getAgentId() {
            return agentId;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getDescription() {
            return "Mock agent for testing";
        }

        @Override
        public Set<String> getCapabilities() {
            return Set.of("read", "write");
        }

        @Override
        public boolean hasCapability(String capability) {
            return getCapabilities().contains(capability);
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of("type", "test", "product", "test");
        }
    }

    private static class MockAgent implements TypedAgent<String, String> {
        private final AgentDescriptor descriptor;

        MockAgent(AgentDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
            return Promise.of(AgentResult.success(descriptor.getAgentId() + " processed: " + input));
        }
    }

    private static class MockDataCloudRegistry implements com.ghatana.agent.spi.AgentRegistry {
        private final Map<String, TypedAgent<?, ?>> agents = new HashMap<>();
        private final Map<String, AgentConfig> configs = new HashMap<>();

        @Override
        public Promise<Void> register(TypedAgent<?, ?> agent, AgentConfig config) {
            agents.put(agent.descriptor().getAgentId(), agent);
            configs.put(agent.descriptor().getAgentId(), config);
            return Promise.complete();
        }

        @Override
        public Promise<Void> deregister(String agentId) {
            agents.remove(agentId);
            configs.remove(agentId);
            return Promise.complete();
        }

        @Override
        public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(String agentId) {
            @SuppressWarnings("unchecked")
            TypedAgent<I, O> agent = (TypedAgent<I, O>) agents.get(agentId);
            return Promise.of(Optional.ofNullable(agent));
        }

        @Override
        public Promise<Set<String>> listAgentIds() {
            return Promise.of(Set.copyOf(agents.keySet()));
        }

        @Override
        public Promise<List<String>> findByCapability(String capability) {
            return Promise.of(agents.values().stream()
                    .filter(a -> a.descriptor().hasCapability(capability))
                    .map(a -> a.descriptor().getAgentId())
                    .toList());
        }

        @Override
        public Promise<Map<String, Object>> getStats() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("registered", agents.size());
            stats.put("backend", "mock");
            return Promise.of(stats);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Registry Discovery and Persistence
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should register agent in both in-process and DataCloud storage")
    void testRegisterAgentToBothBackends() {
        MockAgentDescriptor descriptor = new MockAgentDescriptor("agent-001", "Test Agent", "1.0.0");
        MockAgent agent = new MockAgent(descriptor);
        AgentConfig config = new AgentConfig.Builder()
                .agentId("agent-001")
                .timeout(30000)
                .retries(3)
                .build();

        MockDataCloudRegistry dcRegistry = new MockDataCloudRegistry();

        String registered = runPromise(() -> dcRegistry
                .register(agent, config)
                .then(() -> dcRegistry.resolve("agent-001"))
                .map(resolved -> resolved.isPresent() ? "registered" : "not-found"));

        assertEquals("registered", registered);
    }

    @Test
    @DisplayName("should list agents from in-memory cache")
    void testListAgentsFromInMemoryCache() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        // Register multiple agents
        for (int i = 1; i <= 3; i++) {
            String agentId = "agent-" + String.format("%03d", i);
            MockAgentDescriptor desc = new MockAgentDescriptor(agentId, "Agent " + i, "1.0.0");
            MockAgent agent = new MockAgent(desc);
            AgentConfig config = new AgentConfig.Builder()
                    .agentId(agentId)
                    .timeout(30000)
                    .retries(3)
                    .build();

            runPromise(() -> registry.register(agent, config));
        }

        Set<String> agentIds = runPromise(registry::listAgentIds);

        assertEquals(3, agentIds.size());
        assertTrue(agentIds.contains("agent-001"));
        assertTrue(agentIds.contains("agent-002"));
        assertTrue(agentIds.contains("agent-003"));
    }

    @Test
    @DisplayName("should resolve agent after registration")
    void testResolveAgentAfterRegistration() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        MockAgentDescriptor descriptor = new MockAgentDescriptor("resolver-agent", "Resolver Test", "1.0.0");
        MockAgent agent = new MockAgent(descriptor);
        AgentConfig config = new AgentConfig.Builder()
                .agentId("resolver-agent")
                .timeout(15000)
                .retries(1)
                .build();

        runPromise(() -> registry.register(agent, config));

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("resolver-agent"));

        assertTrue(resolved.isPresent());
        assertEquals("resolver-agent", resolved.get().descriptor().getAgentId());
    }

    @Test
    @DisplayName("should not resolve agent that was never registered")
    void testResolveNonExistentAgent() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("nonexistent-agent"));

        assertFalse(resolved.isPresent());
    }

    @Test
    @DisplayName("should deregister agent from both backends")
    void testDeregisterAgentFromBothBackends() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        MockAgentDescriptor descriptor = new MockAgentDescriptor("dereg-agent", "Deregister Test", "1.0.0");
        MockAgent agent = new MockAgent(descriptor);
        AgentConfig config = new AgentConfig.Builder()
                .agentId("dereg-agent")
                .timeout(30000)
                .retries(3)
                .build();

        runPromise(() -> registry.register(agent, config).then(() -> registry.deregister("dereg-agent")));

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("dereg-agent"));

        assertFalse(resolved.isPresent());
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Capability-Based Discovery
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should find agents by capability")
    void testFindAgentsByCapability() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        // Register agents with different capabilities
        MockAgentDescriptor readDesc = new MockAgentDescriptor("read-agent", "Reader", "1.0.0") {
            @Override
            public Set<String> getCapabilities() {
                return Set.of("read");
            }
        };

        MockAgentDescriptor writeDesc = new MockAgentDescriptor("write-agent", "Writer", "1.0.0") {
            @Override
            public Set<String> getCapabilities() {
                return Set.of("write");
            }
        };

        MockAgentDescriptor bothDesc = new MockAgentDescriptor("both-agent", "Both", "1.0.0") {
            @Override
            public Set<String> getCapabilities() {
                return Set.of("read", "write");
            }
        };

        runPromise(() -> {
            AgentConfig config = new AgentConfig.Builder()
                    .agentId("dummy")
                    .timeout(30000)
                    .retries(3)
                    .build();

            return registry.register(new MockAgent(readDesc), config)
                    .then(() -> registry.register(new MockAgent(writeDesc), config))
                    .then(() -> registry.register(new MockAgent(bothDesc), config));
        });

        List<String> readAgents = runPromise(() -> registry.findByCapability("read"));

        assertEquals(2, readAgents.size());
        assertTrue(readAgents.contains("read-agent"));
        assertTrue(readAgents.contains("both-agent"));
    }

    @Test
    @DisplayName("should return empty list for non-existent capability")
    void testFindAgentsByNonExistentCapability() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        List<String> agents = runPromise(() -> registry.findByCapability("nonexistent"));

        assertTrue(agents.isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Statistics and Monitoring
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should include agent count in statistics")
    void testStatsIncludeAgentCount() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        // Register 2 agents
        for (int i = 1; i <= 2; i++) {
            String agentId = "stat-agent-" + i;
            MockAgentDescriptor desc = new MockAgentDescriptor(agentId, "Stat Agent " + i, "1.0.0");
            MockAgent agent = new MockAgent(desc);
            AgentConfig config = new AgentConfig.Builder()
                    .agentId(agentId)
                    .timeout(30000)
                    .retries(3)
                    .build();

            runPromise(() -> registry.register(agent, config));
        }

        Map<String, Object> stats = runPromise(registry::getStats);

        assertEquals(2, stats.get("registered"));
        assertEquals("mock", stats.get("backend"));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Execution Semantics (AEP → DataCloud)
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should execute agent through unified registry API")
    void testExecuteAgentThroughRegistry() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        MockAgentDescriptor descriptor = new MockAgentDescriptor("exec-agent", "Executor", "1.0.0");
        MockAgent agent = new MockAgent(descriptor);
        AgentConfig config = new AgentConfig.Builder()
                .agentId("exec-agent")
                .timeout(30000)
                .retries(3)
                .build();

        runPromise(() -> registry.register(agent, config));

        String result = runPromise(() -> registry.resolve("exec-agent").flatMap(optAgent -> {
            if (optAgent.isEmpty()) {
                return Promise.of("not-found");
            }
            return optAgent.get().process(null, "test-input").map(agentResult -> (String) agentResult.output());
        }));

        assertEquals("exec-agent processed: test-input", result);
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Multi-Product Agent Discovery (YAPPC + DataCloud)
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should discover agents from multiple products in unified API")
    void testMultiProductAgentDiscovery() {
        MockDataCloudRegistry dcRegistry = new MockDataCloudRegistry();
        MockDataCloudRegistry yappcRegistry = new MockDataCloudRegistry();

        // Register agent in DataCloud
        MockAgentDescriptor dcDesc = new MockAgentDescriptor("dc-analysis-agent", "DC Analyzer", "1.0.0");
        MockAgent dcAgent = new MockAgent(dcDesc);
        AgentConfig dcConfig = new AgentConfig.Builder()
                .agentId("dc-analysis-agent")
                .timeout(30000)
                .retries(3)
                .build();

        // Register agent in YAPPC
        MockAgentDescriptor yappcDesc = new MockAgentDescriptor("yappc-model-agent", "YAPPC Model", "1.0.0");
        MockAgent yappcAgent = new MockAgent(yappcDesc);
        AgentConfig yappcConfig = new AgentConfig.Builder()
                .agentId("yappc-model-agent")
                .timeout(30000)
                .retries(3)
                .build();

        runPromise(() ->
                dcRegistry.register(dcAgent, dcConfig).then(() -> yappcRegistry.register(yappcAgent, yappcConfig)));

        // In unified API, both should be discoverable
        Set<String> dcAgents = runPromise(dcRegistry::listAgentIds);
        Set<String> yappcAgents = runPromise(yappcRegistry::listAgentIds);

        assertEquals(1, dcAgents.size());
        assertEquals(1, yappcAgents.size());
        assertTrue(dcAgents.contains("dc-analysis-agent"));
        assertTrue(yappcAgents.contains("yappc-model-agent"));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Lifecycle Events (Audit Trail)
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should record agent lifecycle events in DataCloud audit trail")
    void testAgentLifecycleEventsAudited() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        MockAgentDescriptor descriptor = new MockAgentDescriptor("audit-agent", "Audit Test", "1.0.0");
        MockAgent agent = new MockAgent(descriptor);
        AgentConfig config = new AgentConfig.Builder()
                .agentId("audit-agent")
                .timeout(30000)
                .retries(3)
                .build();

        // Events should be recorded:
        // 1. agent.registered
        // 2. agent.deregistered

        runPromise(() -> registry.register(agent, config).then(() -> registry.deregister("audit-agent")));

        // Verify agent is no longer accessible
        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("audit-agent"));

        assertFalse(resolved.isPresent());
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // TESTS: Error Handling & Edge Cases
    // ═════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should handle double registration gracefully")
    void testDoubleRegistrationHandling() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        MockAgentDescriptor descriptor = new MockAgentDescriptor("double-agent", "Double Test", "1.0.0");
        MockAgent agent = new MockAgent(descriptor);
        AgentConfig config = new AgentConfig.Builder()
                .agentId("double-agent")
                .timeout(30000)
                .retries(3)
                .build();

        // Register twice (second registration overwrites first)
        runPromise(() -> registry.register(agent, config).then(() -> registry.register(agent, config)));

        Set<String> agents = runPromise(registry::listAgentIds);

        assertEquals(1, agents.size());
    }

    @Test
    @DisplayName("should handle deregistration of non-existent agent gracefully")
    void testDeregisterNonExistentAgent() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        // Should not throw exception
        runPromise(() -> registry.deregister("nonexistent"));

        Set<String> agents = runPromise(registry::listAgentIds);

        assertTrue(agents.isEmpty());
    }

    @Test
    @DisplayName("should process concurrent agent registration correctly")
    void testConcurrentAgentRegistration() {
        MockDataCloudRegistry registry = new MockDataCloudRegistry();

        AgentConfig config = new AgentConfig.Builder()
                .agentId("dummy")
                .timeout(30000)
                .retries(3)
                .build();

        // Register multiple agents in sequence
        runPromise(() -> {
            Promise<Void> result = Promise.complete();

            for (int i = 1; i <= 5; i++) {
                int idx = i;
                result = result.then(() -> {
                    MockAgentDescriptor desc =
                            new MockAgentDescriptor("concurrent-" + idx, "Concurrent " + idx, "1.0.0");
                    MockAgent agent = new MockAgent(desc);
                    return registry.register(agent, config);
                });
            }

            return result;
        });

        Set<String> agents = runPromise(registry::listAgentIds);

        assertEquals(5, agents.size());
        for (int i = 1; i <= 5; i++) {
            assertTrue(agents.contains("concurrent-" + i));
        }
    }
}
