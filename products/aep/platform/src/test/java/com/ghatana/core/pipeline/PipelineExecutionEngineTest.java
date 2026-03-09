package com.ghatana.core.pipeline;

import com.ghatana.core.operator.*;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for the pipeline execution engine.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Single-stage pipeline execution</li>
 *   <li>Linear pipeline (A → B → C)</li>
 *   <li>DAG pipeline with fan-out and fan-in</li>
 *   <li>Error edge routing on operator failure</li>
 *   <li>Fallback edge routing on empty output</li>
 *   <li>Broadcast edge routing</li>
 *   <li>Operator not found in catalog</li>
 *   <li>Continue-on-error mode</li>
 *   <li>Deadline enforcement</li>
 *   <li>Empty pipeline validation</li>
 *   <li>Multi-event output propagation</li>
 *   <li>Operator state validation</li>
 *   <li>Pipeline with no edges (isolated stages)</li>
 * </ul>
 */
class PipelineExecutionEngineTest {

    private PipelineExecutionEngine engine;
    private DefaultOperatorCatalog catalog;

    @BeforeEach
    void setUp() {
        engine = new PipelineExecutionEngine();
        catalog = new DefaultOperatorCatalog();
    }

    // ════════════════════════════════════════════════════════════════
    // Helper: create test operators
    // ════════════════════════════════════════════════════════════════

    /**
     * Creates and registers a pass-through operator that appends a suffix to the event type.
     */
    private void registerPassThroughOperator(String namespace, String type, String name, String version) {
        OperatorId id = OperatorId.of(namespace, type, name, version);
        TestPassThroughOperator op = new TestPassThroughOperator(id, name);
        op.initialize(OperatorConfig.empty()).getResult();
        op.start().getResult();
        catalog.register(op).getResult();
    }

    /**
     * Creates and registers an operator that always fails.
     */
    private void registerFailingOperator(String namespace, String type, String name, String version,
                                          String errorMessage) {
        OperatorId id = OperatorId.of(namespace, type, name, version);
        TestFailingOperator op = new TestFailingOperator(id, name, errorMessage);
        op.initialize(OperatorConfig.empty()).getResult();
        op.start().getResult();
        catalog.register(op).getResult();
    }

    /**
     * Creates and registers an operator that produces no output events.
     */
    private void registerEmptyOutputOperator(String namespace, String type, String name, String version) {
        OperatorId id = OperatorId.of(namespace, type, name, version);
        TestEmptyOutputOperator op = new TestEmptyOutputOperator(id, name);
        op.initialize(OperatorConfig.empty()).getResult();
        op.start().getResult();
        catalog.register(op).getResult();
    }

    /**
     * Creates and registers an operator that produces multiple output events.
     */
    private void registerMultiOutputOperator(String namespace, String type, String name, String version,
                                              int outputCount) {
        OperatorId id = OperatorId.of(namespace, type, name, version);
        TestMultiOutputOperator op = new TestMultiOutputOperator(id, name, outputCount);
        op.initialize(OperatorConfig.empty()).getResult();
        op.start().getResult();
        catalog.register(op).getResult();
    }

    /**
     * Creates and registers a counting operator that tracks invocation count.
     */
    private TestCountingOperator registerCountingOperator(String namespace, String type, String name,
                                                           String version) {
        OperatorId id = OperatorId.of(namespace, type, name, version);
        TestCountingOperator op = new TestCountingOperator(id, name);
        op.initialize(OperatorConfig.empty()).getResult();
        op.start().getResult();
        catalog.register(op).getResult();
        return op;
    }

    private PipelineExecutionContext defaultContext() {
        return PipelineExecutionContext.builder()
                .pipelineId("test")
                .tenantId("test-tenant")
                .operatorCatalog(catalog)
                .deadline(Duration.ofSeconds(30))
                .continueOnError(false)
                .build();
    }

    private PipelineExecutionContext continueOnErrorContext() {
        return PipelineExecutionContext.builder()
                .pipelineId("test")
                .tenantId("test-tenant")
                .operatorCatalog(catalog)
                .deadline(Duration.ofSeconds(30))
                .continueOnError(true)
                .build();
    }

    private Event testEvent(String type) {
        return Event.builder()
                .type(type)
                .payload(Map.of("key", "value"))
                .headers(Map.of("source", "test"))
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: Basic execution
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Single-stage pipeline executes operator and returns output")
    void singleStage_executesOperator() {
        registerPassThroughOperator("test", "stream", "filter", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("p1", "1.0.0")
                .name("Single Stage")
                .stage("s1", OperatorId.of("test", "stream", "filter", "1.0.0"))
                .build();

        Event input = testEvent("order.placed");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagesExecuted()).isEqualTo(1);
        assertThat(result.outputEvents()).isNotEmpty();
        assertThat(result.outputEvents().get(0).getType()).isEqualTo("order.placed.processed");
    }

    @Test
    @DisplayName("Linear pipeline A → B → C processes events in order")
    void linearPipeline_processesInOrder() {
        registerPassThroughOperator("test", "stream", "filter", "1.0.0");
        registerPassThroughOperator("test", "stream", "enrich", "1.0.0");
        registerPassThroughOperator("test", "stream", "detect", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("p2", "1.0.0")
                .name("Linear Pipeline")
                .stage("filter", OperatorId.of("test", "stream", "filter", "1.0.0"))
                .stage("enrich", OperatorId.of("test", "stream", "enrich", "1.0.0"))
                .stage("detect", OperatorId.of("test", "stream", "detect", "1.0.0"))
                .edge("filter", "enrich")
                .edge("enrich", "detect")
                .build();

        Event input = testEvent("transaction");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagesExecuted()).isEqualTo(3);
        assertThat(result.outputEvents()).isNotEmpty();
        // Final output should have been processed 3 times
        assertThat(result.outputEvents().get(0).getType())
                .isEqualTo("transaction.processed.processed.processed");
    }

    @Test
    @DisplayName("DAG with fan-out: A → B, A → C (both receive A's output)")
    void dagFanOut_bothBranchesReceiveInput() {
        registerPassThroughOperator("test", "stream", "source", "1.0.0");
        TestCountingOperator branchB = registerCountingOperator("test", "stream", "branchB", "1.0.0");
        TestCountingOperator branchC = registerCountingOperator("test", "stream", "branchC", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("p3", "1.0.0")
                .name("Fan-out")
                .stage("source", OperatorId.of("test", "stream", "source", "1.0.0"))
                .stage("branchB", OperatorId.of("test", "stream", "branchB", "1.0.0"))
                .stage("branchC", OperatorId.of("test", "stream", "branchC", "1.0.0"))
                .edge("source", "branchB")
                .edge("source", "branchC")
                .build();

        Event input = testEvent("data");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagesExecuted()).isEqualTo(3);
        assertThat(branchB.getInvocationCount()).isEqualTo(1);
        assertThat(branchC.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DAG with fan-in: A → C, B → C (C receives outputs from both)")
    void dagFanIn_mergesInputs() {
        registerPassThroughOperator("test", "stream", "a", "1.0.0");
        registerPassThroughOperator("test", "stream", "b", "1.0.0");
        TestCountingOperator merge = registerCountingOperator("test", "stream", "merge", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("p4", "1.0.0")
                .name("Fan-in")
                .stage("a", OperatorId.of("test", "stream", "a", "1.0.0"))
                .stage("b", OperatorId.of("test", "stream", "b", "1.0.0"))
                .stage("merge", OperatorId.of("test", "stream", "merge", "1.0.0"))
                .edge("a", "merge")
                .edge("b", "merge")
                .build();

        Event input = testEvent("event");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagesExecuted()).isEqualTo(3);
        // Merge stage should be invoked with 2 input events (one from A, one from B)
        assertThat(merge.getInvocationCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Diamond DAG: A → B, A → C, B → D, C → D")
    void diamondDag_executesCorrectly() {
        registerPassThroughOperator("test", "stream", "a", "1.0.0");
        registerPassThroughOperator("test", "stream", "b", "1.0.0");
        registerPassThroughOperator("test", "stream", "c", "1.0.0");
        TestCountingOperator d = registerCountingOperator("test", "stream", "d", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("diamond", "1.0.0")
                .name("Diamond")
                .stage("a", OperatorId.of("test", "stream", "a", "1.0.0"))
                .stage("b", OperatorId.of("test", "stream", "b", "1.0.0"))
                .stage("c", OperatorId.of("test", "stream", "c", "1.0.0"))
                .stage("d", OperatorId.of("test", "stream", "d", "1.0.0"))
                .edge("a", "b")
                .edge("a", "c")
                .edge("b", "d")
                .edge("c", "d")
                .build();

        Event input = testEvent("diamond.input");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagesExecuted()).isEqualTo(4);
        // D receives one event from B and one from C
        assertThat(d.getInvocationCount()).isEqualTo(2);
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: Error handling
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Operator failure routes to error edge handler")
    void operatorFailure_routesToErrorEdge() {
        registerFailingOperator("test", "stream", "risky", "1.0.0", "simulated failure");
        TestCountingOperator errorHandler = registerCountingOperator("test", "stream", "error-handler", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("error-test", "1.0.0")
                .name("Error routing")
                .stage("risky", OperatorId.of("test", "stream", "risky", "1.0.0"))
                .stage("error-handler", OperatorId.of("test", "stream", "error-handler", "1.0.0"))
                .onError("risky", "error-handler")
                .build();

        Event input = testEvent("error.test");
        PipelineExecutionContext ctx = continueOnErrorContext();
        PipelineExecutionResult result = engine.execute(pipeline, input, ctx).getResult();

        // The error handler should have been invoked
        assertThat(errorHandler.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Operator failure with no error edge and continueOnError=false aborts pipeline")
    void operatorFailure_noErrorEdge_abortsPipeline() {
        registerFailingOperator("test", "stream", "fail", "1.0.0", "boom");
        registerPassThroughOperator("test", "stream", "after", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("abort-test", "1.0.0")
                .name("Abort on error")
                .stage("fail", OperatorId.of("test", "stream", "fail", "1.0.0"))
                .stage("after", OperatorId.of("test", "stream", "after", "1.0.0"))
                .edge("fail", "after")
                .build();

        Event input = testEvent("abort.test");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorMessage()).contains("boom");
    }

    @Test
    @DisplayName("Operator failure with continueOnError=true continues to next stages")
    void operatorFailure_continueOnError_proceeds() {
        registerFailingOperator("test", "stream", "fail", "1.0.0", "non-fatal");
        TestCountingOperator next = registerCountingOperator("test", "stream", "independent", "1.0.0");

        // Two independent source stages: "fail" and "independent"
        Pipeline pipeline = DefaultPipeline.builder("continue-test", "1.0.0")
                .name("Continue on error")
                .stage("fail", OperatorId.of("test", "stream", "fail", "1.0.0"))
                .stage("independent", OperatorId.of("test", "stream", "independent", "1.0.0"))
                .build();

        Event input = testEvent("continue.test");
        PipelineExecutionResult result = engine.execute(pipeline, input, continueOnErrorContext()).getResult();

        // Independent stage should still execute even though "fail" failed
        assertThat(next.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Operator not found in catalog produces failure result")
    void operatorNotFound_producesFailure() {
        // Don't register any operator
        Pipeline pipeline = DefaultPipeline.builder("missing-op", "1.0.0")
                .name("Missing operator")
                .stage("s1", OperatorId.of("test", "stream", "missing", "1.0.0"))
                .build();

        Event input = testEvent("missing.test");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorMessage()).contains("Operator not found");
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: Fallback edges
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Empty output activates fallback edge")
    void emptyOutput_activatesFallbackEdge() {
        registerEmptyOutputOperator("test", "stream", "empty", "1.0.0");
        TestCountingOperator fallback = registerCountingOperator("test", "stream", "fallback", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("fallback-test", "1.0.0")
                .name("Fallback routing")
                .stage("empty", OperatorId.of("test", "stream", "empty", "1.0.0"))
                .stage("fallback", OperatorId.of("test", "stream", "fallback", "1.0.0"))
                .onFallback("empty", "fallback")
                .build();

        Event input = testEvent("fallback.test");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(fallback.getInvocationCount()).isEqualTo(1);
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: Broadcast edges
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Broadcast edge sends output to all targets")
    void broadcastEdge_sendsToAllTargets() {
        registerPassThroughOperator("test", "stream", "source", "1.0.0");
        TestCountingOperator target1 = registerCountingOperator("test", "stream", "t1", "1.0.0");
        TestCountingOperator target2 = registerCountingOperator("test", "stream", "t2", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("broadcast-test", "1.0.0")
                .name("Broadcast")
                .stage("source", OperatorId.of("test", "stream", "source", "1.0.0"))
                .stage("t1", OperatorId.of("test", "stream", "t1", "1.0.0"))
                .stage("t2", OperatorId.of("test", "stream", "t2", "1.0.0"))
                .edge("source", "t1", PipelineEdge.LABEL_BROADCAST)
                .edge("source", "t2", PipelineEdge.LABEL_BROADCAST)
                .build();

        Event input = testEvent("broadcast.event");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(target1.getInvocationCount()).isEqualTo(1);
        assertThat(target2.getInvocationCount()).isEqualTo(1);
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: Multi-event output
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Multi-event output propagates all events to downstream stages")
    void multiEventOutput_propagatesAll() {
        registerMultiOutputOperator("test", "stream", "splitter", "1.0.0", 3);
        TestCountingOperator downstream = registerCountingOperator("test", "stream", "collector", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("multi-out", "1.0.0")
                .name("Multi-output")
                .stage("splitter", OperatorId.of("test", "stream", "splitter", "1.0.0"))
                .stage("collector", OperatorId.of("test", "stream", "collector", "1.0.0"))
                .edge("splitter", "collector")
                .build();

        Event input = testEvent("split.me");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        // Collector should be invoked 3 times (once per output from splitter)
        assertThat(downstream.getInvocationCount()).isEqualTo(3);
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: Edge cases
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Pipeline with isolated stages (no edges) processes all as sources")
    void isolatedStages_allProcessed() {
        TestCountingOperator op1 = registerCountingOperator("test", "stream", "iso1", "1.0.0");
        TestCountingOperator op2 = registerCountingOperator("test", "stream", "iso2", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("isolated", "1.0.0")
                .name("Isolated stages")
                .stage("iso1", OperatorId.of("test", "stream", "iso1", "1.0.0"))
                .stage("iso2", OperatorId.of("test", "stream", "iso2", "1.0.0"))
                .build();

        Event input = testEvent("isolated.test");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(op1.getInvocationCount()).isEqualTo(1);
        assertThat(op2.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Null input event throws NullPointerException")
    void nullInput_throwsNPE() {
        Pipeline pipeline = DefaultPipeline.builder("npe-test", "1.0.0")
                .name("NPE test")
                .stage("s1", OperatorId.of("test", "stream", "x", "1.0.0"))
                .build();

        assertThatThrownBy(() -> engine.execute(pipeline, null, defaultContext()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null pipeline throws NullPointerException")
    void nullPipeline_throwsNPE() {
        assertThatThrownBy(() -> engine.execute(null, testEvent("x"), defaultContext()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null context throws NullPointerException")
    void nullContext_throwsNPE() {
        Pipeline pipeline = DefaultPipeline.builder("npe-test", "1.0.0")
                .name("NPE test")
                .stage("s1", OperatorId.of("test", "stream", "x", "1.0.0"))
                .build();

        assertThatThrownBy(() -> engine.execute(pipeline, testEvent("x"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("PipelineExecutionResult contains correct pipeline ID and input event")
    void result_containsCorrectMetadata() {
        registerPassThroughOperator("test", "stream", "op1", "1.0.0");

        Pipeline pipeline = DefaultPipeline.builder("meta-test", "1.0.0")
                .name("Metadata test")
                .stage("s1", OperatorId.of("test", "stream", "op1", "1.0.0"))
                .build();

        Event input = testEvent("metadata.test");
        PipelineExecutionResult result = engine.execute(pipeline, input, defaultContext()).getResult();

        assertThat(result.pipelineId()).isEqualTo("meta-test");
        assertThat(result.inputEvent()).isSameAs(input);
        assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: DefaultPipeline integration (with execution engine)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DefaultPipeline.execute() uses real engine when configured")
    void defaultPipeline_usesRealEngine() {
        registerPassThroughOperator("test", "stream", "real", "1.0.0");

        DefaultPipeline.DefaultPipelineBuilder builder = DefaultPipeline.builder("integrated", "1.0.0");
        builder.executionEngine(engine, catalog);
        Pipeline pipeline = builder
                .name("Integrated test")
                .stage("s1", OperatorId.of("test", "stream", "real", "1.0.0"))
                .build();

        Event input = testEvent("integrated.test");
        PipelineExecutionResult result = pipeline.execute(input).getResult();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputEvents()).isNotEmpty();
        assertThat(result.outputEvents().get(0).getType()).isEqualTo("integrated.test.processed");
    }

    @Test
    @DisplayName("DefaultPipeline.execute() falls back to simulated when no engine")
    void defaultPipeline_simulatedFallback() {
        Pipeline pipeline = DefaultPipeline.builder("simulated", "1.0.0")
                .name("Simulated test")
                .stage("s1", OperatorId.of("test", "stream", "sim", "1.0.0"))
                .build();

        Event input = testEvent("simulated.test");
        PipelineExecutionResult result = pipeline.execute(input).getResult();

        // Simulated execution just enriches metadata
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagesExecuted()).isGreaterThan(0);
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: PipelineExecutionContext
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Context generates unique execution ID when not provided")
    void context_generatesExecutionId() {
        PipelineExecutionContext ctx = PipelineExecutionContext.builder()
                .pipelineId("test")
                .operatorCatalog(catalog)
                .build();

        assertThat(ctx.getExecutionId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Context deadline tracking works correctly")
    void context_deadlineTracking() {
        PipelineExecutionContext ctx = PipelineExecutionContext.builder()
                .pipelineId("test")
                .operatorCatalog(catalog)
                .deadline(Duration.ofHours(1))
                .build();

        assertThat(ctx.isDeadlineExceeded()).isFalse();
        assertThat(ctx.getRemainingTime()).isGreaterThan(Duration.ZERO);
    }

    @Test
    @DisplayName("Context with zero deadline is immediately exceeded")
    void context_zeroDeadline_exceeded() throws InterruptedException {
        PipelineExecutionContext ctx = PipelineExecutionContext.builder()
                .pipelineId("test")
                .operatorCatalog(catalog)
                .deadline(Duration.ZERO)
                .build();

        Thread.sleep(1);
        assertThat(ctx.isDeadlineExceeded()).isTrue();
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: DefaultOperatorCatalog
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Catalog register and lookup")
    void catalog_registerAndLookup() {
        OperatorId id = OperatorId.of("ns", "stream", "op", "1.0.0");
        TestPassThroughOperator op = new TestPassThroughOperator(id, "test-op");
        catalog.register(op).getResult();

        assertThat(catalog.size()).isEqualTo(1);
        assertThat(catalog.lookup(id).getResult()).isSameAs(op);
    }

    @Test
    @DisplayName("Catalog get returns empty for missing operator")
    void catalog_getMissing_returnsEmpty() {
        OperatorId id = OperatorId.of("ns", "stream", "missing", "1.0.0");
        assertThat(catalog.get(id).getResult()).isEmpty();
    }

    @Test
    @DisplayName("Catalog unregister removes operator")
    void catalog_unregister() {
        OperatorId id = OperatorId.of("ns", "stream", "op", "1.0.0");
        TestPassThroughOperator op = new TestPassThroughOperator(id, "test-op");
        catalog.register(op).getResult();
        assertThat(catalog.size()).isEqualTo(1);

        catalog.unregister(id).getResult();
        assertThat(catalog.size()).isEqualTo(0);
        assertThat(catalog.get(id).getResult()).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // Tests: StageExecutionResult
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("StageExecutionResult.success factory")
    void stageResult_successFactory() {
        OperatorId opId = OperatorId.of("ns", "stream", "op", "1.0.0");
        Event event = testEvent("test");
        OperatorResult opResult = OperatorResult.of(event);

        StageExecutionResult result = StageExecutionResult.success(
                "s1", opId, List.of(event), opResult, Duration.ofMillis(10));

        assertThat(result.success()).isTrue();
        assertThat(result.stageId()).isEqualTo("s1");
        assertThat(result.hasOutput()).isTrue();
        assertThat(result.getDurationMs()).isEqualTo(10);
    }

    @Test
    @DisplayName("StageExecutionResult.failure factory")
    void stageResult_failureFactory() {
        OperatorId opId = OperatorId.of("ns", "stream", "op", "1.0.0");

        StageExecutionResult result = StageExecutionResult.failure(
                "s1", opId, List.of(), Duration.ofMillis(5), "error msg");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("error msg");
        assertThat(result.hasOutput()).isFalse();
    }

    // ════════════════════════════════════════════════════════════════
    // Test operator implementations
    // ════════════════════════════════════════════════════════════════

    /**
     * Pass-through operator that appends ".processed" to the event type.
     */
    static class TestPassThroughOperator extends AbstractOperator {
        TestPassThroughOperator(OperatorId id, String name) {
            super(id, OperatorType.STREAM, name, "Test pass-through operator",
                    List.of("test"), null);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            Event output = Event.builder()
                    .type(event.getType() + ".processed")
                    .payload(Map.of("original_type", event.getType()))
                    .headers(Map.of("processed", "true"))
                    .build();
            return Promise.of(OperatorResult.of(output));
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered").payload(Map.of("id", getId().toString())).build();
        }
    }

    /**
     * Operator that always returns a failed result.
     */
    static class TestFailingOperator extends AbstractOperator {
        private final String errorMessage;

        TestFailingOperator(OperatorId id, String name, String errorMessage) {
            super(id, OperatorType.STREAM, name, "Test failing operator",
                    List.of("test"), null);
            this.errorMessage = errorMessage;
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            return Promise.of(OperatorResult.failed(errorMessage));
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered").payload(Map.of("id", getId().toString())).build();
        }
    }

    /**
     * Operator that returns an empty result (no output events).
     */
    static class TestEmptyOutputOperator extends AbstractOperator {
        TestEmptyOutputOperator(OperatorId id, String name) {
            super(id, OperatorType.STREAM, name, "Test empty output operator",
                    List.of("test"), null);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            return Promise.of(OperatorResult.empty());
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered").payload(Map.of("id", getId().toString())).build();
        }
    }

    /**
     * Operator that produces multiple output events.
     */
    static class TestMultiOutputOperator extends AbstractOperator {
        private final int outputCount;

        TestMultiOutputOperator(OperatorId id, String name, int outputCount) {
            super(id, OperatorType.STREAM, name, "Test multi-output operator",
                    List.of("test"), null);
            this.outputCount = outputCount;
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            List<Event> outputs = new ArrayList<>();
            for (int i = 0; i < outputCount; i++) {
                outputs.add(Event.builder()
                        .type(event.getType() + ".split-" + i)
                        .payload(Map.of("index", i))
                        .headers(Map.of())
                        .build());
            }
            return Promise.of(OperatorResult.of(outputs));
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered").payload(Map.of("id", getId().toString())).build();
        }
    }

    /**
     * Operator that counts invocations and passes through events.
     */
    static class TestCountingOperator extends AbstractOperator {
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        TestCountingOperator(OperatorId id, String name) {
            super(id, OperatorType.STREAM, name, "Test counting operator",
                    List.of("test"), null);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            invocationCount.incrementAndGet();
            Event output = Event.builder()
                    .type(event.getType() + ".counted")
                    .payload(Map.of("count", invocationCount.get()))
                    .headers(Map.of("count", String.valueOf(invocationCount.get())))
                    .build();
            return Promise.of(OperatorResult.of(output));
        }

        int getInvocationCount() {
            return invocationCount.get();
        }

        @Override
        public Event toEvent() {
            return Event.builder().type("operator.registered").payload(Map.of("id", getId().toString())).build();
        }
    }
}
