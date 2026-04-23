/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
class PolicyBasedToolSandboxTest extends EventloopTestBase {

    @Mock private PolicyAsCodeEngine policyEngine;
    @Mock private ToolSandbox delegate;

    private PolicyBasedToolSandbox sandbox;

    @BeforeEach
    void setUp() { // GH-90000
        sandbox = new PolicyBasedToolSandbox(policyEngine, delegate); // GH-90000
        // Default: delegate succeeds
        lenient().when(delegate.execute(any(), any(), any(), any())) // GH-90000
            .thenReturn(Promise.of("ok"));
    }

    @Test
    @DisplayName("allowed policy result delegates execution to wrapped sandbox")
    void allowedPolicyDelegates() { // GH-90000
        when(policyEngine.evaluate(eq("tenant-1"), eq("tool_execution_policy"), any()))
            .thenReturn(Promise.of(PolicyEvalResult.allow("tool_execution_policy")));

        String result = runPromise(() -> // GH-90000
            sandbox.execute("tenant-1", "agent-1", "my-tool", Map.of("key", "value"))); // GH-90000

        assertThat(result).isEqualTo("ok");
        verify(delegate).execute("tenant-1", "agent-1", "my-tool", Map.of("key", "value")); // GH-90000
    }

    @Test
    @DisplayName("denied policy result rejects promise without calling delegate")
    void deniedPolicyBlocksExecution() { // GH-90000
        when(policyEngine.evaluate(eq("tenant-2"), eq("tool_execution_policy"), any()))
            .thenReturn(Promise.of(PolicyEvalResult.deny( // GH-90000
                "tool_execution_policy", List.of("tool not permitted for tenant"), 90)));

        Throwable thrown = assertThrows(IllegalArgumentException.class, // GH-90000
            () -> runPromise(() -> sandbox.execute("tenant-2", "agent-1", "my-tool", Map.of()))); // GH-90000

        assertThat(thrown.getMessage()) // GH-90000
            .contains("Tool execution denied by policy")
            .contains("tool_execution_policy");
        verify(delegate, never()).execute(any(), any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("policy engine failure propagates as promise failure")
    void policyEngineFailurePropagates() { // GH-90000
        RuntimeException policyError = new RuntimeException("policy store unavailable");
        when(policyEngine.evaluate(any(), any(), any())) // GH-90000
            .thenReturn(Promise.ofException(policyError)); // GH-90000

        Throwable thrown = assertThrows(RuntimeException.class, // GH-90000
            () -> runPromise(() -> sandbox.execute("tenant-3", "agent-1", "my-tool", Map.of()))); // GH-90000

        assertThat(thrown.getMessage()).contains("policy store unavailable");
        verify(delegate, never()).execute(any(), any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("constructor requires non-null policyEngine and delegate")
    void constructorRequiresNonNull() { // GH-90000
        assertThrows(NullPointerException.class, // GH-90000
            () -> new PolicyBasedToolSandbox(null, delegate)); // GH-90000
        assertThrows(NullPointerException.class, // GH-90000
            () -> new PolicyBasedToolSandbox(policyEngine, null)); // GH-90000
    }
}
