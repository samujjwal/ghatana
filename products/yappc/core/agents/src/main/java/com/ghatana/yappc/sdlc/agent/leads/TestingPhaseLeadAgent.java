package com.ghatana.yappc.sdlc.agent.leads;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase Lead Agent for Testing phase.
 *
 * <p>Coordinates testing steps: derive plan → generate → execute → performance → security → release
 * gate
 *
 * @doc.type class
 * @doc.purpose Phase lead for testing workflow coordination
 * @doc.layer product
 * @doc.pattern Coordinator
 * @doc.gaa.lifecycle act
 */
public class TestingPhaseLeadAgent extends YAPPCAgentBase<TestingRequest, TestingResult> {

  private static final Logger log = LoggerFactory.getLogger(TestingPhaseLeadAgent.class);

  private final YAPPCAgentRegistry agentRegistry;
  private final MemoryStore memoryStore;

  public TestingPhaseLeadAgent(
      @NotNull YAPPCAgentRegistry agentRegistry,
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<TestingRequest>, StepResult<TestingResult>> generator) {
    super(
        "TestingPhaseLeadAgent",
        "testing.coordinate",
        new StepContract(
            "testing.coordinate",
            "#/definitions/TestingRequest",
            "#/definitions/TestingResult",
            List.of("testing", "quality", "validation"),
            Map.of("description", "Coordinates testing phase", "version", "1.0.0")),
        generator);
    this.agentRegistry = agentRegistry;
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull TestingRequest input) {
    return input.implementationId() == null || input.implementationId().isEmpty()
        ? ValidationResult.fail("Implementation ID cannot be empty")
        : ValidationResult.success();
  }

  @Override
  protected StepRequest<TestingRequest> perceive(
      @NotNull StepRequest<TestingRequest> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving testing request for implementation: {}", request.input().implementationId());
    context.addTraceTag("phase.lead", "testing");
    context.addTraceTag("phase.startTime", String.valueOf(System.currentTimeMillis()));
    return request;
  }

  @Override
  protected Promise<StepResult<TestingResult>> act(
      @NotNull StepResult<TestingResult> result, @NotNull AgentContext context) {
    log.info(
        "Acting on testing result: {} tests ({} passed, {} failed), all passed: {}",
        result.output().totalTests(),
        result.output().passedTests(),
        result.output().failedTests(),
        result.output().allTestsPassed());

    // Publish test reports
    return publishTestReports(result, context).map(v -> result);
  }

  private Promise<Void> publishTestReports(StepResult<TestingResult> result, AgentContext context) {
    log.info("Publishing test reports for {} test suites", result.output().testResults().size());
    // In real implementation, would publish to test reporting system
    return Promise.complete();
  }

  @Override
  protected Promise<Void> reflect(
      @NotNull StepRequest<TestingRequest> input,
      @NotNull StepResult<TestingResult> output,
      @NotNull AgentContext context) {
    log.info(
        "Reflecting on testing phase: {} tests, pass rate: {:.2f}%",
        output.output().totalTests(),
        output.output().totalTests() > 0
            ? (100.0 * output.output().passedTests() / output.output().totalTests())
            : 0.0);

    // Learn from test failures
    if (!output.output().allTestsPassed()) {
      log.warn("Test failures detected - analyzing for patterns");
    }

    return Promise.complete();
  }
}
