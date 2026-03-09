package com.ghatana.yappc.sdlc.agent.examples;

import com.ghatana.agent.framework.coordination.*;
import com.ghatana.agent.framework.memory.*;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentRegistry;
import com.ghatana.yappc.sdlc.agent.coordinator.*;
import com.ghatana.yappc.sdlc.agent.leads.*;
import com.ghatana.yappc.sdlc.agent.specialists.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Complete example demonstrating 3-tier agent hierarchy.
 *
 * <p>Shows how Tier 1 coordinator delegates to Tier 2 phase leads, which delegate to Tier 3
 * specialists.
 *
 * @doc.type class
 * @doc.purpose Complete 3-tier hierarchy example
 * @doc.layer product
 * @doc.pattern Example
 */
public class ThreeTierHierarchyExample {

  private static final Logger log = LoggerFactory.getLogger(ThreeTierHierarchyExample.class);

  public static void main(String[] args) {
    log.info("Starting 3-Tier Agent Hierarchy Example");

    Eventloop eventloop = Eventloop.create();
    eventloop.submit(
        () -> {
          // Infrastructure setup - reuse from YAPPCAgentFrameworkExample
          YAPPCAgentRegistry registry = new YAPPCAgentRegistry();
          MemoryStore memoryStore = YAPPCAgentFrameworkExample.createInMemoryStore();
          DelegationManager delegationManager =
              YAPPCAgentFrameworkExample.createDelegationManager();
          OrchestrationStrategy orchestrationStrategy = new SequentialOrchestration();

          log.info("Created infrastructure components");

          // Tier 3: Create specialist agents
          IntakeSpecialistAgent intakeAgent =
              new IntakeSpecialistAgent(memoryStore, new IntakeSpecialistAgent.IntakeGenerator());
          registry.register(intakeAgent);
          log.info("Registered Tier 3 specialist: IntakeSpecialistAgent");

          // Tier 2: Create phase lead agents
          ArchitecturePhaseLeadAgent archPhase =
              new ArchitecturePhaseLeadAgent(
                  registry, memoryStore, new ArchitecturePhaseGenerator(registry));
          registry.register(archPhase);
          log.info("Registered Tier 2 phase lead: ArchitecturePhaseLeadAgent");

          // Tier 1: Create coordinator
          PlatformDeliveryCoordinator coordinator =
              new PlatformDeliveryCoordinator(
                  registry,
                  delegationManager,
                  orchestrationStrategy,
                  memoryStore,
                  new DeliveryCoordinatorGenerator(registry));
          registry.register(coordinator);
          log.info("Registered Tier 1 coordinator: PlatformDeliveryCoordinator");

          // Initialize all agents
          return registry
              .initializeAll()
              .then(
                  () -> {
                    log.info("✓ Initialized all agents");
                    return executeExample(coordinator);
                  })
              .then(() -> registry.shutdownAll())
              .whenComplete(
                  (result, error) -> {
                    if (error != null) {
                      log.error("✗ Error during execution", error);
                    } else {
                      log.info("\n=== EXAMPLE COMPLETE ===");
                      log.info("Demonstrated 3-tier hierarchy:");
                      log.info("  Tier 1: PlatformDeliveryCoordinator orchestrated phases");
                      log.info(
                          "  Tier 2: ArchitecturePhaseLeadAgent coordinated architecture steps");
                      log.info("  Tier 3: IntakeSpecialistAgent processed requirements");
                    }
                  });
        });

    // Run the event loop
    eventloop.run();
  }

  private static Promise<Void> executeExample(PlatformDeliveryCoordinator coordinator) {
    log.info("\n=== EXECUTING 3-TIER HIERARCHY ===");

    // Create delivery request
    DeliveryRequest request =
        new DeliveryRequest(
            "Build a new REST API for user management",
            List.of("architecture"), // Just architecture phase for demo
            DeliveryRequest.Priority.HIGH,
            Map.of("customer", "Acme Corp", "deadline", "Q1 2025"));

    // Create step context (use constructors from existing example)
    StepContext context =
        new StepContext(
            "tenant-123",
            "run-456",
            "delivery",
            "config-v1",
            new Budget(100000L, 50.0, 3600000L),
            new FeatureFlags(Map.of("example", true)),
            new TraceContext("trace-123", "span-001"));

    log.info("Request: {}", request.request());
    log.info("Target phases: {}", request.targetPhases());
    log.info("Priority: {}", request.priority());

    return coordinator
        .execute(request, context)
        .then(
            result -> {
              log.info("\n=== RESULTS ===");
              log.info("Status: {}", result.status());
              log.info("Phase results: {}", result.output().phaseResults().size());
              log.info("Total time: {}ms", result.output().totalExecutionTimeMs());
              log.info("Success: {}", result.output().overallSuccess());

              result
                  .output()
                  .phaseResults()
                  .forEach(
                      (phase, phaseResult) -> {
                        log.info("\n  Phase: {}", phase);
                        log.info("    Status: {}", phaseResult.status());
                        log.info("    Time: {}ms", phaseResult.executionTimeMs());
                        log.info("    Outputs: {}", phaseResult.output().size());
                      });

              return Promise.complete();
            });
  }
}
