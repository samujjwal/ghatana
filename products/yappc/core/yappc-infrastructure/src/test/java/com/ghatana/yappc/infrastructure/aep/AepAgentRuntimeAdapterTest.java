/*
 * Copyright (c) 2025 Ghatana Technologies 
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
@ExtendWith(MockitoExtension.class) 
class AepAgentRuntimeAdapterTest {

    @Mock
    private AgentDispatcher delegate;

    @Mock
    private AgentContext context;

    private AepAgentRuntimeAdapter adapter;

    @BeforeEach
    void setUp() { 
        adapter = new AepAgentRuntimeAdapter(delegate); 
    }

    @Test
    @DisplayName("dispatch delegates to AgentDispatcher")
    void dispatch_delegatesToAgentDispatcher() { 
        String agentId = "code-gen-v1";
        String input = "write a hello-world function";
        AgentResult<String> expected = AgentResult.success("function hello() {}", agentId, Duration.ofMillis(100)); 
        when(delegate.<String, String>dispatch(agentId, input, context)) 
                .thenReturn(Promise.of(expected)); 

        Promise<AgentResult<String>> result = adapter.dispatch(agentId, input, context); 

        verify(delegate).dispatch(agentId, input, context); 
        assertThat(result).isNotNull(); 
    }

    @Test
    @DisplayName("dispatch forwards non-string payloads without coercion")
    void dispatch_forwardsArbitraryPayloadType() { 
        String agentId = "review-agent";
        Integer input = 42;
        AgentResult<Boolean> expected = AgentResult.success(true, agentId, Duration.ofMillis(50)); 
        when(delegate.<Integer, Boolean>dispatch(agentId, input, context)) 
                .thenReturn(Promise.of(expected)); 

        adapter.dispatch(agentId, input, context); 

        verify(delegate).dispatch(agentId, input, context); 
    }

    @Test
    @DisplayName("constructor rejects null delegate")
    void constructor_rejectsNullDelegate() { 
        assertThatThrownBy(() -> new AepAgentRuntimeAdapter(null)) 
                .isInstanceOf(NullPointerException.class); 
    }
}
