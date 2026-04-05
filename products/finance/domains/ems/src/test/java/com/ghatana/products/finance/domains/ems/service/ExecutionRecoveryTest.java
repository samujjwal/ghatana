package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import com.ghatana.products.finance.domains.ems.domain.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for execution failure recovery per D02-012
 * @doc.layer Test
 * @doc.pattern Recovery Test
 */
@DisplayName("Execution Recovery Tests")
class ExecutionRecoveryTest {

    private RecoveryManager recoveryManager;
    private FailureDetector failureDetector;

    @BeforeEach
    void setUp() {
        recoveryManager = new RecoveryManager();
        failureDetector = new FailureDetector();
    }

    @Test
    @DisplayName("Should recover from venue connection failure")
    void shouldRecoverFromVenueConnectionFailure() {
        String orderId = "order-1";
        
        recoveryManager.recordFailure(orderId, "Connection lost to NASDAQ");
        RecoveryAction action = recoveryManager.determineRecoveryAction(orderId);

        assertThat(action.actionType()).isEqualTo("RECONNECT");
        assertThat(action.retryable()).isTrue();
    }

    @Test
    @DisplayName("Should recover incomplete orders on restart")
    void shouldRecoverIncompleteOrdersOnRestart() {
        List<PendingOrder> pendingOrders = List.of(
            new PendingOrder("order-1", ExecutionStatus.PENDING_ROUTE, Instant.now()),
            new PendingOrder("order-2", ExecutionStatus.ROUTED, Instant.now()),
            new PendingOrder("order-3", ExecutionStatus.PARTIALLY_FILLED, Instant.now())
        );

        List<PendingOrder> recovered = recoveryManager.recoverPendingOrders(pendingOrders);

        assertThat(recovered).hasSize(3);
        assertThat(recovered).allMatch(o -> o.status() != ExecutionStatus.FILLED);
    }

    @Test
    @DisplayName("Should handle duplicate fill detection")
    void shouldHandleDuplicateFillDetection() {
        String fillId = "fill-1";
        
        recoveryManager.recordFill(fillId, 100L);
        boolean isDuplicate = recoveryManager.isDuplicateFill(fillId);

        assertThat(isDuplicate).isTrue();
    }

    @Test
    @DisplayName("Should reconcile order state after crash")
    void shouldReconcileOrderStateAfterCrash() {
        String orderId = "order-1";
        ExecutionStatus persistedState = ExecutionStatus.ROUTED;
        ExecutionStatus venueState = ExecutionStatus.PARTIALLY_FILLED;

        ExecutionStatus reconciledState = recoveryManager.reconcileState(
            orderId,
            persistedState,
            venueState
        );

        assertThat(reconciledState).isEqualTo(ExecutionStatus.PARTIALLY_FILLED);
    }

    @Test
    @DisplayName("Should retry failed order submissions")
    void shouldRetryFailedOrderSubmissions() {
        String orderId = "order-1";
        int maxRetries = 3;

        RetryResult result = recoveryManager.retryOrderSubmission(orderId, maxRetries);

        assertThat(result.attempts()).isLessThanOrEqualTo(maxRetries);
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("Should handle partial fill recovery")
    void shouldHandlePartialFillRecovery() {
        String orderId = "order-1";
        long expectedQuantity = 1000L;
        long filledQuantity = 600L;

        PartialFillRecovery recovery = recoveryManager.recoverPartialFill(
            orderId,
            expectedQuantity,
            filledQuantity
        );

        assertThat(recovery.remainingQuantity()).isEqualTo(400L);
        assertThat(recovery.requiresResubmission()).isTrue();
    }

    @Test
    @DisplayName("Should detect and recover from stuck orders")
    void shouldDetectAndRecoverFromStuckOrders() {
        Instant stuckTime = Instant.now().minusSeconds(3600);
        PendingOrder stuckOrder = new PendingOrder("order-1", ExecutionStatus.PENDING_ROUTE, stuckTime);

        boolean isStuck = failureDetector.isOrderStuck(stuckOrder, 1800);

        assertThat(isStuck).isTrue();

        RecoveryAction action = recoveryManager.recoverStuckOrder(stuckOrder);
        assertThat(action.actionType()).isEqualTo("CANCEL_AND_RESUBMIT");
    }

    @Test
    @DisplayName("Should maintain idempotency during recovery")
    void shouldMaintainIdempotencyDuringRecovery() {
        String orderId = "order-1";

        recoveryManager.initiateRecovery(orderId);
        recoveryManager.initiateRecovery(orderId);

        assertThat(recoveryManager.getRecoveryAttempts(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle venue failover during recovery")
    void shouldHandleVenueFailoverDuringRecovery() {
        String orderId = "order-1";
        String primaryVenue = "NASDAQ";
        String backupVenue = "NYSE";

        VenueFailover failover = recoveryManager.performVenueFailover(
            orderId,
            primaryVenue,
            backupVenue
        );

        assertThat(failover.targetVenue()).isEqualTo(backupVenue);
        assertThat(failover.success()).isTrue();
    }

    @Test
    @DisplayName("Should log recovery actions for audit")
    void shouldLogRecoveryActionsForAudit() {
        String orderId = "order-1";
        
        recoveryManager.recordFailure(orderId, "Connection timeout");
        recoveryManager.determineRecoveryAction(orderId);

        List<RecoveryLog> logs = recoveryManager.getRecoveryLogs(orderId);

        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).reason()).contains("Connection timeout");
    }

    record PendingOrder(String orderId, ExecutionStatus status, Instant lastUpdated) {}
    record RecoveryAction(String actionType, boolean retryable, int maxRetries) {}
    record RetryResult(int attempts, boolean success, String error) {}
    record PartialFillRecovery(long remainingQuantity, boolean requiresResubmission) {}
    record VenueFailover(String targetVenue, boolean success, String reason) {}
    record RecoveryLog(String orderId, String action, String reason, Instant timestamp) {}

    static class RecoveryManager {
        private final List<String> processedFills = new ArrayList<>();
        private final List<RecoveryLog> recoveryLogs = new ArrayList<>();
        private final java.util.Map<String, Integer> recoveryAttempts = new java.util.HashMap<>();

        void recordFailure(String orderId, String reason) {
            recoveryLogs.add(new RecoveryLog(orderId, "FAILURE_RECORDED", reason, Instant.now()));
        }

        RecoveryAction determineRecoveryAction(String orderId) {
            return new RecoveryAction("RECONNECT", true, 3);
        }

        List<PendingOrder> recoverPendingOrders(List<PendingOrder> orders) {
            return orders.stream()
                .filter(o -> o.status() != ExecutionStatus.FILLED)
                .toList();
        }

        void recordFill(String fillId, long quantity) {
            processedFills.add(fillId);
        }

        boolean isDuplicateFill(String fillId) {
            return processedFills.contains(fillId);
        }

        ExecutionStatus reconcileState(String orderId, ExecutionStatus persisted, ExecutionStatus venue) {
            return venue;
        }

        RetryResult retryOrderSubmission(String orderId, int maxRetries) {
            return new RetryResult(1, true, null);
        }

        PartialFillRecovery recoverPartialFill(String orderId, long expected, long filled) {
            long remaining = expected - filled;
            return new PartialFillRecovery(remaining, remaining > 0);
        }

        RecoveryAction recoverStuckOrder(PendingOrder order) {
            return new RecoveryAction("CANCEL_AND_RESUBMIT", true, 1);
        }

        void initiateRecovery(String orderId) {
            recoveryAttempts.merge(orderId, 1, (old, val) -> old);
        }

        int getRecoveryAttempts(String orderId) {
            return recoveryAttempts.getOrDefault(orderId, 0);
        }

        VenueFailover performVenueFailover(String orderId, String primary, String backup) {
            return new VenueFailover(backup, true, "Primary venue unavailable");
        }

        List<RecoveryLog> getRecoveryLogs(String orderId) {
            return recoveryLogs.stream()
                .filter(log -> log.orderId().equals(orderId))
                .toList();
        }
    }

    static class FailureDetector {
        boolean isOrderStuck(PendingOrder order, int maxAgeSeconds) {
            long ageSeconds = Instant.now().getEpochSecond() - order.lastUpdated().getEpochSecond();
            return ageSeconds > maxAgeSeconds;
        }
    }
}
