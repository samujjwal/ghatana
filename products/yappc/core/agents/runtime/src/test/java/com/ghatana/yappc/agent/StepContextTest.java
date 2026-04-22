package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StepContext}.
 *
 * @doc.type class
 * @doc.purpose Verify StepContext construction and accessors
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("StepContext Tests [GH-90000]")
class StepContextTest {

  @Test
  @DisplayName("should create StepContext with full constructor [GH-90000]")
  void shouldCreateWithFullConstructor() { // GH-90000
    String tenantId = "tenant-1";
    String runId = "run-1";
    String phase = "test-phase";
    String configSnapshotId = "config-1";
    Budget budget = new Budget(100L, 1.0, 60000L); // GH-90000
    FeatureFlags flags = new FeatureFlags(java.util.Map.of("flag1", true)); // GH-90000
    TraceContext trace = new TraceContext("trace-1", "span-1"); // GH-90000

    StepContext context = new StepContext(tenantId, runId, phase, configSnapshotId, budget, flags, trace); // GH-90000

    assertThat(context.tenantId()).isEqualTo(tenantId); // GH-90000
    assertThat(context.runId()).isEqualTo(runId); // GH-90000
    assertThat(context.phase()).isEqualTo(phase); // GH-90000
    assertThat(context.configSnapshotId()).isEqualTo(configSnapshotId); // GH-90000
    assertThat(context.budget()).isEqualTo(budget); // GH-90000
    assertThat(context.flags()).isEqualTo(flags); // GH-90000
    assertThat(context.trace()).isEqualTo(trace); // GH-90000
  }

  @Test
  @DisplayName("should create StepContext with convenience constructor (5 params) [GH-90000]")
  void shouldCreateWithConvenienceConstructor() { // GH-90000
    String runId = "run-1";
    String tenantId = "tenant-1";
    String phase = "test-phase";
    String configSnapshotId = "config-1";
    Budget budget = new Budget(100L, 1.0, 60000L); // GH-90000

    StepContext context = new StepContext(runId, tenantId, phase, configSnapshotId, budget); // GH-90000

    assertThat(context.tenantId()).isEqualTo(tenantId); // GH-90000
    assertThat(context.runId()).isEqualTo(runId); // GH-90000
    assertThat(context.phase()).isEqualTo(phase); // GH-90000
    assertThat(context.configSnapshotId()).isEqualTo(configSnapshotId); // GH-90000
    assertThat(context.budget()).isEqualTo(budget); // GH-90000
    assertThat(context.flags()).isNull(); // GH-90000
    assertThat(context.trace()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should create StepContext with StepBudget constructor [GH-90000]")
  void shouldCreateWithStepBudgetConstructor() { // GH-90000
    String runId = "run-1";
    String tenantId = "tenant-1";
    String phase = "test-phase";
    String configSnapshotId = "config-1";
    StepBudget stepBudget = new StepBudget(1.0, 1000L); // GH-90000

    StepContext context = new StepContext(runId, tenantId, phase, configSnapshotId, stepBudget); // GH-90000

    assertThat(context.tenantId()).isEqualTo(tenantId); // GH-90000
    assertThat(context.runId()).isEqualTo(runId); // GH-90000
    assertThat(context.budget()).isNotNull(); // GH-90000
    assertThat(context.budget().maxWallTimeMs()).isEqualTo(1000L); // GH-90000
  }

  @Test
  @DisplayName("should handle null StepBudget [GH-90000]")
  void shouldHandleNullStepBudget() { // GH-90000
    StepContext context = new StepContext("run-1", "tenant-1", "phase", "config", (StepBudget) null); // GH-90000

    assertThat(context.budget()).isNull(); // GH-90000
  }

  private StepContext createTestContext() { // GH-90000
    return new StepContext( // GH-90000
        "tenant-1",
        "run-1",
        "phase-1",
        "config-1",
        new Budget(100L, 1.0, 60000L), // GH-90000
        new FeatureFlags(java.util.Map.of()), // GH-90000
        new TraceContext("trace-1", "span-1") // GH-90000
    );
  }
}
