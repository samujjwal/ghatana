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
 * Phase Lead Agent for Ops phase.
 *
 * <p>Coordinates deployment, monitoring, and incident response.
 *
 * @doc.type class
 * @doc.purpose Phase lead for operations workflow coordination
 * @doc.layer product
 * @doc.pattern Coordinator
 * @doc.gaa.lifecycle act
 */
public class OpsPhaseLeadAgent extends YAPPCAgentBase<OpsRequest, OpsResult> {

  private static final Logger log = LoggerFactory.getLogger(OpsPhaseLeadAgent.class);

  private final YAPPCAgentRegistry agentRegistry;
  private final MemoryStore memoryStore;

  public OpsPhaseLeadAgent(
      @NotNull YAPPCAgentRegistry agentRegistry,
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<OpsRequest>, StepResult<OpsResult>> generator) {
    super(
        "OpsPhaseLeadAgent",
        "ops.coordinate",
        new StepContract(
            "ops.coordinate",
            "#/definitions/OpsRequest",
            "#/definitions/OpsResult",
            List.of("operations", "deployment", "monitoring"),
            Map.of("description", "Coordinates ops phase", "version", "1.0.0")),
        generator);
    this.agentRegistry = agentRegistry;
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull OpsRequest input) {
    if (input.deploymentId() == null || input.deploymentId().isEmpty()) {
      return ValidationResult.fail("Deployment ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<OpsRequest> perceive(
      @NotNull StepRequest<OpsRequest> request, @NotNull AgentContext context) {
    log.info("Perceiving ops request for deployment: {}", request.input().deploymentId());
    context.addTraceTag("phase.lead", "ops");
    context.addTraceTag("deployment.id", request.input().deploymentId());
    return request;
  }

  @Override
  protected Promise<StepResult<OpsResult>> act(
      @NotNull StepResult<OpsResult> result, @NotNull AgentContext context) {
    log.info(
        "Acting on ops result: deployment={}, status={}",
        result.output().deploymentId(),
        result.output().status());

    // Publish deployment reports
    publishDeploymentReport(result.output());

    return Promise.of(result);
  }

  private void publishDeploymentReport(OpsResult result) {
    // Publish deployment report (would integrate with artifact store)
    log.info(
        "Published deployment report for {}: {} (steps: {})",
        result.deploymentId(),
        result.status(),
        result.completedSteps().size());
  }
}
