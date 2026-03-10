package com.ghatana.yappc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * L1 orchestrator that coordinates resources and deployments across multiple cloud providers.
 *
 * <p>Abstracts provider-specific details and delegates to L2 cloud specialists
 * ({@code aws-specialist}, {@code azure-specialist}, {@code gcp-specialist},
 * {@code kubernetes-specialist}). Produces a unified deployment plan or resource
 * allocation with per-provider status tracking.
 *
 * @doc.type class
 * @doc.purpose Coordinates resources and deployments across multiple cloud providers
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class MultiCloudOrchestratorAgent
    extends YAPPCAgentBase<MultiCloudOrchestratorInput, MultiCloudOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(MultiCloudOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  /**
   * Constructs the multi-cloud orchestrator.
   *
   * @param memoryStore memory store for cloud operation tracking
   * @param generator   output generator (rule-based or LLM-powered)
   */
  public MultiCloudOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator) {
    super(
        "MultiCloudOrchestratorAgent",
        "orchestrator.multi-cloud",
        new StepContract(
            "orchestrator.multi-cloud",
            "#/definitions/MultiCloudOrchestratorInput",
            "#/definitions/MultiCloudOrchestratorOutput",
            List.of("multi-cloud-orchestration", "cross-cloud-coordination", "provider-abstraction"),
            Map.of(
                "description", "Coordinates resources and deployments across cloud providers",
                "version", "1.0.0",
                "level", "L1",
                "requires_approval", "true",
                "audit_trail", "comprehensive")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull MultiCloudOrchestratorInput input) {
    if (input.requestId() == null || input.requestId().isEmpty()) {
      return ValidationResult.fail("requestId cannot be empty");
    }
    if (input.requestType() == null || input.requestType().isEmpty()) {
      return ValidationResult.fail("requestType cannot be empty");
    }
    if (input.targetProviders() == null || input.targetProviders().isEmpty()) {
      return ValidationResult.fail("at least one target provider is required");
    }
    // Validate known providers
    Set<String> knownProviders = Set.of("aws", "azure", "gcp", "kubernetes");
    for (String provider : input.targetProviders()) {
      if (!knownProviders.contains(provider.toLowerCase(Locale.ROOT))) {
        return ValidationResult.fail("Unknown cloud provider: " + provider
            + ". Supported: " + knownProviders);
      }
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<MultiCloudOrchestratorInput> perceive(
      @NotNull StepRequest<MultiCloudOrchestratorInput> request,
      @NotNull AgentContext context) {
    MultiCloudOrchestratorInput input = request.input();
    log.info("Perceiving multi-cloud request [{}] type={} providers={}",
        input.requestId(), input.requestType(), input.targetProviders());
    return request;
  }

  /**
   * Rule-based multi-cloud orchestration generator.
   *
   * <p>Creates a deployment plan by delegating to provider-specific specialists
   * and aggregating their results.
   */
  public static class MultiCloudOrchestratorGenerator
      implements OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> {

    private static final Logger log =
        LoggerFactory.getLogger(MultiCloudOrchestratorGenerator.class);

    /** Base cost estimate per provider per operation (arbitrary units). */
    private static final Map<String, Double> PROVIDER_BASE_COST = Map.of(
        "aws", 10.0,
        "azure", 12.0,
        "gcp", 11.0,
        "kubernetes", 5.0);

    @Override
    public @NotNull Promise<StepResult<MultiCloudOrchestratorOutput>> generate(
        @NotNull StepRequest<MultiCloudOrchestratorInput> input,
        @NotNull AgentContext context) {

      Instant start = Instant.now();
      MultiCloudOrchestratorInput cloudInput = input.input();

      log.info("Orchestrating multi-cloud [{}] across {} providers",
          cloudInput.requestId(), cloudInput.targetProviders().size());

      List<String> providerActions = new ArrayList<>();
      Map<String, String> providerStatus = new HashMap<>();
      double totalCost = 0.0;

      for (String provider : cloudInput.targetProviders()) {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);

        // Plan provider-specific actions
        String action = planProviderAction(normalizedProvider, cloudInput.requestType());
        providerActions.add(normalizedProvider + ": " + action);

        // Track per-provider status (all succeed in rule-based mode)
        providerStatus.put(normalizedProvider, "PLANNED");

        // Accumulate cost
        totalCost += PROVIDER_BASE_COST.getOrDefault(normalizedProvider, 15.0);
      }

      String planId = "cloud-" + cloudInput.requestId() + "-" + UUID.randomUUID();

      MultiCloudOrchestratorOutput output = new MultiCloudOrchestratorOutput(
          planId,
          MultiCloudOrchestratorOutput.STATUS_PLANNED,
          providerActions,
          providerStatus,
          totalCost,
          Map.of(
              "generatedAt", start.toString(),
              "requestType", cloudInput.requestType(),
              "providersCount", cloudInput.targetProviders().size(),
              "totalEstimatedCost", totalCost));

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "stepId", "orchestrator.multi-cloud",
                  "planId", planId,
                  "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    /** Plans a provider-specific action based on request type. */
    private String planProviderAction(String provider, String requestType) {
      return switch (requestType.toLowerCase(Locale.ROOT)) {
        case "deployment" -> "deploy-" + provider + "-resources";
        case "resource_allocation" -> "allocate-" + provider + "-resources";
        case "migration" -> "migrate-to-" + provider;
        case "audit" -> "audit-" + provider + "-resources";
        default -> "evaluate-" + provider + "-request";
      };
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<MultiCloudOrchestratorInput> input,
        @NotNull AgentContext context) {
      double cost = input.input().targetProviders().stream()
          .mapToDouble(p -> PROVIDER_BASE_COST.getOrDefault(
              p.toLowerCase(Locale.ROOT), 15.0))
          .sum();
      return Promise.of(cost);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("MultiCloudOrchestratorGenerator")
          .type("rule-based")
          .description("Coordinates resources across multiple cloud providers")
          .version("1.0.0")
          .build();
    }
  }
}
