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
    void setUp() {
        NplusOneDetector.resetStats();
        NplusOneDetector.setThreshold(5); // reset to default
    }

    @AfterEach
    void tearDown() {
        // Ensure scope is always cleared even if test fails mid-scope
        NplusOneDetector.endScope();
        NplusOneDetector.resetStats();
    }

    // ── No active scope ───────────────────────────────────────────────────────

    @Test
    @DisplayName("recordQuery has no effect when no scope is active")
    void recordQueryNoopWithoutActiveScope() {
        NplusOneDetector.recordQuery("SELECT * FROM users WHERE id = ?");
        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("endScope returns empty list when no scope is active")
    void endScopeReturnsEmptyWhenNoScope() {
        List<NplusOneDetector.Violation> result = NplusOneDetector.endScope();
        assertThat(result).isEmpty();
    }

    // ── Within threshold ──────────────────────────────────────────────────────

    @Test
    @DisplayName("endScope returns no violations when queries are below threshold")
    void noViolationsBelowThreshold() {
        NplusOneDetector.setThreshold(5);
        NplusOneDetector.beginScope("GET /api/users");

        for (int i = 0; i < 4; i++) {
            NplusOneDetector.recordQuery("SELECT * FROM orders WHERE user_id = ?");
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();
        assertThat(violations).isEmpty();
    }

    // ── Threshold exceeded ────────────────────────────────────────────────────

    @Test
    @DisplayName("endScope returns violations when queries reach the threshold")
    void violationDetectedAtThreshold() {
        NplusOneDetector.setThreshold(3);
        NplusOneDetector.beginScope("GET /api/orders");
        String sql = "SELECT * FROM line_items WHERE order_id = ?";

        for (int i = 0; i < 3; i++) {
            NplusOneDetector.recordQuery(sql);
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).querySql()).isEqualTo(sql);
        assertThat(violations.get(0).executionCount()).isEqualTo(3);
        assertThat(violations.get(0).scopeName()).isEqualTo("GET /api/orders");
    }

    @Test
    @DisplayName("endScope reports violation count above threshold")
    void violationCountReflectsActualExecutions() {
        NplusOneDetector.setThreshold(2);
        NplusOneDetector.beginScope("scope");
        String sql = "SELECT name FROM users WHERE id = ?";

        for (int i = 0; i < 10; i++) {
            NplusOneDetector.recordQuery(sql);
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).executionCount()).isEqualTo(10);
    }

    // ── Multiple SQL templates ────────────────────────────────────────────────

    @Test
    @DisplayName("each distinct SQL template is tracked independently")
    void distinctTemplatesTrackedIndependently() {
        NplusOneDetector.setThreshold(3);
        NplusOneDetector.beginScope("test-scope");

        String sql1 = "SELECT * FROM orders WHERE id = ?";
        String sql2 = "SELECT * FROM items WHERE order_id = ?";

        for (int i = 0; i < 3; i++) {
            NplusOneDetector.recordQuery(sql1);
        }
        for (int i = 0; i < 2; i++) {
            NplusOneDetector.recordQuery(sql2);
        }

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();
        // Only sql1 exceeds threshold (3 >= 3), sql2 does not (2 < 3)
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).querySql()).isEqualTo(sql1);
    }

    // ── Aggregate statistics ──────────────────────────────────────────────────

    @Test
    @DisplayName("aggregate violations are accumulated across multiple scopes")
    void aggregateViolationsAccumulateAcrossScopes() {
        NplusOneDetector.resetStats();
        NplusOneDetector.setThreshold(2);
        String sql = "SELECT * FROM products WHERE category_id = ?";

        // First scope
        NplusOneDetector.beginScope("scope-1");
        for (int i = 0; i < 3; i++) {
            NplusOneDetector.recordQuery(sql);
        }
        NplusOneDetector.endScope();

        // Second scope
        NplusOneDetector.beginScope("scope-2");
        for (int i = 0; i < 4; i++) {
            NplusOneDetector.recordQuery(sql);
        }
        NplusOneDetector.endScope();

        Map<String, Integer> aggregates = NplusOneDetector.getAggregateViolations();
        assertThat(aggregates).containsKey(sql);
        assertThat(aggregates.get(sql)).isEqualTo(7); // 3 + 4
    }

    @Test
    @DisplayName("resetStats clears all aggregate violation counts")
    void resetStatsClearsAggregates() {
        NplusOneDetector.setThreshold(2);
        NplusOneDetector.beginScope("s");
        NplusOneDetector.recordQuery("SELECT 1");
        NplusOneDetector.recordQuery("SELECT 1");
        NplusOneDetector.endScope();

        NplusOneDetector.resetStats();

        assertThat(NplusOneDetector.getAggregateViolations()).isEmpty();
    }

    // ── setThreshold ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("setThreshold below minimum is clamped to 2")
    void thresholdClampedToMinimum() {
        NplusOneDetector.setThreshold(0); // Should clamp to 2
        NplusOneDetector.beginScope("scope");
        NplusOneDetector.recordQuery("SELECT * FROM t");
        NplusOneDetector.recordQuery("SELECT * FROM t"); // exactly at clamped threshold (2)

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();
        assertThat(violations).hasSize(1);
    }

    // ── Scope is isolated per thread ──────────────────────────────────────────

    @Test
    @DisplayName("violation Violation record preserves scope name, SQL, and count")
    void violationRecordPreservesAllFields() {
        NplusOneDetector.setThreshold(2);
        NplusOneDetector.beginScope("unit-test-scope");
        String sql = "SELECT id FROM audit_log WHERE user_id = ?";
        NplusOneDetector.recordQuery(sql);
        NplusOneDetector.recordQuery(sql);

        List<NplusOneDetector.Violation> violations = NplusOneDetector.endScope();

        assertThat(violations).hasSize(1);
        NplusOneDetector.Violation violation = violations.get(0);
        assertThat(violation.scopeName()).isEqualTo("unit-test-scope");
        assertThat(violation.querySql()).isEqualTo(sql);
        assertThat(violation.executionCount()).isEqualTo(2);
        assertThat(violation.scopeDuration()).isNotNull();
    }
}
