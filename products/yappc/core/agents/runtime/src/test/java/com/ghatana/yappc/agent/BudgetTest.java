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
@DisplayName("Budget Tests")
class BudgetTest {

  @Test
  @DisplayName("should create Budget with all fields")
  void shouldCreateBudget() { 
    long maxTokens = 1000L;
    double maxCostUsd = 5.0;
    long maxWallTimeMs = 60000L;

    Budget budget = new Budget(maxTokens, maxCostUsd, maxWallTimeMs); 

    assertThat(budget.maxTokens()).isEqualTo(maxTokens); 
    assertThat(budget.maxCostUsd()).isEqualTo(maxCostUsd); 
    assertThat(budget.maxWallTimeMs()).isEqualTo(maxWallTimeMs); 
  }

  @Test
  @DisplayName("should handle zero values")
  void shouldHandleZeroValues() { 
    Budget budget = new Budget(0L, 0.0, 0L); 

    assertThat(budget.maxTokens()).isZero(); 
    assertThat(budget.maxCostUsd()).isZero(); 
    assertThat(budget.maxWallTimeMs()).isZero(); 
  }

  @Test
  @DisplayName("should handle negative values")
  void shouldHandleNegativeValues() { 
    Budget budget = new Budget(-1L, -1.0, -1L); 

    assertThat(budget.maxTokens()).isNegative(); 
    assertThat(budget.maxCostUsd()).isNegative(); 
    assertThat(budget.maxWallTimeMs()).isNegative(); 
  }

  @Test
  @DisplayName("should implement equals and hashCode correctly")
  void shouldImplementEqualsAndHashCode() { 
    Budget budget1 = new Budget(100L, 1.0, 1000L); 
    Budget budget2 = new Budget(100L, 1.0, 1000L); 
    Budget budget3 = new Budget(200L, 2.0, 2000L); 

    assertThat(budget1).isEqualTo(budget2); 
    assertThat(budget1.hashCode()).isEqualTo(budget2.hashCode()); 
    assertThat(budget1).isNotEqualTo(budget3); 
  }

  @Test
  @DisplayName("should have correct toString")
  void shouldHaveCorrectToString() { 
    Budget budget = new Budget(100L, 1.0, 1000L); 

    String str = budget.toString(); 

    assertThat(str).contains("maxTokens=100");
    assertThat(str).contains("maxCostUsd=1.0");
    assertThat(str).contains("maxWallTimeMs=1000");
  }
}
