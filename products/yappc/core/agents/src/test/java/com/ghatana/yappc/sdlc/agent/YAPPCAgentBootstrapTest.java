package com.ghatana.yappc.sdlc.agent;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.agent.framework.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Integration tests for YAPPC Agent Bootstrap. */
@DisplayName("YAPPC Agent Bootstrap Integration Tests")
/**
 * @doc.type class
 * @doc.purpose Handles yappc agent bootstrap test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class YAPPCAgentBootstrapTest {

  private YAPPCAgentRegistry registry;
  private YAPPCAgentBootstrap bootstrap;
  private MemoryStore memoryStore;

  @BeforeEach
  void setUp() {
    registry = new YAPPCAgentRegistry();
    memoryStore =
        new com.ghatana.agent.framework.memory
            .EventLogMemoryStore(); // Real event-sourced memory store
    bootstrap =
        new YAPPCAgentBootstrap(registry, memoryStore, null, null); // null = no LLM, uses stubs
  }

  @Test
  @DisplayName("Should bootstrap all 27 specialist agents")
  void shouldBootstrapAllSpecialists() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    assertThat(registry.getAgentCount()).isGreaterThanOrEqualTo(27);
  }

  @Test
  @DisplayName("Should register all 4 SDLC phases")
  void shouldRegisterAllPhases() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    assertThat(registry.getAllPhases())
        .containsExactlyInAnyOrder("architecture", "implementation", "testing", "ops");
  }

  @Test
  @DisplayName("Should register 7 Architecture specialists")
  void shouldRegisterArchitectureSpecialists() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    var archAgents = registry.getAgentsByPhase("architecture");
    assertThat(archAgents).hasSizeGreaterThanOrEqualTo(7);

    // Verify key specialists exist
    assertThat(registry.hasAgent("architecture.intake")).isTrue();
    assertThat(registry.hasAgent("architecture.design")).isTrue();
    assertThat(registry.hasAgent("architecture.deriveContracts")).isTrue();
    assertThat(registry.hasAgent("architecture.deriveDataModels")).isTrue();
    assertThat(registry.hasAgent("architecture.validate")).isTrue();
    assertThat(registry.hasAgent("architecture.hitlReview")).isTrue();
    assertThat(registry.hasAgent("architecture.publishArchitecture")).isTrue();
  }

  @Test
  @DisplayName("Should register 7 Implementation specialists")
  void shouldRegisterImplementationSpecialists() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    var implAgents = registry.getAgentsByPhase("implementation");
    assertThat(implAgents).hasSizeGreaterThanOrEqualTo(7);

    // Verify key specialists exist
    assertThat(registry.hasAgent("implementation.scaffold")).isTrue();
    assertThat(registry.hasAgent("implementation.planUnits")).isTrue();
    assertThat(registry.hasAgent("implementation.implement")).isTrue();
    assertThat(registry.hasAgent("implementation.review")).isTrue();
    assertThat(registry.hasAgent("implementation.build")).isTrue();
    assertThat(registry.hasAgent("implementation.qualityGate")).isTrue();
    assertThat(registry.hasAgent("implementation.artifactPublish")).isTrue();
  }

  @Test
  @DisplayName("Should register 6 Testing specialists")
  void shouldRegisterTestingSpecialists() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    var testAgents = registry.getAgentsByPhase("testing");
    assertThat(testAgents).hasSizeGreaterThanOrEqualTo(6);

    // Verify key specialists exist
    assertThat(registry.hasAgent("testing.deriveTestPlan")).isTrue();
    assertThat(registry.hasAgent("testing.generateTests")).isTrue();
    assertThat(registry.hasAgent("testing.executeTests")).isTrue();
    assertThat(registry.hasAgent("testing.analyzeTestResults")).isTrue();
    assertThat(registry.hasAgent("testing.securityTests")).isTrue();
    assertThat(registry.hasAgent("testing.performanceTests")).isTrue();
  }

  @Test
  @DisplayName("Should register 7 Ops specialists")
  void shouldRegisterOpsSpecialists() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    var opsAgents = registry.getAgentsByPhase("ops");
    assertThat(opsAgents).hasSizeGreaterThanOrEqualTo(7);

    // Verify key specialists exist
    assertThat(registry.hasAgent("ops.deployStaging")).isTrue();
    assertThat(registry.hasAgent("ops.validateRelease")).isTrue();
    assertThat(registry.hasAgent("ops.canary")).isTrue();
    assertThat(registry.hasAgent("ops.monitor")).isTrue();
    assertThat(registry.hasAgent("ops.promoteOrRollback")).isTrue();
    assertThat(registry.hasAgent("ops.incidentResponse")).isTrue();
    assertThat(registry.hasAgent("ops.publish")).isTrue();
  }

  @Test
  @DisplayName("Should allow agent lookup by step name")
  void shouldAllowAgentLookupByStepName() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN
    var intakeAgent = registry.getAgent("architecture.intake");
    assertThat(intakeAgent).isNotNull();

    var buildAgent = registry.getAgent("implementation.build");
    assertThat(buildAgent).isNotNull();

    var securityAgent = registry.getAgent("testing.securityTests");
    assertThat(securityAgent).isNotNull();

    var canaryAgent = registry.getAgent("ops.canary");
    assertThat(canaryAgent).isNotNull();
  }

  @Test
  @DisplayName("Should generate comprehensive summary")
  void shouldGenerateComprehensiveSummary() {
    // WHEN
    registry = bootstrap.bootstrap();
    String summary = bootstrap.getSummary();

    // THEN
    assertThat(summary)
        .contains("YAPPC Agent Registry Summary")
        .contains("Total Agents:")
        .contains("ARCHITECTURE")
        .contains("IMPLEMENTATION")
        .contains("TESTING")
        .contains("OPS");
  }

  @Test
  @DisplayName("Should register phase leads after specialists")
  void shouldRegisterPhaseLeads() {
    // WHEN
    registry = bootstrap.bootstrap();

    // THEN - Phase leads use .coordinate suffix
    assertThat(registry.hasAgent("architecture.coordinate")).isTrue();
    assertThat(registry.hasAgent("implementation.coordinate")).isTrue();
    assertThat(registry.hasAgent("testing.coordinate")).isTrue();
    assertThat(registry.hasAgent("ops.coordinate")).isTrue();
  }

  @Test
  @DisplayName("Should not fail on double bootstrap")
  void shouldNotFailOnDoubleBootstrap() {
    // WHEN - Bootstrap twice
    registry = bootstrap.bootstrap();
    int firstCount = registry.getAgentCount();

    registry = bootstrap.bootstrap();
    int secondCount = registry.getAgentCount();

    // THEN - Second bootstrap overwrites, count should be similar
    assertThat(secondCount).isGreaterThanOrEqualTo(27);
  }
}
