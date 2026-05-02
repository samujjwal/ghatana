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
@DisplayName("StepContext Tests")
class StepContextTest {

  @Test
  @DisplayName("should create StepContext with full constructor")
  void shouldCreateWithFullConstructor() { 
    String tenantId = "tenant-1";
    String runId = "run-1";
    String phase = "test-phase";
    String configSnapshotId = "config-1";
    Budget budget = new Budget(100L, 1.0, 60000L); 
    FeatureFlags flags = new FeatureFlags(java.util.Map.of("flag1", true)); 
    TraceContext trace = new TraceContext("trace-1", "span-1"); 

    StepContext context = new StepContext(tenantId, runId, phase, configSnapshotId, budget, flags, trace); 

    assertThat(context.tenantId()).isEqualTo(tenantId); 
    assertThat(context.runId()).isEqualTo(runId); 
    assertThat(context.phase()).isEqualTo(phase); 
    assertThat(context.configSnapshotId()).isEqualTo(configSnapshotId); 
    assertThat(context.budget()).isEqualTo(budget); 
    assertThat(context.flags()).isEqualTo(flags); 
    assertThat(context.trace()).isEqualTo(trace); 
  }

  @Test
  @DisplayName("should create StepContext with convenience constructor (5 params)")
  void shouldCreateWithConvenienceConstructor() { 
    String runId = "run-1";
    String tenantId = "tenant-1";
    String phase = "test-phase";
    String configSnapshotId = "config-1";
    Budget budget = new Budget(100L, 1.0, 60000L); 

    StepContext context = new StepContext(runId, tenantId, phase, configSnapshotId, budget); 

    assertThat(context.tenantId()).isEqualTo(tenantId); 
    assertThat(context.runId()).isEqualTo(runId); 
    assertThat(context.phase()).isEqualTo(phase); 
    assertThat(context.configSnapshotId()).isEqualTo(configSnapshotId); 
    assertThat(context.budget()).isEqualTo(budget); 
    assertThat(context.flags()).isNull(); 
    assertThat(context.trace()).isNull(); 
  }

  @Test
  @DisplayName("should create StepContext with StepBudget constructor")
  void shouldCreateWithStepBudgetConstructor() { 
    String runId = "run-1";
    String tenantId = "tenant-1";
    String phase = "test-phase";
    String configSnapshotId = "config-1";
    StepBudget stepBudget = new StepBudget(1.0, 1000L); 

    StepContext context = new StepContext(runId, tenantId, phase, configSnapshotId, stepBudget); 

    assertThat(context.tenantId()).isEqualTo(tenantId); 
    assertThat(context.runId()).isEqualTo(runId); 
    assertThat(context.budget()).isNotNull(); 
    assertThat(context.budget().maxWallTimeMs()).isEqualTo(1000L); 
  }

  @Test
  @DisplayName("should handle null StepBudget")
  void shouldHandleNullStepBudget() { 
    StepContext context = new StepContext("run-1", "tenant-1", "phase", "config", (StepBudget) null); 

    assertThat(context.budget()).isNull(); 
  }

  private StepContext createTestContext() { 
    return new StepContext( 
        "tenant-1",
        "run-1",
        "phase-1",
        "config-1",
        new Budget(100L, 1.0, 60000L), 
        new FeatureFlags(java.util.Map.of()), 
        new TraceContext("trace-1", "span-1") 
    );
  }
}
