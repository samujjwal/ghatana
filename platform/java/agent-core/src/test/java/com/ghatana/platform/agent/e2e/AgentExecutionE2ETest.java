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
    void setUp() { // GH-90000
        testContext = AgentContext.empty(); // GH-90000
    }

    @Test
    @DisplayName("Echo agent should echo input unchanged")
    void testEchoAgent() { // GH-90000
        TestEchoAgent agent = new TestEchoAgent("echo-test");

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "hello") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("hello");
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
        assertThat(result.getAgentId()).isEqualTo("echo-test");
    }

    @Test
    @DisplayName("Transform agent should apply function")
    void testTransformAgent() { // GH-90000
        TestTransformAgent<String, String> agent =
                new TestTransformAgent<>("uppercase", String::toUpperCase); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "hello world") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("HELLO WORLD");
        assertThat(result.isSuccess()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Confidence agent should return specified confidence")
    void testConfidenceAgent() { // GH-90000
        TestConfidenceAgent<String> agent =
                new TestConfidenceAgent<>("conf-test", 0.75); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "input") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("input");
        assertThat(result.getConfidence()).isEqualTo(0.75); // GH-90000
        assertThat(result.getStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Failing agent should return failure status")
    void testFailingAgent() { // GH-90000
        Exception testError = new RuntimeException("Test failure");
        TestFailingAgent<String> agent =
                new TestFailingAgent<>("fail-test", testError); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "input") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.isFailed()).isTrue(); // GH-90000
        assertThat(result.getConfidence()).isEqualTo(0.0); // GH-90000
        assertThat(result.getExplanation()).contains("Test failure");
    }

    @Test
    @DisplayName("Delay agent should complete execution")
    void testDelayAgent() { // GH-90000
        java.time.Duration delay = java.time.Duration.ofMillis(100); // GH-90000
        TestDelayAgent<String> agent =
                new TestDelayAgent<>("delay-test", delay); // GH-90000

        AgentResult<String> result = runPromise(() -> // GH-90000
                agent.process(testContext, "data") // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getOutput()).isEqualTo("data");
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getProcessingTime()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Multiple sequential executions should work")
    void testSequentialExecutions() { // GH-90000
        TestEchoAgent agent = new TestEchoAgent("seq-test");

        AgentResult<String> result1 = runPromise(() -> // GH-90000
                agent.process(testContext, "first") // GH-90000
        );
        AgentResult<String> result2 = runPromise(() -> // GH-90000
                agent.process(testContext, "second") // GH-90000
        );
        AgentResult<String> result3 = runPromise(() -> // GH-90000
                agent.process(testContext, "third") // GH-90000
        );

        assertThat(result1.getOutput()).isEqualTo("first");
        assertThat(result2.getOutput()).isEqualTo("second");
        assertThat(result3.getOutput()).isEqualTo("third");
    }

    @Test
    @DisplayName("Batch execution via processBatch should work")
    void testBatchExecution() { // GH-90000
        TestEchoAgent agent = new TestEchoAgent("batch-test");

        java.util.List<String> inputs = java.util.Arrays.asList("a", "b", "c"); // GH-90000

        java.util.List<AgentResult<String>> results = runPromise(() -> // GH-90000
                agent.processBatch(testContext, inputs) // GH-90000
        );

        assertThat(results).hasSize(3); // GH-90000
        assertThat(results.get(0).getOutput()).isEqualTo("a");
        assertThat(results.get(1).getOutput()).isEqualTo("b");
        assertThat(results.get(2).getOutput()).isEqualTo("c");
    }
}
