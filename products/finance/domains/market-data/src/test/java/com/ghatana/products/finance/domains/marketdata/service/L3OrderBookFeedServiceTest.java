package com.ghatana.products.finance.domains.marketdata.service;

import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify L3 feed throttling reuses the shared platform limiter
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("L3 Order Book Feed Service Tests")
class L3OrderBookFeedServiceTest extends EventloopTestBase {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    @DisplayName("Should enforce the registered per-subscriber publish rate")
    void shouldEnforceRegisteredSubscriberRate() {
        List<Object> publishedEvents = new CopyOnWriteArrayList<>();
        EventBusPort eventBusPort = publishedEvents::add;
        L3OrderBookFeedService service = new L3OrderBookFeedService(DIRECT_EXECUTOR, eventBusPort);
        L3OrderBookFeedService.L3OrderEvent event = new L3OrderBookFeedService.L3OrderEvent(
                "BTC-USD",
                "order-1",
                "BUY",
                BigDecimal.TEN,
                5L,
                "NEW"
        );

        service.registerSubscriber("sub-1", 2);

        assertThat(runPromise(() -> service.publishL3Event(event, "sub-1"))).isTrue();
        assertThat(runPromise(() -> service.publishL3Event(event, "sub-1"))).isTrue();
        assertThat(runPromise(() -> service.publishL3Event(event, "sub-1"))).isFalse();
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("Should reset subscriber throttling when deregistered")
    void shouldResetSubscriberThrottleWhenDeregistered() {
        List<Object> publishedEvents = new CopyOnWriteArrayList<>();
        EventBusPort eventBusPort = publishedEvents::add;
        L3OrderBookFeedService service = new L3OrderBookFeedService(DIRECT_EXECUTOR, eventBusPort);
        L3OrderBookFeedService.L3OrderEvent event = new L3OrderBookFeedService.L3OrderEvent(
                "ETH-USD",
                "order-2",
                "SELL",
                BigDecimal.ONE,
                3L,
                "MODIFY"
        );

        service.registerSubscriber("sub-2", 1);

        assertThat(runPromise(() -> service.publishL3Event(event, "sub-2"))).isTrue();
        assertThat(runPromise(() -> service.publishL3Event(event, "sub-2"))).isFalse();

        service.deregisterSubscriber("sub-2");

        assertThat(runPromise(() -> service.publishL3Event(event, "sub-2"))).isTrue();
        assertThat(publishedEvents).hasSize(2);
    }
}