/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.engine.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AEP Registry Integration [GH-90000]")
class AepDataCloudRegistryIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("register + resolve returns typed agent [GH-90000]")
    void shouldRegisterAndResolve() { // GH-90000
        InMemoryRegistry registry = new InMemoryRegistry(); // GH-90000
        TypedAgent<String, String> agent = new MockAgent(descriptor("agent-001", Set.of("read", "write"))); // GH-90000

        runPromise(() -> registry.register(agent, config("agent-001 [GH-90000]")));

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("agent-001 [GH-90000]"));
        assertTrue(resolved.isPresent()); // GH-90000
        assertEquals("agent-001", resolved.get().descriptor().getAgentId()); // GH-90000
    }

    @Test
    @DisplayName("findByCapability filters registered agents [GH-90000]")
    void shouldFindByCapability() { // GH-90000
        InMemoryRegistry registry = new InMemoryRegistry(); // GH-90000

        runPromise(() -> registry.register( // GH-90000
                        new MockAgent(descriptor("read-agent", Set.of("read [GH-90000]"))), config("read-agent [GH-90000]"))
                .then(() -> registry.register( // GH-90000
                        new MockAgent(descriptor("write-agent", Set.of("write [GH-90000]"))), config("write-agent [GH-90000]")))
                .then(() -> registry.register( // GH-90000
                        new MockAgent(descriptor("both-agent", Set.of("read", "write"))), config("both-agent [GH-90000]"))));

        List<String> readAgents = runPromise(() -> registry.findByCapability("read [GH-90000]"));

        assertEquals(2, readAgents.size()); // GH-90000
        assertTrue(readAgents.contains("read-agent [GH-90000]"));
        assertTrue(readAgents.contains("both-agent [GH-90000]"));
    }

    @Test
    @DisplayName("resolve + process executes through registry [GH-90000]")
    void shouldExecuteResolvedAgent() { // GH-90000
        InMemoryRegistry registry = new InMemoryRegistry(); // GH-90000
        TypedAgent<String, String> agent = new MockAgent(descriptor("exec-agent", Set.of("execute [GH-90000]")));
        runPromise(() -> registry.register(agent, config("exec-agent [GH-90000]")));

        String output = runPromise(() -> registry.<String, String>resolve("exec-agent [GH-90000]")
                .then(opt -> opt.map(typed -> typed.process(AgentContext.empty(), "test-input") // GH-90000
                                .map(AgentResult::getOutput)) // GH-90000
                        .orElseGet(() -> Promise.of("not-found [GH-90000]"))));

        assertEquals("exec-agent processed: test-input", output); // GH-90000
    }

    @Test
    @DisplayName("deregister removes the agent [GH-90000]")
    void shouldDeregisterAgent() { // GH-90000
        InMemoryRegistry registry = new InMemoryRegistry(); // GH-90000
        TypedAgent<String, String> agent = new MockAgent(descriptor("dereg-agent", Set.of("read [GH-90000]")));

        runPromise( // GH-90000
                () -> registry.register(agent, config("dereg-agent [GH-90000]")).then(() -> registry.deregister("dereg-agent [GH-90000]")));

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("dereg-agent [GH-90000]"));
        assertFalse(resolved.isPresent()); // GH-90000
    }

    private static AgentDescriptor descriptor(String id, Set<String> capabilities) { // GH-90000
        return AgentDescriptor.builder() // GH-90000
                .agentId(id) // GH-90000
                .name("Test " + id) // GH-90000
                .description("Test descriptor [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .capabilities(capabilities) // GH-90000
                .build(); // GH-90000
    }

    private static AgentConfig config(String id) { // GH-90000
        return AgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.DETERMINISTIC) // GH-90000
                .timeout(Duration.ofSeconds(30)) // GH-90000
                .maxRetries(3) // GH-90000
                .build(); // GH-90000
    }

    private static final class MockAgent implements TypedAgent<String, String> {
        private final AgentDescriptor descriptor;

        private MockAgent(AgentDescriptor descriptor) { // GH-90000
            this.descriptor = descriptor;
        }

        @Override
        public AgentDescriptor descriptor() { // GH-90000
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> shutdown() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<HealthStatus> healthCheck() { // GH-90000
            return Promise.of(HealthStatus.healthy("Agent is healthy [GH-90000]"));
        }

        @Override
        public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
            return Promise.of(AgentResult.success( // GH-90000
                    descriptor.getAgentId() + " processed: " + input, descriptor.getAgentId(), Duration.ZERO)); // GH-90000
        }
    }

    private static final class InMemoryRegistry implements AgentRegistry {
        private final Map<String, TypedAgent<?, ?>> agents = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, AgentConfig> configs = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public Promise<Void> register(TypedAgent<?, ?> agent, AgentConfig config) { // GH-90000
            String agentId = agent.descriptor().getAgentId(); // GH-90000
            agents.put(agentId, agent); // GH-90000
            configs.put(agentId, config); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> deregister(String agentId) { // GH-90000
            agents.remove(agentId); // GH-90000
            configs.remove(agentId); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(String agentId) { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            TypedAgent<I, O> typedAgent = (TypedAgent<I, O>) agents.get(agentId); // GH-90000
            return Promise.of(Optional.ofNullable(typedAgent)); // GH-90000
        }

        @Override
        public Promise<Set<String>> listAgentIds() { // GH-90000
            return Promise.of(Set.copyOf(agents.keySet())); // GH-90000
        }

        @Override
        public Promise<List<String>> findByCapability(String capability) { // GH-90000
            List<String> matches = new ArrayList<>(); // GH-90000
            for (TypedAgent<?, ?> agent : agents.values()) { // GH-90000
                if (agent.descriptor().hasCapability(capability)) { // GH-90000
                    matches.add(agent.descriptor().getAgentId()); // GH-90000
                }
            }
            return Promise.of(matches); // GH-90000
        }

        @Override
        public Promise<Map<String, Object>> getStats() { // GH-90000
            Map<String, Object> stats = new HashMap<>(); // GH-90000
            stats.put("registered", agents.size()); // GH-90000
            stats.put("configured", configs.size()); // GH-90000
            return Promise.of(stats); // GH-90000
        }
    }
}
