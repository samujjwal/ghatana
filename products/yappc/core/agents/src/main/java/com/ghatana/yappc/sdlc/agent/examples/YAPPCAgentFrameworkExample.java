package com.ghatana.yappc.sdlc.agent.examples;

import com.ghatana.agent.framework.coordination.*;
import com.ghatana.agent.framework.memory.*;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentRegistry;
import com.ghatana.yappc.sdlc.agent.coordinator.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating the YAPPC agent framework in action.
 *
 * <p>Shows:
 *
 * <ul>
 *   <li>Agent registration
 *   <li>Delivery coordination
 *   <li>Phase execution
 *   <li>Result aggregation
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Example usage of YAPPC agent framework
 * @doc.layer product
 * @doc.pattern Example
 */
public class YAPPCAgentFrameworkExample {

  private static final Logger log = LoggerFactory.getLogger(YAPPCAgentFrameworkExample.class);

  /**
   * Example main method demonstrating framework usage.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args) {
    log.info("Starting YAPPC Agent Framework Example");

    // Create ActiveJ event loop for async operations
    Eventloop eventloop = Eventloop.create();

    eventloop.submit(
        () -> {
          // 1. Create infrastructure components
          YAPPCAgentRegistry registry = new YAPPCAgentRegistry();
          MemoryStore memoryStore = createInMemoryStore();
          DelegationManager delegationManager = createDelegationManager();
          OrchestrationStrategy orchestrationStrategy = new SequentialOrchestration();

          // 2. Create the coordinator
          DeliveryCoordinatorGenerator generator = new DeliveryCoordinatorGenerator(registry);
          PlatformDeliveryCoordinator coordinator =
              new PlatformDeliveryCoordinator(
                  registry, delegationManager, orchestrationStrategy, memoryStore, generator);

          // 3. Register the coordinator
          registry.register(coordinator);

          // 4. Initialize all agents
          return registry
              .initializeAll()
              .then(
                  v -> {
                    log.info("All agents initialized successfully");
                    log.info("Registered agents: {}", registry.getAllStepNames());
                    log.info("Registered phases: {}", registry.getAllPhases());

                    // 5. Create a delivery request
                    DeliveryRequest request =
                        new DeliveryRequest(
                            "Build microservices platform with event-driven architecture",
                            List.of("architecture", "implementation", "testing"),
                            DeliveryRequest.Priority.HIGH,
                            Map.of("source", "example", "version", "1.0"));

                    // 6. Create execution context
                    StepContext stepContext =
                        new StepContext(
                            "tenant-001",
                            "run-" + UUID.randomUUID(),
                            "platform",
                            "config-v1",
                            new Budget(100000L, 50.0, 3600000L),
                            new FeatureFlags(Map.of("ai-assisted", true)),
                            new TraceContext("trace-" + UUID.randomUUID(), "span-001"));

                    // 7. Execute the delivery workflow
                    log.info("Executing delivery request...");
                    return coordinator
                        .execute(request, stepContext)
                        .whenResult(
                            result -> {
                              log.info("Delivery completed!");
                              log.info("Status: {}", result.status());
                              log.info(
                                  "Phases executed: {}", result.output().phaseResults().size());
                              log.info("Total time: {}ms", result.output().totalExecutionTimeMs());
                              log.info("Overall success: {}", result.output().overallSuccess());

                              // Print phase results
                              result
                                  .output()
                                  .phaseResults()
                                  .forEach(
                                      (phase, phaseResult) -> {
                                        log.info(
                                            "  Phase {}: {} ({}ms)",
                                            phase,
                                            phaseResult.status(),
                                            phaseResult.executionTimeMs());
                                      });
                            });
                  })
              .then(v -> registry.shutdownAll())
              .whenComplete(
                  (v, e) -> {
                    if (e != null) {
                      log.error("Example failed", e);
                    } else {
                      log.info("Example completed successfully");
                    }
                  });
        });

    // Run the event loop
    eventloop.run();
  }

  /** Creates a simple in-memory store for the example. */
  public static MemoryStore createInMemoryStore() {
    return new MemoryStore() {
      private final List<Episode> episodes = new ArrayList<>();

      @Override
      public Promise<Episode> storeEpisode(Episode episode) {
        episodes.add(episode);
        return Promise.of(episode);
      }

      @Override
      public Promise<List<Episode>> queryEpisodes(MemoryFilter filter, int limit) {
        return Promise.of(episodes.stream().limit(limit).toList());
      }

      @Override
      public Promise<List<Episode>> searchEpisodes(String query, int limit) {
        return Promise.of(List.of());
      }

      @Override
      public Promise<Fact> storeFact(Fact fact) {
        return Promise.of(fact);
      }

      @Override
      public Promise<List<Fact>> queryFacts(String subject, String predicate, String object) {
        return Promise.of(List.of());
      }

      @Override
      public Promise<List<Fact>> searchFacts(String concept, int limit) {
        return Promise.of(List.of());
      }

      @Override
      public Promise<Policy> storePolicy(Policy policy) {
        return Promise.of(policy);
      }

      @Override
      public Promise<List<Policy>> queryPolicies(String situation, double minConfidence) {
        return Promise.of(List.of());
      }

      @Override
      public Promise<Policy> getPolicy(String policyId) {
        return Promise.of(null);
      }

      @Override
      public Promise<Preference> storePreference(Preference preference) {
        return Promise.of(preference);
      }

      @Override
      public Promise<String> getPreference(String key) {
        return Promise.of(null);
      }

      @Override
      public Promise<Map<String, String>> getPreferences(String namespace) {
        return Promise.of(Map.of());
      }

      @Override
      public Promise<GovernanceResult> applyGovernance(GovernancePolicy policy) {
        return Promise.of(new GovernanceResult(0, 0, 0, 0));
      }

      @Override
      public Promise<Integer> clearMemory() {
        return Promise.of(episodes.size());
      }

      @Override
      public Promise<MemoryStats> getStats() {
        return Promise.of(new MemoryStats(episodes.size(), 0, 0, 0, 0));
      }
    };
  }

  /** Creates a simple delegation manager for the example. */
  public static DelegationManager createDelegationManager() {
    return new DelegationManager() {
      @Override
      public <TResult> Promise<TResult> delegate(
          DelegationRequest<TResult> request,
          com.ghatana.agent.framework.api.AgentContext context) {
        log.info("Delegating task to agent: {}", request.getToAgentId());
        return Promise.of(null);
      }

      @Override
      public <TResult> Promise<List<TResult>> delegateParallel(
          List<DelegationRequest<TResult>> requests,
          com.ghatana.agent.framework.api.AgentContext context) {
        log.info("Delegating {} tasks in parallel", requests.size());
        return Promise.of(Collections.nCopies(requests.size(), null));
      }

      @Override
      public Promise<List<AgentInfo>> findAgents(
          AgentCriteria criteria, com.ghatana.agent.framework.api.AgentContext context) {
        return Promise.of(List.of());
      }

      @Override
      public Promise<DelegationStatus> getStatus(
          String delegationId, com.ghatana.agent.framework.api.AgentContext context) {
        return Promise.of(
            new DelegationStatus(
                delegationId,
                null,
                null,
                DelegationStatus.State.COMPLETED,
                java.time.Instant.now(),
                java.time.Instant.now(),
                null,
                null));
      }

      @Override
      public Promise<Boolean> cancel(
          String delegationId, com.ghatana.agent.framework.api.AgentContext context) {
        return Promise.of(true);
      }
    };
  }
}
