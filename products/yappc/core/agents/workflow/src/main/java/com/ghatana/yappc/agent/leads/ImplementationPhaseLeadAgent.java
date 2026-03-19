package com.ghatana.yappc.agent.leads;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase Lead Agent for Implementation phase.
 *
 * <p>Coordinates implementation steps: scaffold → plan → implement → review → build → quality gate
 * → publish
 *
 * @doc.type class
 * @doc.purpose Phase lead for implementation workflow coordination
 * @doc.layer product
 * @doc.pattern Coordinator
 * @doc.gaa.lifecycle act
 */
public class ImplementationPhaseLeadAgent
    extends YAPPCAgentBase<ImplementationRequest, ImplementationResult> {

  private static final Logger log = LoggerFactory.getLogger(ImplementationPhaseLeadAgent.class);

  private final YAPPCAgentRegistry agentRegistry;
  private final MemoryStore memoryStore;

  public ImplementationPhaseLeadAgent(
      @NotNull YAPPCAgentRegistry agentRegistry,
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<ImplementationRequest>, StepResult<ImplementationResult>>
              generator) {
    super(
        "ImplementationPhaseLeadAgent",
        "implementation.coordinate",
        new StepContract(
            "implementation.coordinate",
            "#/definitions/ImplementationRequest",
            "#/definitions/ImplementationResult",
            List.of("implementation", "build", "codegen"),
            Map.of("description", "Coordinates implementation phase", "version", "1.0.0")),
        generator);
    this.agentRegistry = agentRegistry;
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ImplementationRequest input) {
    return input.architecture() == null || input.architecture().isEmpty()
        ? ValidationResult.fail("Architecture cannot be empty")
        : ValidationResult.success();
  }

  @Override
  protected StepRequest<ImplementationRequest> perceive(
      @NotNull StepRequest<ImplementationRequest> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving implementation request for architecture: {}",
        request
            .input()
            .architecture()
            .substring(0, Math.min(50, request.input().architecture().length())));
    // Enrich with phase context
    context.addTraceTag("phase.lead", "implementation");
    context.addTraceTag("phase.startTime", String.valueOf(System.currentTimeMillis()));
    return request;
  }

  @Override
  protected Promise<StepResult<ImplementationResult>> act(
      @NotNull StepResult<ImplementationResult> result, @NotNull AgentContext context) {
    log.info(
        "Acting on implementation result: {} steps completed, build={}, quality={}",
        result.output().stepCount(),
        result.output().buildSuccessful(),
        result.output().qualityGatePassed());
    // Publish implementation artifacts
    return publishArtifacts(result, context).map(v -> result);
  }

  private Promise<Void> publishArtifacts(
      StepResult<ImplementationResult> result, AgentContext context) {
    log.info("Publishing implementation artifacts");
    // In real implementation, would publish to artifact repository
    return Promise.complete();
  }

  @Override
  protected Promise<Void> reflect(
      @NotNull StepRequest<ImplementationRequest> input,
      @NotNull StepResult<ImplementationResult> output,
      @NotNull AgentContext context) {
    log.info("Reflecting on implementation phase: {} steps", output.output().stepCount());
    return Promise.complete();
  }
}
