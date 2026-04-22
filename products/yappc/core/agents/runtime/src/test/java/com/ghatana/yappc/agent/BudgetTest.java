package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Budget}.
 *
 * @doc.type class
 * @doc.purpose Verify Budget record behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("Budget Tests [GH-90000]")
class BudgetTest {

  @Test
  @DisplayName("should create Budget with all fields [GH-90000]")
  void shouldCreateBudget() { // GH-90000
    long maxTokens = 1000L;
    double maxCostUsd = 5.0;
    long maxWallTimeMs = 60000L;

    Budget budget = new Budget(maxTokens, maxCostUsd, maxWallTimeMs); // GH-90000

    assertThat(budget.maxTokens()).isEqualTo(maxTokens); // GH-90000
    assertThat(budget.maxCostUsd()).isEqualTo(maxCostUsd); // GH-90000
    assertThat(budget.maxWallTimeMs()).isEqualTo(maxWallTimeMs); // GH-90000
  }

  @Test
  @DisplayName("should handle zero values [GH-90000]")
  void shouldHandleZeroValues() { // GH-90000
    Budget budget = new Budget(0L, 0.0, 0L); // GH-90000

    assertThat(budget.maxTokens()).isZero(); // GH-90000
    assertThat(budget.maxCostUsd()).isZero(); // GH-90000
    assertThat(budget.maxWallTimeMs()).isZero(); // GH-90000
  }

  @Test
  @DisplayName("should handle negative values [GH-90000]")
  void shouldHandleNegativeValues() { // GH-90000
    Budget budget = new Budget(-1L, -1.0, -1L); // GH-90000

    assertThat(budget.maxTokens()).isNegative(); // GH-90000
    assertThat(budget.maxCostUsd()).isNegative(); // GH-90000
    assertThat(budget.maxWallTimeMs()).isNegative(); // GH-90000
  }

  @Test
  @DisplayName("should implement equals and hashCode correctly [GH-90000]")
  void shouldImplementEqualsAndHashCode() { // GH-90000
    Budget budget1 = new Budget(100L, 1.0, 1000L); // GH-90000
    Budget budget2 = new Budget(100L, 1.0, 1000L); // GH-90000
    Budget budget3 = new Budget(200L, 2.0, 2000L); // GH-90000

    assertThat(budget1).isEqualTo(budget2); // GH-90000
    assertThat(budget1.hashCode()).isEqualTo(budget2.hashCode()); // GH-90000
    assertThat(budget1).isNotEqualTo(budget3); // GH-90000
  }

  @Test
  @DisplayName("should have correct toString [GH-90000]")
  void shouldHaveCorrectToString() { // GH-90000
    Budget budget = new Budget(100L, 1.0, 1000L); // GH-90000

    String str = budget.toString(); // GH-90000

    assertThat(str).contains("maxTokens=100 [GH-90000]");
    assertThat(str).contains("maxCostUsd=1.0 [GH-90000]");
    assertThat(str).contains("maxWallTimeMs=1000 [GH-90000]");
  }
}
