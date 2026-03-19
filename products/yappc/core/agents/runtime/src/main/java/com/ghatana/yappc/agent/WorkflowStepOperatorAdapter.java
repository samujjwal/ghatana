package com.ghatana.yappc.agent;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.OperatorConfig;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorState;
import com.ghatana.platform.workflow.operator.OperatorType;
import com.ghatana.platform.workflow.operator.UnifiedOperator;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter that wraps a YAPPC {@link WorkflowStep} as a platform {@link UnifiedOperator}.
 *
 * <p>This enables YAPPC workflow steps to participate in the platform's operator catalog,
 * pipeline composition, and lifecycle management infrastructure.
 *
 * @param <I> the workflow step input type
 * @param <O> the workflow step output type
 * @doc.type class
 * @doc.purpose Adapts YAPPC WorkflowStep to platform UnifiedOperator
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class WorkflowStepOperatorAdapter<I, O> implements UnifiedOperator {

  private final WorkflowStep<I, O> step;
  private final OperatorId operatorId;
  private volatile OperatorState state;
  private OperatorConfig config;

  /**
   * Creates an adapter for the given workflow step.
   *
   * @param step the YAPPC workflow step to adapt
   */
  public WorkflowStepOperatorAdapter(@NotNull WorkflowStep<I, O> step) {
    this.step = Objects.requireNonNull(step, "step");
    this.operatorId = OperatorId.of("yappc:workflow:" + step.stepName() + ":1.0.0");
    this.state = OperatorState.CREATED;
  }

  @Override
  public OperatorId getId() {
    return operatorId;
  }

  @Override
  public String getName() {
    return step.stepName();
  }

  @Override
  public OperatorType getType() {
    return OperatorType.STREAM;
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public String getDescription() {
    StepContract contract = step.contract();
    return "YAPPC workflow step: " + step.stepName()
        + " (input: " + contract.inputSchemaRef() + ", output: " + contract.outputSchemaRef() + ")";
  }

  @Override
  public List<String> getCapabilities() {
    StepContract contract = step.contract();
    return contract.requiredCapabilities() != null
        ? List.copyOf(contract.requiredCapabilities())
        : List.of("yappc.workflow." + step.stepName());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Promise<OperatorResult> process(Event event) {
    // Extract input from event payload — stored under the "input" key by convention
    I input = (I) event.getPayload("input");
    StepContext context = extractContext(event);

    ValidationResult validation = step.validateInput(input);
    if (!validation.ok()) {
      return Promise.of(OperatorResult.builder()
          .failed("Validation failed: " + validation.errors())
          .build());
    }

    return step.execute(input, context)
        .map(result -> {
          if (result.success()) {
            return OperatorResult.builder().success().build();
          } else {
            return OperatorResult.builder()
                .failed(String.join("; ", result.errors()))
                .build();
          }
        });
  }

  @Override
  public Promise<Void> initialize(OperatorConfig config) {
    this.config = config;
    this.state = OperatorState.INITIALIZED;
    return Promise.complete();
  }

  @Override
  public Promise<Void> start() {
    this.state = OperatorState.RUNNING;
    return Promise.complete();
  }

  @Override
  public Promise<Void> stop() {
    this.state = OperatorState.STOPPED;
    return Promise.complete();
  }

  @Override
  public boolean isHealthy() {
    return state == OperatorState.RUNNING;
  }

  @Override
  public OperatorState getState() {
    return state;
  }

  @Override
  public Event toEvent() {
    return Event.builder()
        .type("operator.registered")
        .addPayload("operatorId", operatorId.toString())
        .addPayload("source", "yappc-workflow-step")
        .addPayload("stepName", step.stepName())
        .addPayload("type", "STREAM")
        .addPayload("version", "1.0.0")
        .build();
  }

  @Override
  public Map<String, Object> getMetrics() {
    return Map.of(
        "operator.type", "yappc-workflow-step",
        "operator.step", step.stepName(),
        "operator.state", state.name());
  }

  @Override
  public Map<String, Object> getInternalState() {
    return Map.of("stepName", step.stepName(), "state", state.name());
  }

  @Override
  public OperatorConfig getConfig() {
    return config;
  }

  @Override
  public Map<String, String> getMetadata() {
    return Map.of(
        "product", "yappc",
        "stepName", step.stepName(),
        "adaptedFrom", "WorkflowStep");
  }

  /** Returns the underlying YAPPC workflow step. */
  @NotNull
  public WorkflowStep<I, O> getWorkflowStep() {
    return step;
  }

  private StepContext extractContext(Event event) {
    return new StepContext(
        Objects.toString(event.getPayload("tenantId"), "default"),
        Objects.toString(event.getPayload("runId"), "unknown"),
        Objects.toString(event.getPayload("phase"), "unknown"),
        Objects.toString(event.getPayload("configSnapshotId"), ""),
        null, // budget — caller should set via config
        null, // feature flags
        null  // trace context
    );
  }
}
