package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Dry-run testing environment for workflows: TASK steps return mock responses.
 *              Supports assertion steps, scenario execution (happy/error/timeout/compensation).
 *              Produces a per-step test report.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; Promise.ofBlocking
 *
 * STORY-W01-016: Workflow testing environment
 */
public class WorkflowTestingEnvironmentService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface WorkflowRunnerPort {
        String startDryRun(String workflowName, Map<String, Object> input, Map<String, Object> mockRegistry) throws Exception;
        List<StepExecutionRecord> getDryRunSteps(String dryRunId) throws Exception;
        DryRunStatus getStatus(String dryRunId) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum ScenarioType { HAPPY_PATH, ERROR_PATH, TIMEOUT_PATH, COMPENSATION_PATH }
    public enum DryRunStatus { RUNNING, COMPLETED, FAILED }
    public enum AssertionStatus { PASS, FAIL, SKIP }

    public record StepMock(String stepId, Object mockResponse, String failureReason, Long artificialDelayMs) {}

    public record TestScenario(
        String scenarioName,
        ScenarioType type,
        Map<String, Object> input,
        List<StepMock> mocks,
        List<StepAssertion> assertions
    ) {}

    public record StepAssertion(String stepId, String field, String operator, Object expectedValue) {}

    public record StepExecutionRecord(
        String stepId,
        String stepType,
        boolean succeeded,
        Object mockResponseUsed,
        long latencyMs,
        String error
    ) {}

    public record AssertionResult(StepAssertion assertion, AssertionStatus status, String actualValue, String detail) {}

    public record StepTestResult(
        StepExecutionRecord execution,
        List<AssertionResult> assertions,
        boolean passed
    ) {}

    public record WorkflowTestReport(
        String workflowName,
        String scenarioName,
        ScenarioType scenarioType,
        String dryRunId,
        boolean allPassed,
        List<StepTestResult> stepResults,
        String runAt
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final WorkflowRunnerPort runner;
    private final Executor executor;
    private final Counter testRunCounter;
    private final Counter scenarioPassCounter;
    private final Counter scenarioFailCounter;

    public WorkflowTestingEnvironmentService(
        WorkflowRunnerPort runner,
        MeterRegistry registry,
        Executor executor
    ) {
        this.runner   = runner;
        this.executor = executor;
        this.testRunCounter      = Counter.builder("workflow.test.runs").register(registry);
        this.scenarioPassCounter = Counter.builder("workflow.test.scenario.pass").register(registry);
        this.scenarioFailCounter = Counter.builder("workflow.test.scenario.fail").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Register a mock response for a step in dry-run mode.
     * DSL: mockStep("settlement.dvp", Map.of("status", "SETTLED"))
     */
    public StepMock mockStep(String stepId, Object response) {
        return new StepMock(stepId, response, null, null);
    }

    public StepMock mockStepFailure(String stepId, String failureReason) {
        return new StepMock(stepId, null, failureReason, null);
    }

    public StepMock mockStepTimeout(String stepId, long delayMs) {
        return new StepMock(stepId, null, "TIMEOUT", delayMs);
    }

    /**
     * Run a single test scenario in dry-run mode and produce a test report.
     */
    public Promise<WorkflowTestReport> runScenario(String workflowName, TestScenario scenario) {
        return Promise.ofBlocking(executor, () -> {
            // Build mock registry map for the runner
            Map<String, Object> mockRegistry = new HashMap<>();
            for (StepMock mock : scenario.mocks()) {
                if (mock.failureReason() != null) {
                    mockRegistry.put(mock.stepId() + ".__fail__", mock.failureReason());
                    if (mock.artificialDelayMs() != null) {
                        mockRegistry.put(mock.stepId() + ".__delay__", mock.artificialDelayMs());
                    }
                } else {
                    mockRegistry.put(mock.stepId(), mock.mockResponse());
                }
            }

            String dryRunId = runner.startDryRun(workflowName, scenario.input(), mockRegistry);

            // Poll until dry-run completes (with timeout)
            long deadline = System.currentTimeMillis() + 60_000;
            DryRunStatus status;
            do {
                Thread.sleep(200);
                status = runner.getStatus(dryRunId);
            } while (status == DryRunStatus.RUNNING && System.currentTimeMillis() < deadline);

            List<StepExecutionRecord> steps = runner.getDryRunSteps(dryRunId);

            // Map step records by stepId for assertion lookup
            Map<String, StepExecutionRecord> stepMap = new HashMap<>();
            for (StepExecutionRecord s : steps) stepMap.put(s.stepId(), s);

            // Group assertions by step
            Map<String, List<StepAssertion>> assertionsByStep = new HashMap<>();
            for (StepAssertion a : scenario.assertions()) {
                assertionsByStep.computeIfAbsent(a.stepId(), k -> new ArrayList<>()).add(a);
            }

            // Build per-step results
            List<StepTestResult> stepResults = new ArrayList<>();
            boolean allPassed = true;

            for (StepExecutionRecord step : steps) {
                List<AssertionResult> assertionResults = new ArrayList<>();
                List<StepAssertion> assertions = assertionsByStep.getOrDefault(step.stepId(), List.of());

                for (StepAssertion assertion : assertions) {
                    AssertionResult result = evaluate(assertion, step);
                    assertionResults.add(result);
                    if (result.status() == AssertionStatus.FAIL) allPassed = false;
                }

                if (!step.succeeded() && scenario.type() == ScenarioType.HAPPY_PATH) {
                    allPassed = false;
                }

                stepResults.add(new StepTestResult(step, assertionResults,
                    assertionResults.stream().noneMatch(a -> a.status() == AssertionStatus.FAIL)));
            }

            testRunCounter.increment();
            if (allPassed) scenarioPassCounter.increment();
            else scenarioFailCounter.increment();

            return new WorkflowTestReport(workflowName, scenario.scenarioName(), scenario.type(),
                dryRunId, allPassed, stepResults, now());
        });
    }

    /**
     * Run a batch of named test scenarios and return all reports.
     */
    public Promise<List<WorkflowTestReport>> runScenarios(String workflowName, List<TestScenario> scenarios) {
        return Promise.ofBlocking(executor, () -> {
            List<WorkflowTestReport> reports = new ArrayList<>();
            for (TestScenario scenario : scenarios) {
                reports.add((WorkflowTestReport) runScenario(workflowName, scenario).get());
            }
            return reports;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AssertionResult evaluate(StepAssertion assertion, StepExecutionRecord step) {
        // Simplified evaluator: real impl would navigate nested output object
        if (step.mockResponseUsed() == null) {
            return new AssertionResult(assertion, AssertionStatus.SKIP, "null", "No mock response available");
        }
        String actual = step.mockResponseUsed().toString();
        boolean passed = switch (assertion.operator()) {
            case "eq"       -> actual.equals(String.valueOf(assertion.expectedValue()));
            case "contains" -> actual.contains(String.valueOf(assertion.expectedValue()));
            case "notNull"  -> !actual.equals("null");
            default         -> false;
        };
        return new AssertionResult(assertion, passed ? AssertionStatus.PASS : AssertionStatus.FAIL,
            actual, passed ? "Match" : "Expected: " + assertion.expectedValue() + ", Actual: " + actual);
    }

    private String now() { return java.time.Instant.now().toString(); }
}
