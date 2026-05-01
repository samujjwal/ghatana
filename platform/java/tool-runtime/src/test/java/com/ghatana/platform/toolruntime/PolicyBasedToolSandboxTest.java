/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for PolicyBasedToolSandbox policy gate and delegation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PolicyBasedToolSandbox")
@ExtendWith(MockitoExtension.class) 
class PolicyBasedToolSandboxTest extends EventloopTestBase {

    @Mock private PolicyAsCodeEngine policyEngine;
    @Mock private ToolSandbox delegate;

    private PolicyBasedToolSandbox sandbox;

    @BeforeEach
    void setUp() { 
        sandbox = new PolicyBasedToolSandbox(policyEngine, delegate); 
        // Default: delegate succeeds
        lenient().when(delegate.execute(any(), any(), any(), any())) 
            .thenReturn(Promise.of("ok"));
    }

    @Test
    @DisplayName("allowed policy result delegates execution to wrapped sandbox")
    void allowedPolicyDelegates() { 
        when(policyEngine.evaluate(eq("tenant-1"), eq("tool_execution_policy"), any()))
            .thenReturn(Promise.of(PolicyEvalResult.allow("tool_execution_policy")));

        String result = runPromise(() -> 
            sandbox.execute("tenant-1", "agent-1", "my-tool", Map.of("key", "value"))); 

        assertThat(result).isEqualTo("ok");
        verify(delegate).execute("tenant-1", "agent-1", "my-tool", Map.of("key", "value")); 
    }

    @Test
    @DisplayName("denied policy result rejects promise without calling delegate")
    void deniedPolicyBlocksExecution() { 
        when(policyEngine.evaluate(eq("tenant-2"), eq("tool_execution_policy"), any()))
            .thenReturn(Promise.of(PolicyEvalResult.deny( 
                "tool_execution_policy", List.of("tool not permitted for tenant"), 90)));

        Throwable thrown = assertThrows(IllegalArgumentException.class, 
            () -> runPromise(() -> sandbox.execute("tenant-2", "agent-1", "my-tool", Map.of()))); 

        assertThat(thrown.getMessage()) 
            .contains("Tool execution denied by policy")
            .contains("tool_execution_policy");
        verify(delegate, never()).execute(any(), any(), any(), any()); 
    }

    @Test
    @DisplayName("policy engine failure propagates as promise failure")
    void policyEngineFailurePropagates() { 
        RuntimeException policyError = new RuntimeException("policy store unavailable");
        when(policyEngine.evaluate(any(), any(), any())) 
            .thenReturn(Promise.ofException(policyError)); 

        Throwable thrown = assertThrows(RuntimeException.class, 
            () -> runPromise(() -> sandbox.execute("tenant-3", "agent-1", "my-tool", Map.of()))); 

        assertThat(thrown.getMessage()).contains("policy store unavailable");
        verify(delegate, never()).execute(any(), any(), any(), any()); 
    }

    @Test
    @DisplayName("constructor requires non-null policyEngine and delegate")
    void constructorRequiresNonNull() { 
        assertThrows(NullPointerException.class, 
            () -> new PolicyBasedToolSandbox(null, delegate)); 
        assertThrows(NullPointerException.class, 
            () -> new PolicyBasedToolSandbox(policyEngine, null)); 
    }
}
