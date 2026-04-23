package com.ghatana.platform.database.diagnostics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for NplusOneDetector query pattern detection
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("NplusOneDetector — N+1 query pattern detection and violation reporting")
class NplusOneDetectorTest {

    @BeforeEach
    void setUp() { // GH-90000
        NplusOneDetector.resetStats(); // GH-90000
        NplusOneDetector.setThreshold(5); // reset to default // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        // Ensure scope is always cleared even if test fails mid-scope
        NplusOneDetector.endScope(); // GH-90000
        NplusOneDetector.resetStats(); // GH-90000
    }

    // ── No active scope ───────────────────────────────────────────────────────

    @Test
    @DisplayName("recordQuery has no effect when no scope is active")
    void recordQueryNoopWithoutActiveScope() { // GH-90000
        NplusOneDetector.recordQuery("SELECT * FROM users WHERE id = ?");
        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000
        assertThat(violations).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("endScope returns empty list when no scope is active")
    void endScopeReturnsEmptyWhenNoScope() { // GH-90000
        List<NplusOneDetector.Violation> result = NplusOneDetector.endScope(); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    // ── Within threshold ──────────────────────────────────────────────────────

    @Test
    @DisplayName("endScope returns no violations when queries are below threshold")
    void noViolationsBelowThreshold() { // GH-90000
        NplusOneDetector.setThreshold(5); // GH-90000
        NplusOneDetector.beginScope("GET /api/users");

        for (int i = 0; i < 4; i++) { // GH-90000
            NplusOneDetector.recordQuery("SELECT * FROM orders WHERE user_id = ?");
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000
        assertThat(violations).isEmpty(); // GH-90000
    }

    // ── Threshold exceeded ────────────────────────────────────────────────────

    @Test
    @DisplayName("endScope returns violations when queries reach the threshold")
    void violationDetectedAtThreshold() { // GH-90000
        NplusOneDetector.setThreshold(3); // GH-90000
        NplusOneDetector.beginScope("GET /api/orders");
        String sql = "SELECT * FROM line_items WHERE order_id = ?";

        for (int i = 0; i < 3; i++) { // GH-90000
            NplusOneDetector.recordQuery(sql); // GH-90000
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000
        assertThat(violations).hasSize(1); // GH-90000
        assertThat(violations.get(0).querySql()).isEqualTo(sql); // GH-90000
        assertThat(violations.get(0).executionCount()).isEqualTo(3); // GH-90000
        assertThat(violations.get(0).scopeName()).isEqualTo("GET /api/orders");
    }

    @Test
    @DisplayName("endScope reports violation count above threshold")
    void violationCountReflectsActualExecutions() { // GH-90000
        NplusOneDetector.setThreshold(2); // GH-90000
        NplusOneDetector.beginScope("scope");
        String sql = "SELECT name FROM users WHERE id = ?";

        for (int i = 0; i < 10; i++) { // GH-90000
            NplusOneDetector.recordQuery(sql); // GH-90000
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000
        assertThat(violations).hasSize(1); // GH-90000
        assertThat(violations.get(0).executionCount()).isEqualTo(10); // GH-90000
    }

    // ── Multiple SQL templates ────────────────────────────────────────────────

    @Test
    @DisplayName("each distinct SQL template is tracked independently")
    void distinctTemplatesTrackedIndependently() { // GH-90000
        NplusOneDetector.setThreshold(3); // GH-90000
        NplusOneDetector.beginScope("test-scope");

        String sql1 = "SELECT * FROM orders WHERE id = ?";
        String sql2 = "SELECT * FROM items WHERE order_id = ?";

        for (int i = 0; i < 3; i++) { // GH-90000
            NplusOneDetector.recordQuery(sql1); // GH-90000
        }
        for (int i = 0; i < 2; i++) { // GH-90000
            NplusOneDetector.recordQuery(sql2); // GH-90000
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000
        // Only sql1 exceeds threshold (3 >= 3), sql2 does not (2 < 3) // GH-90000
        assertThat(violations).hasSize(1); // GH-90000
        assertThat(violations.get(0).querySql()).isEqualTo(sql1); // GH-90000
    }

    // ── Aggregate statistics ──────────────────────────────────────────────────

    @Test
    @DisplayName("aggregate violations are accumulated across multiple scopes")
    void aggregateViolationsAccumulateAcrossScopes() { // GH-90000
        NplusOneDetector.resetStats(); // GH-90000
        NplusOneDetector.setThreshold(2); // GH-90000
        String sql = "SELECT * FROM products WHERE category_id = ?";

        // First scope
        NplusOneDetector.beginScope("scope-1");
        for (int i = 0; i < 3; i++) { // GH-90000
            NplusOneDetector.recordQuery(sql); // GH-90000
        }
        NplusOneDetector.endScope(); // GH-90000

        // Second scope
        NplusOneDetector.beginScope("scope-2");
        for (int i = 0; i < 4; i++) { // GH-90000
            NplusOneDetector.recordQuery(sql); // GH-90000
        }
        NplusOneDetector.endScope(); // GH-90000

        Map<String, Integer> aggregates = NplusOneDetector.getAggregateViolations(); // GH-90000
        assertThat(aggregates).containsKey(sql); // GH-90000
        assertThat(aggregates.get(sql)).isEqualTo(7); // 3 + 4 // GH-90000
    }

    @Test
    @DisplayName("resetStats clears all aggregate violation counts")
    void resetStatsClearsAggregates() { // GH-90000
        NplusOneDetector.setThreshold(2); // GH-90000
        NplusOneDetector.beginScope("s");
        NplusOneDetector.recordQuery("SELECT 1");
        NplusOneDetector.recordQuery("SELECT 1");
        NplusOneDetector.endScope(); // GH-90000

        NplusOneDetector.resetStats(); // GH-90000

        assertThat(NplusOneDetector.getAggregateViolations()).isEmpty(); // GH-90000
    }

    // ── setThreshold ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("setThreshold below minimum is clamped to 2")
    void thresholdClampedToMinimum() { // GH-90000
        NplusOneDetector.setThreshold(0); // Should clamp to 2 // GH-90000
        NplusOneDetector.beginScope("scope");
        NplusOneDetector.recordQuery("SELECT * FROM t");
        NplusOneDetector.recordQuery("SELECT * FROM t"); // exactly at clamped threshold (2)

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000
        assertThat(violations).hasSize(1); // GH-90000
    }

    // ── Scope is isolated per thread ──────────────────────────────────────────

    @Test
    @DisplayName("violation Violation record preserves scope name, SQL, and count")
    void violationRecordPreservesAllFields() { // GH-90000
        NplusOneDetector.setThreshold(2); // GH-90000
        NplusOneDetector.beginScope("unit-test-scope");
        String sql = "SELECT id FROM audit_log WHERE user_id = ?";
        NplusOneDetector.recordQuery(sql); // GH-90000
        NplusOneDetector.recordQuery(sql); // GH-90000

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope(); // GH-90000

        assertThat(violations).hasSize(1); // GH-90000
        NplusOneDetector.Violation violation = violations.get(0); // GH-90000
        assertThat(violation.scopeName()).isEqualTo("unit-test-scope");
        assertThat(violation.querySql()).isEqualTo(sql); // GH-90000
        assertThat(violation.executionCount()).isEqualTo(2); // GH-90000
        assertThat(violation.scopeDuration()).isNotNull(); // GH-90000
    }
}
