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
  void shouldCreateStepBudget() { 
    double maxCostUsd = 5.0;
    long maxWallTimeMs = 60000L;

    StepBudget budget = new StepBudget(maxCostUsd, maxWallTimeMs); 

    assertThat(budget.maxCostUsd()).isEqualTo(maxCostUsd); 
    assertThat(budget.maxWallTimeMs()).isEqualTo(maxWallTimeMs); 
  }

  @Test
  @DisplayName("should convert to Budget with zero tokens")
  void shouldConvertToBudget() { 
    StepBudget stepBudget = new StepBudget(10.0, 5000L); 

    Budget budget = stepBudget.toBudget(); 

    assertThat(budget.maxTokens()).isZero(); 
    assertThat(budget.maxCostUsd()).isEqualTo(10.0); 
    assertThat(budget.maxWallTimeMs()).isEqualTo(5000L); 
  }

  @Test
  @DisplayName("should handle zero values")
  void shouldHandleZeroValues() { 
    StepBudget budget = new StepBudget(0.0, 0L); 

    assertThat(budget.maxCostUsd()).isZero(); 
    assertThat(budget.maxWallTimeMs()).isZero(); 

    Budget converted = budget.toBudget(); 
    assertThat(converted.maxTokens()).isZero(); 
    assertThat(converted.maxCostUsd()).isZero(); 
    assertThat(converted.maxWallTimeMs()).isZero(); 
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() { 
    StepBudget budget1 = new StepBudget(1.0, 1000L); 
    StepBudget budget2 = new StepBudget(1.0, 1000L); 
    StepBudget budget3 = new StepBudget(2.0, 2000L); 

    assertThat(budget1).isEqualTo(budget2); 
    assertThat(budget1).isNotEqualTo(budget3); 
  }
}
