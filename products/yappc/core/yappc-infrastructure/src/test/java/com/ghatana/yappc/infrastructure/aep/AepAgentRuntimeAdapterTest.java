/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Infrastructure — AEP Runtime Adapter Tests
 */
package com.ghatana.yappc.infrastructure.aep;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link AepAgentRuntimeAdapter} correctly delegates
 * {@link com.ghatana.yappc.agent.spi.AgentRuntimePort#dispatch} to
 * the underlying {@link AgentDispatcher}.
 */
@DisplayName("AepAgentRuntimeAdapter — delegation contract")
@ExtendWith(MockitoExtension.class) // GH-90000
class AepAgentRuntimeAdapterTest {

    @Mock
    private AgentDispatcher delegate;

    @Mock
    private AgentContext context;

    private AepAgentRuntimeAdapter adapter;

    @BeforeEach
    void setUp() { // GH-90000
        adapter = new AepAgentRuntimeAdapter(delegate); // GH-90000
    }

    @Test
    @DisplayName("dispatch delegates to AgentDispatcher")
    void dispatch_delegatesToAgentDispatcher() { // GH-90000
        String agentId = "code-gen-v1";
        String input = "write a hello-world function";
        AgentResult<String> expected = AgentResult.success("function hello() {}", agentId, Duration.ofMillis(100)); // GH-90000
        when(delegate.<String, String>dispatch(agentId, input, context)) // GH-90000
                .thenReturn(Promise.of(expected)); // GH-90000

        Promise<AgentResult<String>> result = adapter.dispatch(agentId, input, context); // GH-90000

        verify(delegate).dispatch(agentId, input, context); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("dispatch forwards non-string payloads without coercion")
    void dispatch_forwardsArbitraryPayloadType() { // GH-90000
        String agentId = "review-agent";
        Integer input = 42;
        AgentResult<Boolean> expected = AgentResult.success(true, agentId, Duration.ofMillis(50)); // GH-90000
        when(delegate.<Integer, Boolean>dispatch(agentId, input, context)) // GH-90000
                .thenReturn(Promise.of(expected)); // GH-90000

        adapter.dispatch(agentId, input, context); // GH-90000

        verify(delegate).dispatch(agentId, input, context); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null delegate")
    void constructor_rejectsNullDelegate() { // GH-90000
        assertThatThrownBy(() -> new AepAgentRuntimeAdapter(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
