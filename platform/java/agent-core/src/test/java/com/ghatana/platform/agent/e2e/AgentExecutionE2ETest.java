package com.ghatana.platform.agent.e2e;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 E2E Tests - Basic Agent Execution
 *
 * <p>Verifies that TypedAgent test doubles work correctly with EventloopTestBase.
 */
@DisplayName("[Phase 5] Basic Agent E2E Tests [GH-90000]")
class AgentExecutionE2ETest extends EventloopTestBase {

    private AgentContext testContext;

    @BeforeEach
    void setUp() { // GH-90000
        testContext = AgentContext.empty(); // GH-90000
    }

    @Test
    @DisplayName("Echo agent should echo input unchanged [GH-90000]")
    void testEchoAgent() { // GH-90000
        TestEchoAgent agent = new TestEchoAgent("echo-test [GH-90000]");

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "hello") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("hello [GH-90000]");
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
        assertThat(result.getAgentId()).isEqualTo("echo-test [GH-90000]");
    }

    @Test
    @DisplayName("Transform agent should apply function [GH-90000]")
    void testTransformAgent() { // GH-90000
        TestTransformAgent<String, String> agent =
                new TestTransformAgent<>("uppercase", String::toUpperCase); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "hello world") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("HELLO WORLD [GH-90000]");
        assertThat(result.isSuccess()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Confidence agent should return specified confidence [GH-90000]")
    void testConfidenceAgent() { // GH-90000
        TestConfidenceAgent<String> agent =
                new TestConfidenceAgent<>("conf-test", 0.75); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "input") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("input [GH-90000]");
        assertThat(result.getConfidence()).isEqualTo(0.75); // GH-90000
        assertThat(result.getStatus().name()).isEqualTo("SUCCESS [GH-90000]");
    }

    @Test
    @DisplayName("Failing agent should return failure status [GH-90000]")
    void testFailingAgent() { // GH-90000
        Exception testError = new RuntimeException("Test failure [GH-90000]");
        TestFailingAgent<String> agent =
                new TestFailingAgent<>("fail-test", testError); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "input") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.isFailed()).isTrue(); // GH-90000
        assertThat(result.getConfidence()).isEqualTo(0.0); // GH-90000
        assertThat(result.getExplanation()).contains("Test failure [GH-90000]");
    }

    @Test
    @DisplayName("Delay agent should complete execution [GH-90000]")
    void testDelayAgent() { // GH-90000
        java.time.Duration delay = java.time.Duration.ofMillis(100); // GH-90000
        TestDelayAgent<String> agent =
                new TestDelayAgent<>("delay-test", delay); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "data") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("data [GH-90000]");
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getProcessingTime()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Multiple sequential executions should work [GH-90000]")
    void testSequentialExecutions() { // GH-90000
        TestEchoAgent agent = new TestEchoAgent("seq-test [GH-90000]");

        AgentResult<String> result1 = runPromise(() -> // GH-90000
                agent.process(testContext, "first") // GH-90000
        );
        AgentResult<String> result2 = runPromise(() -> // GH-90000
                agent.process(testContext, "second") // GH-90000
        );
        AgentResult<String> result3 = runPromise(() -> // GH-90000
                agent.process(testContext, "third") // GH-90000
        );

        assertThat(result1.getOutput()).isEqualTo("first [GH-90000]");
        assertThat(result2.getOutput()).isEqualTo("second [GH-90000]");
        assertThat(result3.getOutput()).isEqualTo("third [GH-90000]");
    }

    @Test
    @DisplayName("Batch execution via processBatch should work [GH-90000]")
    void testBatchExecution() { // GH-90000
        TestEchoAgent agent = new TestEchoAgent("batch-test [GH-90000]");

        java.util.List<String> inputs = java.util.Arrays.asList("a", "b", "c"); // GH-90000

        java.util.List<AgentResult<String>> results = runPromise(() -> // GH-90000
                agent.processBatch(testContext, inputs) // GH-90000
        );

        assertThat(results).hasSize(3); // GH-90000
        assertThat(results.get(0).getOutput()).isEqualTo("a [GH-90000]");
        assertThat(results.get(1).getOutput()).isEqualTo("b [GH-90000]");
        assertThat(results.get(2).getOutput()).isEqualTo("c [GH-90000]");
    }
}
