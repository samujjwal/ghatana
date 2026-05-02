/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void setUp() { 
            monitor = new DefaultInvariantMonitor(); 
        }

        @Test
        @DisplayName("should have 4 built-in rules")
        void shouldHaveFourBuiltInRules() { 
            assertThat(monitor.getRules()).hasSize(4); 
        }

        @Test
        @DisplayName("no violations for healthy context")
        void noViolationsForHealthyContext() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    0.5, 10.0,   // cost $0.50 / cap $10
                    1, 5,         // depth 1 / max 5
                    3, 100,       // actions 3 / max 100
                    Instant.now(), 300, 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations).isEmpty(); 
        }

        @Test
        @DisplayName("should detect budget cap exceeded")
        void shouldDetectBudgetExceeded() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    15.0, 10.0,   // over budget
                    0, 5, 0, 100,
                    Instant.now(), 300, 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations).anyMatch(v -> 
                    v.invariantId().equals("safety.budget-cap")
                            && v.description().contains("Budget exceeded"));
        }

        @Test
        @DisplayName("should detect delegation depth exceeded")
        void shouldDetectDelegationDepthExceeded() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    0, 10,
                    10, 3,        // depth 10 > max 3
                    0, 100,
                    Instant.now(), 300, 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations).anyMatch(v -> 
                    v.invariantId().equals("safety.delegation-depth"));
        }

        @Test
        @DisplayName("should detect action budget exceeded")
        void shouldDetectActionBudgetExceeded() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5,
                    200, 100,     // actions 200 > max 100
                    Instant.now(), 300, 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations).anyMatch(v -> 
                    v.invariantId().equals("safety.action-budget"));
        }

        @Test
        @DisplayName("should detect turn timeout exceeded")
        void shouldDetectTurnTimeoutExceeded() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5, 0, 100,
                    Instant.now().minusSeconds(600), 300, // 600s > 300s max 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations).anyMatch(v -> 
                    v.invariantId().equals("safety.turn-timeout"));
        }

        @Test
        @DisplayName("CRITICAL violations should terminate turn")
        void criticalViolationsShouldTerminateTurn() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    15.0, 10.0, // over budget → CRITICAL
                    0, 5, 0, 100,
                    Instant.now(), 300, 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations) 
                    .filteredOn(v -> v.severity() == InvariantViolation.Severity.CRITICAL) 
                    .allMatch(v -> v.responseTaken() == InvariantViolation.ResponseAction.TURN_TERMINATED); 
        }

        @Test
        @DisplayName("WARNING violations should be logged only")
        void warningViolationsShouldBeLogged() { 
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5,
                    200, 100, // action budget exceeded → WARNING
                    Instant.now(), 300, 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 

            assertThat(violations) 
                    .filteredOn(v -> v.severity() == InvariantViolation.Severity.WARNING) 
                    .allMatch(v -> v.responseTaken() == InvariantViolation.ResponseAction.LOGGED); 
        }

        @Test
        @DisplayName("should support registration of custom rules")
        void shouldSupportCustomRules() { 
            InvariantRule customRule = new InvariantRule() { 
                @Override public String getId() { return "custom.rule"; } 
                @Override public String getDescription() { return "Custom check"; } 
                @Override public InvariantViolation.Severity getSeverity() { 
                    return InvariantViolation.Severity.INFO;
                }
                @Override public Optional<String> evaluate(InvariantContext ctx) { 
                    return Optional.of("Custom violation detected");
                }
            };

            monitor.register(customRule); 

            assertThat(monitor.getRules()).hasSize(5); 

            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    0, 10, 0, 5, 0, 100,
                    Instant.now(), 300, Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 
            assertThat(violations).anyMatch(v -> 
                    v.invariantId().equals("custom.rule")
                            && v.description().contains("Custom violation"));
        }

        @Test
        @DisplayName("zero caps should disable their invariant check")
        void zeroCapsDisableCheck() { 
            // costCap=0, maxDelegation=0, maxActions=0, maxDuration=0 → all disabled
            InvariantContext ctx = new InvariantContext( 
                    "agent-1", "tenant-1", "trace-1",
                    999.0, 0.0,   // costCap=0 → check disabled
                    99, 0,         // maxDepth=0 → check disabled
                    999, 0,        // maxActions=0 → check disabled
                    Instant.now().minusSeconds(99999), 0, // maxDuration=0 → check disabled 
                    Map.of()); 

            List<InvariantViolation> violations = monitor.evaluate(ctx); 
            assertThat(violations).isEmpty(); 
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
        void shouldBeValidWhenNotExpiredAndNotRevoked() { 
            AgentExecutionGrant grant = AgentExecutionGrant.create( 
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ", "WRITE_REVERSIBLE"), 
                    10.0, 3, 50,
                    Duration.ofMinutes(30)); 

            assertThat(grant.isValid()).isTrue(); 
            assertThat(grant.permitsAction("READ")).isTrue();
            assertThat(grant.permitsAction("WRITE_REVERSIBLE")).isTrue();
            assertThat(grant.permitsAction("WRITE_IRREVERSIBLE")).isFalse();
        }

        @Test
        @DisplayName("should be invalid when expired")
        void shouldBeInvalidWhenExpired() { 
            AgentExecutionGrant grant = new AgentExecutionGrant( 
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ"), 10.0, 3, 50,
                    Instant.now().minusSeconds(3600), 
                    Instant.now().minusSeconds(60), 
                    false);

            assertThat(grant.isValid()).isFalse(); 
        }

        @Test
        @DisplayName("should be invalid when revoked")
        void shouldBeInvalidWhenRevoked() { 
            AgentExecutionGrant grant = new AgentExecutionGrant( 
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ"), 10.0, 3, 50,
                    Instant.now(), 
                    Instant.now().plusSeconds(3600), 
                    true); // revoked

            assertThat(grant.isValid()).isFalse(); 
        }

        @Test
        @DisplayName("action classes should be immutable copies")
        void actionClassesShouldBeImmutable() { 
            AgentExecutionGrant grant = AgentExecutionGrant.create( 
                    "grant-1", "agent-1", "tenant-1", "trace-1",
                    Set.of("READ"), 10.0, 3, 50,
                    Duration.ofMinutes(5)); 

            assertThatThrownBy(() -> grant.allowedActionClasses().add("WRITE_IRREVERSIBLE"))
                    .isInstanceOf(UnsupportedOperationException.class); 
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
        void shouldConstructValidGrant() { 
            DelegationGrant grant = new DelegationGrant( 
                    "del-1", "parent-agent", "child-agent",
                    "tenant-1", "trace-1",
                    1, 3,
                    Set.of("READ", "DRAFT"), 
                    5.0,
                    Instant.now(), 
                    Instant.now().plusSeconds(1800)); 

            assertThat(grant.depth()).isEqualTo(1); 
            assertThat(grant.parentAgentId()).isEqualTo("parent-agent");
            assertThat(grant.childAgentId()).isEqualTo("child-agent");
            assertThat(grant.allowedActionClasses()).containsExactlyInAnyOrder("READ", "DRAFT"); 
        }

        @Test
        @DisplayName("should reject depth exceeding maxDepth")
        void shouldRejectDepthExceedingMax() { 
            assertThatThrownBy(() -> new DelegationGrant( 
                    "del-1", "parent", "child",
                    "tenant-1", "trace-1",
                    5, 3,  // depth 5 > maxDepth 3
                    Set.of("READ"), 5.0,
                    Instant.now(), 
                    Instant.now().plusSeconds(1800))) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("exceeds max");
        }

        @Test
        @DisplayName("should reject negative depth")
        void shouldRejectNegativeDepth() { 
            assertThatThrownBy(() -> new DelegationGrant( 
                    "del-1", "parent", "child",
                    "tenant-1", "trace-1",
                    -1, 3,
                    Set.of("READ"), 5.0,
                    Instant.now(), 
                    Instant.now().plusSeconds(1800))) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("should allow depth 0 at maxDepth 0 (root only)")
        void shouldAllowDepthZeroAtMaxZero() { 
            DelegationGrant grant = new DelegationGrant( 
                    "del-1", "parent", "child",
                    "tenant-1", "trace-1",
                    0, 0,
                    Set.of("READ"), 5.0,
                    Instant.now(), 
                    Instant.now().plusSeconds(1800)); 

            assertThat(grant.depth()).isEqualTo(0); 
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
        void shouldConstructWithAllFields() { 
            InvariantViolation violation = new InvariantViolation( 
                    "v-1", "safety.budget-cap",
                    "agent-1", "tenant-1",
                    InvariantViolation.Severity.CRITICAL,
                    "Budget exceeded: $15.00 > cap $10.00",
                    InvariantViolation.ResponseAction.TURN_TERMINATED,
                    Map.of("traceId", "trace-1"), 
                    Instant.now()); 

            assertThat(violation.violationId()).isEqualTo("v-1");
            assertThat(violation.severity()).isEqualTo(InvariantViolation.Severity.CRITICAL); 
            assertThat(violation.responseTaken()).isEqualTo( 
                    InvariantViolation.ResponseAction.TURN_TERMINATED);
            assertThat(violation.context()).containsEntry("traceId", "trace-1"); 
        }

        @Test
        @DisplayName("Severity enum should have 4 levels")
        void severityShouldHaveFourLevels() { 
            assertThat(InvariantViolation.Severity.values()).hasSize(4); 
        }

        @Test
        @DisplayName("ResponseAction enum should have 6 actions")
        void responseActionShouldHaveSixActions() { 
            assertThat(InvariantViolation.ResponseAction.values()).hasSize(6); 
        }
    }
}
