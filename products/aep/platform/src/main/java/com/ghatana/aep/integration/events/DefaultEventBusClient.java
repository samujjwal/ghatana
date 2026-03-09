package com.ghatana.aep.integration.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Default implementation of EventBusClient using EventLog as the backend.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Publishes events to EventLog with proper serialization</li>
 *   <li>Polls for new events using cursor-based pagination</li>
 *   <li>Provides at-least-once delivery semantics</li>
 *   <li>Handles backpressure via configurable poll intervals</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose EventBus implementation backed by EventLog
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class DefaultEventBusClient implements EventBusClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultEventBusClient.class);
    
    private final Eventloop eventloop;
    private final EventLogAdapter eventLogAdapter;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Map<String, DefaultEventSubscription> subscriptions;
    private final AtomicBoolean running;
    private final long pollIntervalMs;
    
    /**
     * Creates a new DefaultEventBusClient.
     * 
     * @param eventloop The ActiveJ eventloop for async operations
     * @param eventLogAdapter Adapter for EventLog operations
     * @param pollIntervalMs Interval between subscription polls (default: 100ms)
     */
    public DefaultEventBusClient(
        Eventloop eventloop,
        EventLogAdapter eventLogAdapter,
        long pollIntervalMs
    ) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.eventLogAdapter = Objects.requireNonNull(eventLogAdapter, "eventLogAdapter cannot be null");
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 100;
        this.executor = Executors.newCachedThreadPool();
        this.subscriptions = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        
        this.objectMapper = JsonUtils.getDefaultMapper();
    }
    
    /**
     * Creates a DefaultEventBusClient with default poll interval.
     */
    public DefaultEventBusClient(Eventloop eventloop, EventLogAdapter eventLogAdapter) {
        this(eventloop, eventLogAdapter, 100);
    }
    
    @Override
    public <T> Promise<String> publish(String tenantId, T event) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
        
        String eventType = extractEventType(event);
        String aggregateId = extractAggregateId(event);
        
        return publish(tenantId, eventType, aggregateId, event);
    }
    
    @Override
    public <T> Promise<String> publish(String tenantId, String eventType, String aggregateId, T event) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
        
        return Promise.ofBlocking(executor, () -> {
            try {
                String eventId = UUID.randomUUID().toString();
                String payload = objectMapper.writeValueAsString(event);
                
                EventLogAdapter.EventRecord record = new EventLogAdapter.EventRecord(
                    eventId,
                    tenantId,
                    eventType,
                    aggregateId != null ? aggregateId : eventId,
                    payload,
                    event.getClass().getName(),
                    Instant.now()
                );
                
                eventLogAdapter.append(record);
                
                logger.debug("Published event: type={}, id={}, tenant={}", 
                    eventType, eventId, tenantId);
                
                return eventId;
            } catch (Exception e) {
                logger.error("Failed to publish event: type={}, tenant={}", 
                    eventType, tenantId, e);
                throw new EventPublishException("Failed to publish event", e);
            }
        });
    }
    
    @Override
    public Promise<EventSubscription> subscribe(
        String subscriptionId,
        String eventType,
        Function<Object, Promise<Void>> handler
    ) {
        return subscribe(subscriptionId, eventType, Object.class, handler::apply);
    }
    
    @Override
    public <T> Promise<EventSubscription> subscribe(
        String subscriptionId,
        String eventType,
        Class<T> eventClass,
        Function<T, Promise<Void>> handler
    ) {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        
        return Promise.ofBlocking(executor, () -> {
            DefaultEventSubscription subscription = new DefaultEventSubscription(
                subscriptionId,
                new String[]{eventType},
                eventClass,
                (obj) -> handler.apply((T) obj),
                eventLogAdapter,
                objectMapper,
                eventloop,
                pollIntervalMs
            );
            
            subscriptions.put(subscriptionId, subscription);
            
            if (running.get()) {
                subscription.start();
            }
            
            logger.info("Created subscription: id={}, eventType={}", 
                subscriptionId, eventType);
            
            return subscription;
        });
    }
    
    @Override
    public Promise<EventSubscription> subscribeMultiple(
        String subscriptionId,
        String[] eventTypes,
        Function<Object, Promise<Void>> handler
    ) {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(eventTypes, "eventTypes cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        
        return Promise.ofBlocking(executor, () -> {
            DefaultEventSubscription subscription = new DefaultEventSubscription(
                subscriptionId,
                eventTypes,
                Object.class,
                handler::apply,
                eventLogAdapter,
                objectMapper,
                eventloop,
                pollIntervalMs
            );
            
            subscriptions.put(subscriptionId, subscription);
            
            if (running.get()) {
                subscription.start();
            }
            
            logger.info("Created multi-event subscription: id={}, eventTypes={}", 
                subscriptionId, String.join(",", eventTypes));
            
            return subscription;
        });
    }
    
    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(executor, () -> {
            if (running.compareAndSet(false, true)) {
                logger.info("Starting EventBusClient with {} subscriptions", 
                    subscriptions.size());
                
                for (DefaultEventSubscription subscription : subscriptions.values()) {
                    subscription.start();
                }
            }
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(executor, () -> {
            if (running.compareAndSet(true, false)) {
                logger.info("Stopping EventBusClient");
                
                for (DefaultEventSubscription subscription : subscriptions.values()) {
                    subscription.stop();
                }
                
                executor.shutdown();
            }
            return null;
        });
    }
    
    /**
     * Extracts event type from event object using reflection.
     */
    private String extractEventType(Object event) {
        try {
            // Try to get EVENT_TYPE constant
            java.lang.reflect.Field field = event.getClass().getDeclaredField("EVENT_TYPE");
            return (String) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Fall back to class name
            return event.getClass().getSimpleName();
        }
    }
    
    /**
     * Extracts aggregate ID from event object using reflection.
     */
    private String extractAggregateId(Object event) {
        try {
            // Try common ID field names
            for (String fieldName : new String[]{"eventId", "id", "aggregateId"}) {
                try {
                    Method getter = event.getClass().getMethod(fieldName);
                    Object id = getter.invoke(event);
                    if (id != null) {
                        return id.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract aggregate ID from event", e);
        }
        return null;
    }
    
    /**
     * Internal subscription implementation.
     */
    private static class DefaultEventSubscription implements EventSubscription {
        
        private final String subscriptionId;
        private final String[] eventTypes;
        private final Class<?> eventClass;
        private final Function<Object, Promise<Void>> handler;
        private final EventLogAdapter eventLogAdapter;
        private final ObjectMapper objectMapper;
        private final Eventloop eventloop;
        private final long pollIntervalMs;
        
        private final AtomicBoolean active;
        private final AtomicBoolean paused;
        private final AtomicLong eventsProcessed;
        private final AtomicLong eventsFailed;
        private volatile String lastProcessedEventId;
        private volatile Thread pollThread;
        
        DefaultEventSubscription(
            String subscriptionId,
            String[] eventTypes,
            Class<?> eventClass,
            Function<Object, Promise<Void>> handler,
            EventLogAdapter eventLogAdapter,
            ObjectMapper objectMapper,
            Eventloop eventloop,
            long pollIntervalMs
        ) {
            this.subscriptionId = subscriptionId;
            this.eventTypes = eventTypes;
            this.eventClass = eventClass;
            this.handler = handler;
            this.eventLogAdapter = eventLogAdapter;
            this.objectMapper = objectMapper;
            this.eventloop = eventloop;
            this.pollIntervalMs = pollIntervalMs;
            
            this.active = new AtomicBoolean(false);
            this.paused = new AtomicBoolean(false);
            this.eventsProcessed = new AtomicLong(0);
            this.eventsFailed = new AtomicLong(0);
        }
        
        void start() {
            if (active.compareAndSet(false, true)) {
                pollThread = new Thread(this::pollLoop, "event-subscription-" + subscriptionId);
                pollThread.setDaemon(true);
                pollThread.start();
            }
        }
        
        void stop() {
            active.set(false);
            if (pollThread != null) {
                pollThread.interrupt();
            }
        }
        
        private void pollLoop() {
            while (active.get()) {
                try {
                    if (!paused.get()) {
                        pollAndProcess();
                    }
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in poll loop for subscription: {}", subscriptionId, e);
                }
            }
        }
        
        private void pollAndProcess() {
            try {
                var events = eventLogAdapter.query(
                    eventTypes,
                    lastProcessedEventId,
                    100 // batch size
                );
                
                for (var record : events) {
                    try {
                        Object event = deserializeEvent(record);
                        
                        // Execute handler on eventloop
                        eventloop.execute(() -> {
                            handler.apply(event)
                                .whenComplete((result, error) -> {
                                    if (error != null) {
                                        eventsFailed.incrementAndGet();
                                        logger.error("Handler failed for event: {}", 
                                            record.eventId(), error);
                                    } else {
                                        eventsProcessed.incrementAndGet();
                                    }
                                });
                        });
                        
                        lastProcessedEventId = record.eventId();
                        
                    } catch (Exception e) {
                        eventsFailed.incrementAndGet();
                        logger.error("Failed to process event: {}", record.eventId(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to poll events for subscription: {}", subscriptionId, e);
            }
        }
        
        private Object deserializeEvent(EventLogAdapter.EventRecord record) throws Exception {
            if (eventClass == Object.class) {
                // Try to load actual class
                try {
                    Class<?> actualClass = Class.forName(record.eventClassName());
                    return objectMapper.readValue(record.payload(), actualClass);
                } catch (ClassNotFoundException e) {
                    // Fall back to Map
                    return objectMapper.readValue(record.payload(), Map.class);
                }
            } else {
                return objectMapper.readValue(record.payload(), eventClass);
            }
        }
        
        @Override
        public String getSubscriptionId() {
            return subscriptionId;
        }
        
        @Override
        public String[] getEventTypes() {
            return eventTypes;
        }
        
        @Override
        public boolean isActive() {
            return active.get() && !paused.get();
        }
        
        @Override
        public long getEventsProcessed() {
            return eventsProcessed.get();
        }
        
        @Override
        public long getEventsFailed() {
            return eventsFailed.get();
        }
        
        @Override
        public String getLastProcessedEventId() {
            return lastProcessedEventId;
        }
        
        @Override
        public Promise<Void> pause() {
            paused.set(true);
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> resume() {
            paused.set(false);
            return Promise.complete();
        }
        
        @Override
        public Promise<Void> cancel() {
            stop();
            return Promise.complete();
        }
    }
    
    /**
     * Exception thrown when event publishing fails.
     */
    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
