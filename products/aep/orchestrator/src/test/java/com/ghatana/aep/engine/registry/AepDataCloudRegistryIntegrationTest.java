/*
 * Copyright (c) 2026 Ghatana Inc. 
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

@DisplayName("AEP Registry Integration")
class AepDataCloudRegistryIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("register + resolve returns typed agent")
    void shouldRegisterAndResolve() { 
        InMemoryRegistry registry = new InMemoryRegistry(); 
        TypedAgent<String, String> agent = new MockAgent(descriptor("agent-001", Set.of("read", "write"))); 

        runPromise(() -> registry.register(agent, config("agent-001")));

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("agent-001"));
        assertTrue(resolved.isPresent()); 
        assertEquals("agent-001", resolved.get().descriptor().getAgentId()); 
    }

    @Test
    @DisplayName("findByCapability filters registered agents")
    void shouldFindByCapability() { 
        InMemoryRegistry registry = new InMemoryRegistry(); 

        runPromise(() -> registry.register( 
                        new MockAgent(descriptor("read-agent", Set.of("read"))), config("read-agent"))
                .then(() -> registry.register( 
                        new MockAgent(descriptor("write-agent", Set.of("write"))), config("write-agent")))
                .then(() -> registry.register( 
                        new MockAgent(descriptor("both-agent", Set.of("read", "write"))), config("both-agent"))));

        List<String> readAgents = runPromise(() -> registry.findByCapability("read"));

        assertEquals(2, readAgents.size()); 
        assertTrue(readAgents.contains("read-agent"));
        assertTrue(readAgents.contains("both-agent"));
    }

    @Test
    @DisplayName("resolve + process executes through registry")
    void shouldExecuteResolvedAgent() { 
        InMemoryRegistry registry = new InMemoryRegistry(); 
        TypedAgent<String, String> agent = new MockAgent(descriptor("exec-agent", Set.of("execute")));
        runPromise(() -> registry.register(agent, config("exec-agent")));

        String output = runPromise(() -> registry.<String, String>resolve("exec-agent")
                .then(opt -> opt.map(typed -> typed.process(AgentContext.empty(), "test-input") 
                                .map(AgentResult::getOutput)) 
                        .orElseGet(() -> Promise.of("not-found"))));

        assertEquals("exec-agent processed: test-input", output); 
    }

    @Test
    @DisplayName("deregister removes the agent")
    void shouldDeregisterAgent() { 
        InMemoryRegistry registry = new InMemoryRegistry(); 
        TypedAgent<String, String> agent = new MockAgent(descriptor("dereg-agent", Set.of("read")));

        runPromise( 
                () -> registry.register(agent, config("dereg-agent")).then(() -> registry.deregister("dereg-agent")));

        Optional<TypedAgent<String, String>> resolved = runPromise(() -> registry.resolve("dereg-agent"));
        assertFalse(resolved.isPresent()); 
    }

    private static AgentDescriptor descriptor(String id, Set<String> capabilities) { 
        return AgentDescriptor.builder() 
                .agentId(id) 
                .name("Test " + id) 
                .description("Test descriptor")
                .type(AgentType.DETERMINISTIC) 
                .capabilities(capabilities) 
                .build(); 
    }

    private static AgentConfig config(String id) { 
        return AgentConfig.builder() 
                .agentId(id) 
                .type(AgentType.DETERMINISTIC) 
                .timeout(Duration.ofSeconds(30)) 
                .maxRetries(3) 
                .build(); 
    }

    private static final class MockAgent implements TypedAgent<String, String> {
        private final AgentDescriptor descriptor;

        private MockAgent(AgentDescriptor descriptor) { 
            this.descriptor = descriptor;
        }

        @Override
        public AgentDescriptor descriptor() { 
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) { 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> shutdown() { 
            return Promise.complete(); 
        }

        @Override
        public Promise<HealthStatus> healthCheck() { 
            return Promise.of(HealthStatus.healthy("Agent is healthy"));
        }

        @Override
        public Promise<AgentResult<String>> process(AgentContext ctx, String input) { 
            return Promise.of(AgentResult.success( 
                    descriptor.getAgentId() + " processed: " + input, descriptor.getAgentId(), Duration.ZERO)); 
        }
    }

    private static final class InMemoryRegistry implements AgentRegistry {
        private final Map<String, TypedAgent<?, ?>> agents = new ConcurrentHashMap<>(); 
        private final Map<String, AgentConfig> configs = new ConcurrentHashMap<>(); 

        @Override
        public Promise<Void> register(TypedAgent<?, ?> agent, AgentConfig config) { 
            String agentId = agent.descriptor().getAgentId(); 
            agents.put(agentId, agent); 
            configs.put(agentId, config); 
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
            TypedAgent<I, O> typedAgent = (TypedAgent<I, O>) agents.get(agentId); 
            return Promise.of(Optional.ofNullable(typedAgent)); 
        }

        @Override
        public Promise<Set<String>> listAgentIds() { 
            return Promise.of(Set.copyOf(agents.keySet())); 
        }

        @Override
        public Promise<List<String>> findByCapability(String capability) { 
            List<String> matches = new ArrayList<>(); 
            for (TypedAgent<?, ?> agent : agents.values()) { 
                if (agent.descriptor().hasCapability(capability)) { 
                    matches.add(agent.descriptor().getAgentId()); 
                }
            }
            return Promise.of(matches); 
        }

        @Override
        public Promise<Map<String, Object>> getStats() { 
            Map<String, Object> stats = new HashMap<>(); 
            stats.put("registered", agents.size()); 
            stats.put("configured", configs.size()); 
            return Promise.of(stats); 
        }
    }
}
