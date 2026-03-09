package com.ghatana.yappc.refactorer.event;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-memory implementation of EventBus for single-JVM event distribution.
 *
 * <p>Provides non-persistent, in-memory pub-sub event bus for development,
 * testing, and single-instance deployments.</p>
 
 * @doc.type class
 * @doc.purpose Handles in memory event bus operations
 * @doc.layer core
 * @doc.pattern Implementation
* @doc.gaa.memory episodic
*/
public final class InMemoryEventBus implements EventBus {

    private static final Logger log = LogManager.getLogger(InMemoryEventBus.class);

    private final List<EventHandler> handlers = new CopyOnWriteArrayList<>();
    private final AtomicLong eventCount = new AtomicLong(0);

    @Override
    public Promise<Void> publish(Event event) {
        if (event == null) {
            return Promise.complete();
        }

        eventCount.incrementAndGet();
        log.debug("Publishing event: {} ({})", event.getId(), event.getType());

        List<Promise<Void>> promises = new ArrayList<>();
        for (EventHandler handler : handlers) {
            if (handler.canHandle(event)) {
                promises.add(handler.handleEvent(event)
                        .whenException(ex -> log.error("Error handling event: {} ({})",
                                event.getId(), event.getType(), ex)));
            }
        }

        if (promises.isEmpty()) {
            return Promise.complete();
        }

        return Promises.all(promises.toArray(new Promise[0])).map(ignored -> null);
    }

    @Override
    public Promise<Void> subscribe(EventHandler handler) {
        if (handler == null) {
            return Promise.complete();
        }

        handlers.add(handler);
        log.debug("Subscribed handler for event types: {}",
                String.join(", ", handler.getSupportedEventTypes()));
        return Promise.complete();
    }

    @Override
    public Promise<Void> unsubscribe(EventHandler handler) {
        if (handler == null) {
            return Promise.complete();
        }

        handlers.remove(handler);
        log.debug("Unsubscribed handler for event types: {}",
                String.join(", ", handler.getSupportedEventTypes()));
        return Promise.complete();
    }

    @Override
    public int getHandlerCount() {
        return handlers.size();
    }

    @Override
    public long getEventCount() {
        return eventCount.get();
    }

    @Override
    public Promise<Void> clear() {
        handlers.clear();
        eventCount.set(0);
        log.debug("Event bus cleared");
        return Promise.complete();
    }
}
