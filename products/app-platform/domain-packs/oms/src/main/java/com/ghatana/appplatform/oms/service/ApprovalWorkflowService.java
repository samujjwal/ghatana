package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.OrderStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Maker-checker approval workflow for orders (D01-011, D01-012).
 *              Manages PENDING → PENDING_APPROVAL → APPROVED/REJECTED transitions.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service, Maker-Checker Pattern
 */
public class ApprovalWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalWorkflowService.class);

    private final OrderStore orderStore;
    private final OrderLifecycleService lifecycleService;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public ApprovalWorkflowService(OrderStore orderStore, OrderLifecycleService lifecycleService,
                                    Executor executor, Consumer<Object> eventPublisher) {
        this.orderStore = orderStore;
        this.lifecycleService = lifecycleService;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sends an order to the approval queue (PENDING → PENDING_APPROVAL).
     */
    public Promise<Order> requestApproval(String orderId) {
        return lifecycleService.transition(orderId, OrderStatus.PENDING_APPROVAL, null);
    }

    /**
     * Approves a pending order (PENDING_APPROVAL → APPROVED).
     * Emits ApprovalGranted event.
     */
    public Promise<Order> approve(String orderId, String approverId, String notes) {
        return Promise.ofBlocking(executor, () -> {
            Order updated = lifecycleService.transition(orderId, OrderStatus.APPROVED, null).get();
            eventPublisher.accept(new ApprovalDecisionEvent(
                    UUID.randomUUID().toString(), orderId, "APPROVED", approverId, notes, Instant.now()));
            log.info("Order approved: orderId={} approver={}", orderId, approverId);
            return updated;
        });
    }

    /**
     * Rejects a pending order (PENDING_APPROVAL → REJECTED).
     */
    public Promise<Order> reject(String orderId, String approverId, String reason) {
        return Promise.ofBlocking(executor, () -> {
            Order updated = lifecycleService.transition(orderId, OrderStatus.REJECTED, reason).get();
            eventPublisher.accept(new ApprovalDecisionEvent(
                    UUID.randomUUID().toString(), orderId, "REJECTED", approverId, reason, Instant.now()));
            log.info("Order rejected: orderId={} approver={} reason={}", orderId, approverId, reason);
            return updated;
        });
    }

    /** Returns the pending approval queue for a given approver. */
    public Promise<List<Order>> getPendingApprovalQueue(String approverId) {
        return Promise.ofBlocking(executor, () ->
                orderStore.findPendingApproval(approverId).get());
    }

    public record ApprovalDecisionEvent(
            String decisionId, String orderId, String decision,
            String approverId, String notes, Instant at
    ) {}
}
