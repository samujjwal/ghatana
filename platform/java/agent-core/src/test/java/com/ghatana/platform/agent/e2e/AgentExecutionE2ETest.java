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
@DisplayName("[Phase 5] Basic Agent E2E Tests")
class AgentExecutionE2ETest extends EventloopTestBase {

    private AgentContext testContext;

    @BeforeEach
    void setUp() {
        testContext = AgentContext.empty();
    }

    @Test
    @DisplayName("Echo agent should echo input unchanged")
    void testEchoAgent() {
        TestEchoAgent agent = new TestEchoAgent("echo-test");

        AgentResult<String> result = runPromise(() ->
                agent.process(testContext, "hello")
        );

        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isEqualTo("hello");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getAgentId()).isEqualTo("echo-test");
    }

    @Test
    @DisplayName("Transform agent should apply function")
    void testTransformAgent() {
        TestTransformAgent<String, String> agent =
                new TestTransformAgent<>("uppercase", String::toUpperCase);

        AgentResult<String> result = runPromise(() ->
                agent.process(testContext, "hello world")
        );

        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isEqualTo("HELLO WORLD");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Confidence agent should return specified confidence")
    void testConfidenceAgent() {
        TestConfidenceAgent<String> agent =
                new TestConfidenceAgent<>("conf-test", 0.75);

        AgentResult<String> result = runPromise(() ->
                agent.process(testContext, "input")
        );

        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isEqualTo("input");
        assertThat(result.getConfidence()).isEqualTo(0.75);
        assertThat(result.getStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Failing agent should return failure status")
    void testFailingAgent() {
        Exception testError = new RuntimeException("Test failure");
        TestFailingAgent<String> agent =
                new TestFailingAgent<>("fail-test", testError);

        AgentResult<String> result = runPromise(() ->
                agent.process(testContext, "input")
        );

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.getExplanation()).contains("Test failure");
    }

    @Test
    @DisplayName("Delay agent should complete execution")
    void testDelayAgent() {
        java.time.Duration delay = java.time.Duration.ofMillis(100);
        TestDelayAgent<String> agent =
                new TestDelayAgent<>("delay-test", delay);

        AgentResult<String> result = runPromise(() ->
                agent.process(testContext, "data")
        );

        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isEqualTo("data");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProcessingTime()).isNotNull();
    }

    @Test
    @DisplayName("Multiple sequential executions should work")
    void testSequentialExecutions() {
        TestEchoAgent agent = new TestEchoAgent("seq-test");

        AgentResult<String> result1 = runPromise(() ->
                agent.process(testContext, "first")
        );
        AgentResult<String> result2 = runPromise(() ->
                agent.process(testContext, "second")
        );
        AgentResult<String> result3 = runPromise(() ->
                agent.process(testContext, "third")
        );

        assertThat(result1.getOutput()).isEqualTo("first");
        assertThat(result2.getOutput()).isEqualTo("second");
        assertThat(result3.getOutput()).isEqualTo("third");
    }

    @Test
    @DisplayName("Batch execution via processBatch should work")
    void testBatchExecution() {
        TestEchoAgent agent = new TestEchoAgent("batch-test");

        java.util.List<String> inputs = java.util.Arrays.asList("a", "b", "c");

        java.util.List<AgentResult<String>> results = runPromise(() ->
                agent.processBatch(testContext, inputs)
        );

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getOutput()).isEqualTo("a");
        assertThat(results.get(1).getOutput()).isEqualTo("b");
        assertThat(results.get(2).getOutput()).isEqualTo("c");
    }
}
