package com.ghatana.yappc.ai.cost;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CostTrackingService} — the token usage and cost tracking engine
 * that enforces budgets and auto-downgrades models when budgets are exceeded.
 *
 * @doc.type class
 * @doc.purpose Unit tests for cost tracking, budget enforcement, and model recommendation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CostTrackingService Tests")
class CostTrackingServiceTest extends EventloopTestBase {

  private CostTrackingService service;

  @BeforeEach
  void setUp() { // GH-90000
    service = new CostTrackingService(eventloop()); // GH-90000
  }

  // ===== Usage Tracking Tests =====

  @Nested
  @DisplayName("Usage Tracking")
  class UsageTracking {

    @Test
    @DisplayName("Should track usage and return calculated cost")
    void shouldTrackUsage() { // GH-90000
      Double cost = runPromise( // GH-90000
          () -> service.trackUsage("user-1", "gpt-4", 1000, 500, "code-gen")); // GH-90000

      assertThat(cost).isGreaterThan(0.0); // GH-90000
    }

    @Test
    @DisplayName("Should calculate cost based on model pricing")
    void shouldCalculateCostFromPricing() { // GH-90000
      // gpt-4: input=$0.03/1k, output=$0.06/1k
      // 1000 input tokens = $0.03, 1000 output tokens = $0.06 → total $0.09
      Double cost = runPromise( // GH-90000
          () -> service.trackUsage("user-1", "gpt-4", 1000, 1000, "test")); // GH-90000

      assertThat(cost).isCloseTo(0.09, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
    }

    @Test
    @DisplayName("Should accumulate costs across multiple requests")
    void shouldAccumulateCosts() { // GH-90000
      runPromise(() -> service.trackUsage("user-1", "gpt-4", 1000, 500, "req-1")); // GH-90000
      runPromise(() -> service.trackUsage("user-1", "gpt-4", 2000, 1000, "req-2")); // GH-90000

      Map<String, Object> stats = runPromise(() -> service.getUserStats("user-1"));

      double totalCost = (double) stats.get("totalCost");
      assertThat(totalCost).isGreaterThan(0.0); // GH-90000
      assertThat((int) stats.get("usageCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return zero cost for unknown model")
    void shouldReturnZeroCostForUnknownModel() { // GH-90000
      Double cost = runPromise( // GH-90000
          () -> service.trackUsage("user-1", "unknown-model", 1000, 500, "test")); // GH-90000

      assertThat(cost).isEqualTo(0.0); // GH-90000
    }
  }

  // ===== User Stats Tests =====

  @Nested
  @DisplayName("User Stats")
  class UserStats {

    @Test
    @DisplayName("Should return default stats for new user")
    void shouldReturnDefaultStatsForNewUser() { // GH-90000
      Map<String, Object> stats = runPromise(() -> service.getUserStats("new-user"));

      assertThat((double) stats.get("totalCost")).isEqualTo(0.0);
      assertThat((double) stats.get("budget")).isEqualTo(50.0);
      assertThat((int) stats.get("usageCount")).isEqualTo(0);
      assertThat(stats.get("status")).isEqualTo("OK");
    }

    @Test
    @DisplayName("Should report WARNING when usage exceeds 80% of budget")
    void shouldReportWarningStatus() { // GH-90000
      // Set small budget
      runPromise(() -> service.setBudget("user-1", 0.10)); // GH-90000
      // Generate cost above 80% of budget: 0.09 > 80% of 0.10
      runPromise(() -> service.trackUsage("user-1", "gpt-4", 1000, 1000, "test")); // GH-90000

      Map<String, Object> stats = runPromise(() -> service.getUserStats("user-1"));

      assertThat(stats.get("status")).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("Should report EXCEEDED when usage exceeds budget")
    void shouldReportExceededStatus() { // GH-90000
      // Set very small budget
      runPromise(() -> service.setBudget("user-1", 0.01)); // GH-90000
      // GPT-4 with 1k tokens costs ~$0.09 which exceeds $0.01
      runPromise(() -> service.trackUsage("user-1", "gpt-4", 1000, 1000, "test")); // GH-90000

      Map<String, Object> stats = runPromise(() -> service.getUserStats("user-1"));

      assertThat(stats.get("status")).isEqualTo("EXCEEDED");
    }
  }

  // ===== Budget Management Tests =====

  @Nested
  @DisplayName("Budget Management")
  class BudgetManagement {

    @Test
    @DisplayName("Should set custom budget for user")
    void shouldSetCustomBudget() { // GH-90000
      runPromise(() -> service.setBudget("user-1", 100.0)); // GH-90000

      Map<String, Object> stats = runPromise(() -> service.getUserStats("user-1"));

      assertThat((double) stats.get("budget")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should set budget for user with no prior usage")
    void shouldSetBudgetForNewUser() { // GH-90000
      runPromise(() -> service.setBudget("new-user", 25.0)); // GH-90000

      Map<String, Object> stats = runPromise(() -> service.getUserStats("new-user"));

      assertThat((double) stats.get("budget")).isEqualTo(25.0);
      assertThat((double) stats.get("totalCost")).isEqualTo(0.0);
    }
  }

  // ===== Model Recommendation Tests =====

  @Nested
  @DisplayName("Model Recommendation")
  class ModelRecommendation {

    @Test
    @DisplayName("Should return preferred model when within budget")
    void shouldReturnPreferredModelWithinBudget() { // GH-90000
      String model = runPromise( // GH-90000
          () -> service.recommendModel("user-1", "gpt-4")); // GH-90000

      assertThat(model).isEqualTo("gpt-4");
    }

    @Test
    @DisplayName("Should downgrade GPT-4 to GPT-3.5 when budget exceeded")
    void shouldDowngradeGpt4WhenExceeded() { // GH-90000
      runPromise(() -> service.setBudget("user-1", 0.01)); // GH-90000
      runPromise(() -> service.trackUsage("user-1", "gpt-4", 1000, 1000, "test")); // GH-90000

      String model = runPromise( // GH-90000
          () -> service.recommendModel("user-1", "gpt-4")); // GH-90000

      assertThat(model).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    @DisplayName("Should downgrade Claude Opus to Sonnet when budget exceeded")
    void shouldDowngradeOpusWhenExceeded() { // GH-90000
      runPromise(() -> service.setBudget("user-1", 0.01)); // GH-90000
      runPromise(() -> service.trackUsage("user-1", "claude-3-opus", 1000, 1000, "test")); // GH-90000

      String model = runPromise( // GH-90000
          () -> service.recommendModel("user-1", "claude-3-opus")); // GH-90000

      assertThat(model).isEqualTo("claude-3-sonnet");
    }

    @Test
    @DisplayName("Should keep model if user has no budget override")
    void shouldKeepModelWithDefaultBudget() { // GH-90000
      // Default budget is $50, small usage won't exceed
      runPromise(() -> service.trackUsage("user-1", "gpt-4", 100, 50, "test")); // GH-90000

      String model = runPromise( // GH-90000
          () -> service.recommendModel("user-1", "gpt-4")); // GH-90000

      assertThat(model).isEqualTo("gpt-4");
    }
  }
}
