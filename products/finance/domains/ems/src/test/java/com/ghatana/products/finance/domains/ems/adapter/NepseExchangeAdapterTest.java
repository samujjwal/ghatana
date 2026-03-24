/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.ems.adapter;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.products.finance.domains.ems.domain.ExecutionFill;
import com.ghatana.products.finance.domains.ems.service.FixProtocolService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NepseExchangeAdapter} focusing on EventBusPort integration (F16),
 * execution report handling, and domain event publishing.
 *
 * @doc.type class
 * @doc.purpose Tests for NepseExchangeAdapter EventBusPort publishing
 * @doc.layer Adapter
 * @doc.pattern Test
 */
@DisplayName("NepseExchangeAdapter")
class NepseExchangeAdapterTest extends EventloopTestBase {

    private final List<Object> publishedEvents = new ArrayList<>();
    private final EventBusPort eventBus = publishedEvents::add;
    private NepseExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry metricsRegistry = new SimpleMeterRegistry();

        // FixProtocolService needs eventloop, busPort, metrics, executor
        FixProtocolService fixEngine = new FixProtocolService(
                eventBus, metricsRegistry, eventloop(),
                Executors.newSingleThreadExecutor());

        adapter = new NepseExchangeAdapter(fixEngine, eventBus, metricsRegistry);
    }

    @Nested
    @DisplayName("handleExecReport - fill events")
    class FillEvents {

        @Test
        @DisplayName("publishes ExecutionFillReceivedEvent on partial fill")
        void publishesEventOnPartialFill() {
            adapter.handleExecReport("EXEC-1", "routing-001", "1", 50, "125.50");

            assertEquals(1, publishedEvents.size());
            assertInstanceOf(NepseExchangeAdapter.ExecutionFillReceivedEvent.class,
                    publishedEvents.get(0));

            var event = (NepseExchangeAdapter.ExecutionFillReceivedEvent) publishedEvents.get(0);
            assertEquals("routing-001", event.routingId());
            assertEquals("NEPSE", event.exchange());
            assertNotNull(event.fill());
            assertEquals(50, event.fill().filledQuantity());
        }

        @Test
        @DisplayName("publishes ExecutionFillReceivedEvent on full fill")
        void publishesEventOnFullFill() {
            adapter.handleExecReport("EXEC-2", "routing-002", "2", 100, "200.00");

            assertEquals(1, publishedEvents.size());
            var event = (NepseExchangeAdapter.ExecutionFillReceivedEvent) publishedEvents.get(0);
            assertEquals("routing-002", event.routingId());
            assertEquals(100, event.fill().filledQuantity());
        }

        @Test
        @DisplayName("invokes consumer callback alongside EventBusPort")
        void callbackAndEventBothFire() {
            AtomicReference<ExecutionFill> callbackFill = new AtomicReference<>();
            adapter.onExecutionReport(callbackFill::set);

            adapter.handleExecReport("EXEC-3", "routing-003", "1", 25, "80.00");

            // Both the callback and the eventBus should have been invoked
            assertNotNull(callbackFill.get());
            assertEquals("routing-003", callbackFill.get().routingId());
            assertEquals(1, publishedEvents.size());
        }

        @Test
        @DisplayName("publishes event even when no callback registered")
        void publishesWithoutCallback() {
            // Do NOT register any callback
            adapter.handleExecReport("EXEC-4", "routing-004", "2", 75, "150.00");

            assertEquals(1, publishedEvents.size());
            var event = (NepseExchangeAdapter.ExecutionFillReceivedEvent) publishedEvents.get(0);
            assertEquals(75, event.fill().filledQuantity());
        }
    }

    @Nested
    @DisplayName("handleExecReport - rejection events")
    class RejectionEvents {

        @Test
        @DisplayName("publishes OrderRejectedByExchangeEvent on rejection")
        void publishesRejectionEvent() {
            adapter.handleExecReport("EXEC-5", "routing-005", "8", 0, null);

            assertEquals(1, publishedEvents.size());
            assertInstanceOf(NepseExchangeAdapter.OrderRejectedByExchangeEvent.class,
                    publishedEvents.get(0));

            var event = (NepseExchangeAdapter.OrderRejectedByExchangeEvent) publishedEvents.get(0);
            assertEquals("routing-005", event.routingId());
        }
    }

    @Nested
    @DisplayName("handleExecReport - non-fill events")
    class NonFillEvents {

        @Test
        @DisplayName("does not publish events on cancel confirmation")
        void noEventOnCancel() {
            adapter.handleExecReport("EXEC-6", "routing-006", "4", 0, null);

            assertTrue(publishedEvents.isEmpty());
        }

        @Test
        @DisplayName("does not publish events on unknown exec type")
        void noEventOnUnknown() {
            adapter.handleExecReport("EXEC-7", "routing-007", "Z", 0, null);

            assertTrue(publishedEvents.isEmpty());
        }
    }

    @Nested
    @DisplayName("adapter construction")
    class Construction {

        @Test
        @DisplayName("rejects null EventBusPort")
        void rejectsNullEventBus() {
            SimpleMeterRegistry metrics = new SimpleMeterRegistry();
            FixProtocolService fixEngine = new FixProtocolService(
                    eventBus, metrics, eventloop(),
                    Executors.newSingleThreadExecutor());

            assertThrows(NullPointerException.class,
                    () -> new NepseExchangeAdapter(fixEngine, null, metrics));
        }

        @Test
        @DisplayName("reports NEPSE as exchange ID")
        void exchangeId() {
            assertEquals("NEPSE", adapter.exchangeId());
        }
    }
}
