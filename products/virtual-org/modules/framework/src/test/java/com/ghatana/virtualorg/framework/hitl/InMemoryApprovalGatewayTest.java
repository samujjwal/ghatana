package com.ghatana.virtualorg.framework.hitl;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for InMemoryApprovalGateway.
 *
 * @doc.type class
 * @doc.purpose Unit tests for approval gateway
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryApprovalGateway Tests")
class InMemoryApprovalGatewayTest extends EventloopTestBase {

    private InMemoryApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new InMemoryApprovalGateway(new MockMetricsCollector());
    }

    @Test
    @DisplayName("Should create approval request")
    void shouldCreateApprovalRequest() {
        // WHEN
        ApprovalRequest request = runPromise(() -> gateway.requestApproval(
                "Deploy to production",
                "devops-agent-001",
                ApprovalContext.of("Fix critical bug"),
                Duration.ofHours(4)
        ));

        // THEN
        assertThat(request).isNotNull();
        assertThat(request.getId()).startsWith("approval-");
        assertThat(request.getAction()).isEqualTo("Deploy to production");
        assertThat(request.getRequestorAgentId()).isEqualTo("devops-agent-001");
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("Should approve pending request")
    void shouldApprovePendingRequest() {
        // GIVEN
        ApprovalRequest request = runPromise(() -> gateway.requestApproval(
                "Deploy",
                "agent-001",
                ApprovalContext.empty(),
                Duration.ofHours(1)
        ));

        // WHEN
        runPromise(() -> gateway.approve(request.getId(), "john@company.com", "Looks good"));

        // THEN
        ApprovalStatus status = runPromise(() -> gateway.checkStatus(request.getId()));
        assertThat(status).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("Should reject pending request")
    void shouldRejectPendingRequest() {
        // GIVEN
        ApprovalRequest request = runPromise(() -> gateway.requestApproval(
                "Delete database",
                "agent-001",
                ApprovalContext.empty(),
                Duration.ofHours(1)
        ));

        // WHEN
        runPromise(() -> gateway.reject(request.getId(), "john@company.com", "Too risky"));

        // THEN
        ApprovalStatus status = runPromise(() -> gateway.checkStatus(request.getId()));
        assertThat(status).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    @DisplayName("Should list pending approvals")
    void shouldListPendingApprovals() {
        // GIVEN
        runPromise(() -> gateway.requestApproval(
                "Action 1", "agent-001", ApprovalContext.empty(), Duration.ofHours(1)));
        runPromise(() -> gateway.requestApproval(
                "Action 2", "agent-002", ApprovalContext.empty(), Duration.ofHours(1)));

        // WHEN
        List<ApprovalRequest> pending = runPromise(gateway::getPendingApprovals);

        // THEN
        assertThat(pending).hasSize(2);
    }

    @Test
    @DisplayName("Should not allow double approval")
    void shouldNotAllowDoubleApproval() {
        // GIVEN
        ApprovalRequest request = runPromise(() -> gateway.requestApproval(
                "Action",
                "agent-001",
                ApprovalContext.empty(),
                Duration.ofHours(1)
        ));
        runPromise(() -> gateway.approve(request.getId(), "john@company.com", "OK"));

        // THEN
        assertThatThrownBy(()
                -> runPromise(() -> gateway.approve(request.getId(), "jane@company.com", "Also OK"))
        ).hasMessageContaining("not pending");
    }

    @Test
    @DisplayName("Should notify listeners on approval")
    void shouldNotifyListenersOnApproval() {
        // GIVEN
        AtomicBoolean notified = new AtomicBoolean(false);
        gateway.addListener(new ApprovalGateway.ApprovalListener() {
            @Override
            public void onApproved(ApprovalRequest request, String approver) {
                notified.set(true);
            }
        });

        ApprovalRequest request = runPromise(() -> gateway.requestApproval(
                "Action",
                "agent-001",
                ApprovalContext.empty(),
                Duration.ofHours(1)
        ));

        // WHEN
        runPromise(() -> gateway.approve(request.getId(), "approver", "OK"));

        // THEN
        assertThat(notified.get()).isTrue();
    }

    @Test
    @DisplayName("Should cancel pending request")
    void shouldCancelPendingRequest() {
        // GIVEN
        ApprovalRequest request = runPromise(() -> gateway.requestApproval(
                "Action",
                "agent-001",
                ApprovalContext.empty(),
                Duration.ofHours(1)
        ));

        // WHEN
        runPromise(() -> gateway.cancel(request.getId()));

        // THEN
        ApprovalStatus status = runPromise(() -> gateway.checkStatus(request.getId()));
        assertThat(status).isEqualTo(ApprovalStatus.CANCELLED);
    }

    // ========== Mock ==========
    private static class MockMetricsCollector implements MetricsCollector {

        @Override
        public void increment(String metricName, double amount, java.util.Map<String, String> tags) {
        }

        @Override
        public void recordError(String metricName, Exception e, java.util.Map<String, String> tags) {
        }

        @Override
        public void incrementCounter(String name, String... tags) {
        }

        @Override
        public MeterRegistry getMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
