/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.oms.service;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.OrderStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderLifecycleService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for 9-state order lifecycle (D01-004) and fill processing (D01-014)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderLifecycleService — Unit Tests")
class OrderLifecycleServiceTest extends EventloopTestBase {

    @Mock private OrderStore orderStore;
    @Mock private EventBusPort eventBusPort;

    private OrderLifecycleService service;

    @BeforeEach
    void setUp() {
        doNothing().when(eventBusPort).publish(any());
        service = new OrderLifecycleService(
                orderStore,
                Executors.newSingleThreadExecutor(),
                eventBusPort,
                new SimpleMeterRegistry()
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Order pendingOrder() {
        return Order.newOrder(
                "order-1", "client-1", "account-1",
                "instr-NEPSE-NICA", OrderSide.BUY, OrderType.LIMIT,
                TimeInForce.DAY,
                new BigDecimal("100"), new BigDecimal("250.00"), null,
                UUID.randomUUID().toString(),
                Instant.now(), "2081-10-15"
        );
    }

    private Order orderWithStatus(OrderStatus status) {
        return pendingOrder().withStatus(status);
    }

    // ─── Transition tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PENDING → APPROVED — valid transition succeeds")
    void transition_pendingToApproved_succeeds() {
        Order order = pendingOrder();
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(order)));
        when(orderStore.update(any())).thenReturn(Promise.of(null));

        Order updated = runPromise(() -> service.transition("order-1", OrderStatus.APPROVED, null));

        assertThat(updated.status()).isEqualTo(OrderStatus.APPROVED);
        verify(eventBusPort).publish(any(OrderLifecycleService.OrderStatusChangedEvent.class));
    }

    @Test
    @DisplayName("PENDING → REJECTED — valid terminal transition with reason")
    void transition_pendingToRejected_setsRejectionReason() {
        Order order = pendingOrder();
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(order)));
        when(orderStore.update(any())).thenReturn(Promise.of(null));

        Order updated = runPromise(() -> service.transition("order-1", OrderStatus.REJECTED, "Insufficient funds"));

        assertThat(updated.rejectionReason()).isEqualTo("Insufficient funds");
        verify(eventBusPort).publish(any());
    }

    @Test
    @DisplayName("FILLED → CANCELLED — invalid transition throws InvalidOrderTransitionException")
    void transition_filledToCancelled_throws() {
        Order filledOrder = orderWithStatus(OrderStatus.FILLED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(filledOrder)));

        assertThatThrownBy(() -> runPromise(() -> service.transition("order-1", OrderStatus.CANCELLED, null)))
                .hasCauseInstanceOf(OrderLifecycleService.InvalidOrderTransitionException.class)
                .hasMessageContaining("FILLED");
    }

    @Test
    @DisplayName("REJECTED → APPROVED — invalid transition from terminal state throws")
    void transition_rejectedToApproved_throws() {
        Order rejected = orderWithStatus(OrderStatus.REJECTED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(rejected)));

        assertThatThrownBy(() -> runPromise(() -> service.transition("order-1", OrderStatus.APPROVED, null)))
                .hasCauseInstanceOf(OrderLifecycleService.InvalidOrderTransitionException.class);
    }

    @Test
    @DisplayName("unknown order ID — throws OrderNotFoundException")
    void transition_unknownOrderId_throws() {
        when(orderStore.findById("ghost")).thenReturn(Promise.of(Optional.empty()));

        assertThatThrownBy(() -> runPromise(() -> service.transition("ghost", OrderStatus.APPROVED, null)))
                .hasCauseInstanceOf(OrderLifecycleService.OrderNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ─── Fill tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("first fill — status becomes PARTIALLY_FILLED and avgPrice is set")
    void applyFill_firstFill_setsPartiallyFilled() {
        Order routedOrder = orderWithStatus(OrderStatus.ROUTED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(routedOrder)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderStore.update(captor.capture())).thenReturn(Promise.of(null));

        runPromise(() -> service.applyFill("order-1", "exec-001",
                new BigDecimal("50"), new BigDecimal("251.00"), BigDecimal.ZERO));

        Order updated = captor.getValue();
        assertThat(updated.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(updated.filledQuantity()).isEqualByComparingTo("50");
        assertThat(updated.avgFillPrice()).isEqualByComparingTo("251.00000000");
    }

    @Test
    @DisplayName("fill completing the order — status becomes FILLED")
    void applyFill_completeFill_setsFilledStatus() {
        Order routedOrder = orderWithStatus(OrderStatus.ROUTED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(routedOrder)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderStore.update(captor.capture())).thenReturn(Promise.of(null));

        // Fill all 100 units
        runPromise(() -> service.applyFill("order-1", "exec-001",
                new BigDecimal("100"), new BigDecimal("250.50"), BigDecimal.ZERO));

        Order updated = captor.getValue();
        assertThat(updated.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(updated.remainingQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("duplicate execId — idempotent: second application is a no-op")
    void applyFill_duplicateExecId_isIdempotent() {
        // Create order that already has exec-001 in fills
        Order order = orderWithStatus(OrderStatus.ROUTED);
        Fill existingFill = new Fill("fill-id-1", "order-1", "exec-001",
                new BigDecimal("10"), new BigDecimal("250"), BigDecimal.ZERO, Instant.now());
        Order withFill = order.withFill(existingFill, new BigDecimal("250"));
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(withFill)));

        Order result = runPromise(() -> service.applyFill("order-1", "exec-001",
                new BigDecimal("10"), new BigDecimal("250"), BigDecimal.ZERO));

        // No update call should happen — idempotent
        verify(orderStore, never()).update(any());
        assertThat(result.fills()).hasSize(1);
    }

    @Test
    @DisplayName("weighted average fill price — computed correctly across two fills")
    void applyFill_weightedAveragePrice() {
        // First fill: 60 units @ 250 (already applied) → avg=250
        Order partialOrder = orderWithStatus(OrderStatus.ROUTED);
        Fill firstFill = new Fill("f1", "order-1", "exec-001",
                new BigDecimal("60"), new BigDecimal("250"), BigDecimal.ZERO, Instant.now());
        Order afterFirstFill = partialOrder.withFill(firstFill, new BigDecimal("250"));

        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(afterFirstFill)));
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderStore.update(captor.capture())).thenReturn(Promise.of(null));

        // Second fill: 40 units @ 260 → weighted avg = (60×250 + 40×260) / 100 = 254
        runPromise(() -> service.applyFill("order-1", "exec-002",
                new BigDecimal("40"), new BigDecimal("260"), BigDecimal.ZERO));

        Order updated = captor.getValue();
        assertThat(updated.avgFillPrice()).isEqualByComparingTo("254.00000000");
    }

    // ─── Cancel tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel ROUTED order — transitions to CANCELLED and emits event")
    void cancel_routedOrder_success() {
        Order routed = orderWithStatus(OrderStatus.ROUTED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(routed)));
        when(orderStore.update(any())).thenReturn(Promise.of(null));

        Order updated = runPromise(() -> service.cancel("order-1", "Client request"));

        assertThat(updated.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventBusPort).publish(any(OrderLifecycleService.OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("cancel FILLED order — invalid transition throws")
    void cancel_filledOrder_throws() {
        Order filled = orderWithStatus(OrderStatus.FILLED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(filled)));

        assertThatThrownBy(() -> runPromise(() -> service.cancel("order-1", "attempt")))
                .hasCauseInstanceOf(OrderLifecycleService.InvalidOrderTransitionException.class);
    }

    // ─── Transition matrix boundary tests ─────────────────────────────────────

    @Test
    @DisplayName("DRAFT → PENDING — allowed first transition")
    void transition_draftToPending_allowed() {
        Order draft = orderWithStatus(OrderStatus.DRAFT);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(draft)));
        when(orderStore.update(any())).thenReturn(Promise.of(null));

        Order updated = runPromise(() -> service.transition("order-1", OrderStatus.PENDING, null));

        assertThat(updated.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("CANCELLED → any — all transitions from terminal CANCELLED state are blocked")
    void transition_cancelledToAny_throws() {
        Order cancelled = orderWithStatus(OrderStatus.CANCELLED);
        when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(cancelled)));

        for (OrderStatus target : List.of(OrderStatus.PENDING, OrderStatus.APPROVED, OrderStatus.FILLED)) {
            reset(orderStore);
            when(orderStore.findById("order-1")).thenReturn(Promise.of(Optional.of(cancelled)));
            assertThatThrownBy(() -> runPromise(() -> service.transition("order-1", target, null)))
                    .hasCauseInstanceOf(OrderLifecycleService.InvalidOrderTransitionException.class);
        }
    }
}
