/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP6: Runtime safety — DefaultInvariantMonitor, InvariantContext,
 * AgentExecutionGrant, DelegationGrant, invariant violation detection.
 */
@DisplayName("Runtime Safety (WP6)")
class RuntimeSafetyTest {

    // =========================================================================
    // DefaultInvariantMonitor
    // =========================================================================

    @Nested
    @DisplayName("DefaultInvariantMonitor")
    class InvariantMonitorTests {

        private DefaultInvariantMonitor monitor;

        @BeforeEach
        void setUp() { // GH-90000
            monitor = new DefaultInvariantMonitor(); // GH-90000
        }

        @Test
        @DisplayName("should have 4 built-in rules")
        void shouldHaveFourBuiltInRules() { // GH-90000
            assertThat(monitor.getRules()).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("no violations for healthy context")
        void noViolationsForHealthyContext() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    0.5, 10.0,   // cost $0.50 / cap $10
                    1, 5,         // depth 1 / max 5
                    3, 100,       // actions 3 / max 100
                    Instant.now(), 300, // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should detect budget cap exceeded")
        void shouldDetectBudgetExceeded() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    15.0, 10.0,   // over budget
                    0, 5, 0, 100,
                    Instant.now(), 300, // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations).anyMatch(v -> // GH-90000
                    v.invariantId().equals("safety.budget-cap")
                            && v.description().contains("Budget exceeded"));
        }

        @Test
        @DisplayName("should detect delegation depth exceeded")
        void shouldDetectDelegationDepthExceeded() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    0, 10,
                    10, 3,        // depth 10 > max 3
                    0, 100,
                    Instant.now(), 300, // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations).anyMatch(v -> // GH-90000
                    v.invariantId().equals("safety.delegation-depth"));
        }

        @Test
        @DisplayName("should detect action budget exceeded")
        void shouldDetectActionBudgetExceeded() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5,
                    200, 100,     // actions 200 > max 100
                    Instant.now(), 300, // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations).anyMatch(v -> // GH-90000
                    v.invariantId().equals("safety.action-budget"));
        }

        @Test
        @DisplayName("should detect turn timeout exceeded")
        void shouldDetectTurnTimeoutExceeded() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5, 0, 100,
                    Instant.now().minusSeconds(600), 300, // 600s > 300s max // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations).anyMatch(v -> // GH-90000
                    v.invariantId().equals("safety.turn-timeout"));
        }

        @Test
        @DisplayName("CRITICAL violations should terminate turn")
        void criticalViolationsShouldTerminateTurn() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    15.0, 10.0, // over budget → CRITICAL
                    0, 5, 0, 100,
                    Instant.now(), 300, // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations) // GH-90000
                    .filteredOn(v -> v.severity() == InvariantViolation.Severity.CRITICAL) // GH-90000
                    .allMatch(v -> v.responseTaken() == InvariantViolation.ResponseAction.TURN_TERMINATED); // GH-90000
        }

        @Test
        @DisplayName("WARNING violations should be logged only")
        void warningViolationsShouldBeLogged() { // GH-90000
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5,
                    200, 100, // action budget exceeded → WARNING
                    Instant.now(), 300, // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000

            assertThat(violations) // GH-90000
                    .filteredOn(v -> v.severity() == InvariantViolation.Severity.WARNING) // GH-90000
                    .allMatch(v -> v.responseTaken() == InvariantViolation.ResponseAction.LOGGED); // GH-90000
        }

        @Test
        @DisplayName("should support registration of custom rules")
        void shouldSupportCustomRules() { // GH-90000
            InvariantRule customRule = new InvariantRule() { // GH-90000
                @Override public String getId() { return "custom.rule"; } // GH-90000
                @Override public String getDescription() { return "Custom check"; } // GH-90000
                @Override public InvariantViolation.Severity getSeverity() { // GH-90000
                    return InvariantViolation.Severity.INFO;
                }
                @Override public Optional<String> evaluate(InvariantContext ctx) { // GH-90000
                    return Optional.of("Custom violation detected");
                }
            };

            monitor.register(customRule); // GH-90000

            assertThat(monitor.getRules()).hasSize(5); // GH-90000

            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5, 0, 100,
                    Instant.now(), 300, Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000
            assertThat(violations).anyMatch(v -> // GH-90000
                    v.invariantId().equals("custom.rule")
                            && v.description().contains("Custom violation"));
        }

        @Test
        @DisplayName("zero caps should disable their invariant check")
        void zeroCapsDisableCheck() { // GH-90000
            // costCap=0, maxDelegation=0, maxActions=0, maxDuration=0 → all disabled
            InvariantContext ctx = new InvariantContext( // GH-90000
                    "agent-1", "tenant-1", "trace-1",
                    999.0, 0.0,   // costCap=0 → check disabled
                    99, 0,         // maxDepth=0 → check disabled
                    999, 0,        // maxActions=0 → check disabled
                    Instant.now().minusSeconds(99999), 0, // maxDuration=0 → check disabled // GH-90000
                    Map.of()); // GH-90000

            List<InvariantViolation> violations = monitor.evaluate(ctx); // GH-90000
            assertThat(violations).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // AgentExecutionGrant
    // =========================================================================

    @Nested
    @DisplayName("AgentExecutionGrant")
    class ExecutionGrantTests {

        @Test
        @DisplayName("should be valid when not expired and not revoked")
        void shouldBeValidWhenNotExpiredAndNotRevoked() { // GH-90000
            AgentExecutionGrant grant = AgentExecutionGrant.create( // GH-90000
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ", "WRITE_REVERSIBLE"), // GH-90000
                    10.0, 3, 50,
                    Duration.ofMinutes(30)); // GH-90000

            assertThat(grant.isValid()).isTrue(); // GH-90000
            assertThat(grant.permitsAction("READ")).isTrue();
            assertThat(grant.permitsAction("WRITE_REVERSIBLE")).isTrue();
            assertThat(grant.permitsAction("WRITE_IRREVERSIBLE")).isFalse();
        }

        @Test
        @DisplayName("should be invalid when expired")
        void shouldBeInvalidWhenExpired() { // GH-90000
            AgentExecutionGrant grant = new AgentExecutionGrant( // GH-90000
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ"), 10.0, 3, 50,
                    Instant.now().minusSeconds(3600), // GH-90000
                    Instant.now().minusSeconds(60), // GH-90000
                    false);

            assertThat(grant.isValid()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should be invalid when revoked")
        void shouldBeInvalidWhenRevoked() { // GH-90000
            AgentExecutionGrant grant = new AgentExecutionGrant( // GH-90000
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ"), 10.0, 3, 50,
                    Instant.now(), // GH-90000
                    Instant.now().plusSeconds(3600), // GH-90000
                    true); // revoked

            assertThat(grant.isValid()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("action classes should be immutable copies")
        void actionClassesShouldBeImmutable() { // GH-90000
            AgentExecutionGrant grant = AgentExecutionGrant.create( // GH-90000
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ"), 10.0, 3, 50,
                    Duration.ofMinutes(5)); // GH-90000

            assertThatThrownBy(() -> grant.allowedActionClasses().add("WRITE_IRREVERSIBLE"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // =========================================================================
    // DelegationGrant
    // =========================================================================

    @Nested
    @DisplayName("DelegationGrant")
    class DelegationGrantTests {

        @Test
        @DisplayName("should construct valid delegation grant")
        void shouldConstructValidGrant() { // GH-90000
            DelegationGrant grant = new DelegationGrant( // GH-90000
                    "del-1", "parent-agent", "child-agent",
                    "tenant-1", "trace-1",
                    1, 3,
                    Set.of("READ", "DRAFT"), // GH-90000
                    5.0,
                    Instant.now(), // GH-90000
                    Instant.now().plusSeconds(1800)); // GH-90000

            assertThat(grant.depth()).isEqualTo(1); // GH-90000
            assertThat(grant.parentAgentId()).isEqualTo("parent-agent");
            assertThat(grant.childAgentId()).isEqualTo("child-agent");
            assertThat(grant.allowedActionClasses()).containsExactlyInAnyOrder("READ", "DRAFT"); // GH-90000
        }

        @Test
        @DisplayName("should reject depth exceeding maxDepth")
        void shouldRejectDepthExceedingMax() { // GH-90000
            assertThatThrownBy(() -> new DelegationGrant( // GH-90000
                    "del-1", "parent", "child",
                    "tenant-1", "trace-1",
                    5, 3,  // depth 5 > maxDepth 3
                    Set.of("READ"), 5.0,
                    Instant.now(), // GH-90000
                    Instant.now().plusSeconds(1800))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("exceeds max");
        }

        @Test
        @DisplayName("should reject negative depth")
        void shouldRejectNegativeDepth() { // GH-90000
            assertThatThrownBy(() -> new DelegationGrant( // GH-90000
                    "del-1", "parent", "child",
                    "tenant-1", "trace-1",
                    -1, 3,
                    Set.of("READ"), 5.0,
                    Instant.now(), // GH-90000
                    Instant.now().plusSeconds(1800))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("should allow depth 0 at maxDepth 0 (root only)")
        void shouldAllowDepthZeroAtMaxZero() { // GH-90000
            DelegationGrant grant = new DelegationGrant( // GH-90000
                    "del-1", "parent", "child",
                    "tenant-1", "trace-1",
                    0, 0,
                    Set.of("READ"), 5.0,
                    Instant.now(), // GH-90000
                    Instant.now().plusSeconds(1800)); // GH-90000

            assertThat(grant.depth()).isEqualTo(0); // GH-90000
        }
    }

    // =========================================================================
    // InvariantViolation
    // =========================================================================

    @Nested
    @DisplayName("InvariantViolation")
    class InvariantViolationTests {

        @Test
        @DisplayName("should construct with all field values")
        void shouldConstructWithAllFields() { // GH-90000
            InvariantViolation violation = new InvariantViolation( // GH-90000
                    "v-1", "safety.budget-cap",
                    "agent-1", "tenant-1",
                    InvariantViolation.Severity.CRITICAL,
                    "Budget exceeded: $15.00 > cap $10.00",
                    InvariantViolation.ResponseAction.TURN_TERMINATED,
                    Map.of("traceId", "trace-1"), // GH-90000
                    Instant.now()); // GH-90000

            assertThat(violation.violationId()).isEqualTo("v-1");
            assertThat(violation.severity()).isEqualTo(InvariantViolation.Severity.CRITICAL); // GH-90000
            assertThat(violation.responseTaken()).isEqualTo( // GH-90000
                    InvariantViolation.ResponseAction.TURN_TERMINATED);
            assertThat(violation.context()).containsEntry("traceId", "trace-1"); // GH-90000
        }

        @Test
        @DisplayName("Severity enum should have 4 levels")
        void severityShouldHaveFourLevels() { // GH-90000
            assertThat(InvariantViolation.Severity.values()).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("ResponseAction enum should have 6 actions")
        void responseActionShouldHaveSixActions() { // GH-90000
            assertThat(InvariantViolation.ResponseAction.values()).hasSize(6); // GH-90000
        }
    }
}
