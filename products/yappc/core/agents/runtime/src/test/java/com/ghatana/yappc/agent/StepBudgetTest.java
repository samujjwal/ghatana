package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StepBudget}.
 *
 * @doc.type class
 * @doc.purpose Verify StepBudget record behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("StepBudget Tests")
class StepBudgetTest {

  @Test
  @DisplayName("should create StepBudget with all fields")
  void shouldCreateStepBudget() { // GH-90000
    double maxCostUsd = 5.0;
    long maxWallTimeMs = 60000L;

    StepBudget budget = new StepBudget(maxCostUsd, maxWallTimeMs); // GH-90000

    assertThat(budget.maxCostUsd()).isEqualTo(maxCostUsd); // GH-90000
    assertThat(budget.maxWallTimeMs()).isEqualTo(maxWallTimeMs); // GH-90000
  }

  @Test
  @DisplayName("should convert to Budget with zero tokens")
  void shouldConvertToBudget() { // GH-90000
    StepBudget stepBudget = new StepBudget(10.0, 5000L); // GH-90000

    Budget budget = stepBudget.toBudget(); // GH-90000

    assertThat(budget.maxTokens()).isZero(); // GH-90000
    assertThat(budget.maxCostUsd()).isEqualTo(10.0); // GH-90000
    assertThat(budget.maxWallTimeMs()).isEqualTo(5000L); // GH-90000
  }

  @Test
  @DisplayName("should handle zero values")
  void shouldHandleZeroValues() { // GH-90000
    StepBudget budget = new StepBudget(0.0, 0L); // GH-90000

    assertThat(budget.maxCostUsd()).isZero(); // GH-90000
    assertThat(budget.maxWallTimeMs()).isZero(); // GH-90000

    Budget converted = budget.toBudget(); // GH-90000
    assertThat(converted.maxTokens()).isZero(); // GH-90000
    assertThat(converted.maxCostUsd()).isZero(); // GH-90000
    assertThat(converted.maxWallTimeMs()).isZero(); // GH-90000
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() { // GH-90000
    StepBudget budget1 = new StepBudget(1.0, 1000L); // GH-90000
    StepBudget budget2 = new StepBudget(1.0, 1000L); // GH-90000
    StepBudget budget3 = new StepBudget(2.0, 2000L); // GH-90000

    assertThat(budget1).isEqualTo(budget2); // GH-90000
    assertThat(budget1).isNotEqualTo(budget3); // GH-90000
  }
}
