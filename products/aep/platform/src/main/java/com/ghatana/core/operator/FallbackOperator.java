package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Operator that provides fallback processing when primary operator fails.
 *
 * <p><b>Purpose</b><br>
 * Wraps another operator and provides alternative processing logic when the primary
 * operator fails. Enables graceful degradation and default value generation for
 * resilient system behavior.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FallbackOperator fallback = FallbackOperator.builder()
 *     .primary(externalAPIOperator)
 *     .fallback(cachedDataOperator)
 *     .build();
 *
 * // If primary fails, fallback is used
 * OperatorResult result = fallback.process(event).getResult();
 * }</pre>
 *
 * <p><b>Fallback Strategies</b><br>
 * <ul>
 *   <li><b>Alternative operator:</b> Use different operator on failure</li>
 *   <li><b>Default value:</b> Return predefined default event</li>
 *   <li><b>Function-based:</b> Generate fallback from original event</li>
 *   <li><b>Empty result:</b> Return empty result on failure</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Primary and fallback operators must be thread-safe.
 *
 * <p><b>Performance</b><br>
 * Overhead: <5μs when primary succeeds
 * Fallback: Depends on fallback strategy
 *
 * @see UnifiedOperator
 * @see RetryOperator
 * @doc.type class
 * @doc.purpose Graceful degradation with fallback processing
 * @doc.layer core
 * @doc.pattern Decorator
 */
public class FallbackOperator extends AbstractOperator {

    private static final Logger logger = LoggerFactory.getLogger(FallbackOperator.class);

    private final UnifiedOperator primary;
    private final UnifiedOperator fallbackOperator;
    private final Function<Event, Event> fallbackFunction;
    private final Event defaultEvent;
    private final boolean returnEmptyOnFailure;

    /**
     * Create fallback operator with builder.
     *
     * @param builder Builder with configuration
     */
    private FallbackOperator(Builder builder) {
        super(
            OperatorId.of("ghatana", "error-handling", "fallback", "1.0.0"),
            OperatorType.STREAM,
            "Fallback Operator",
            "Provides fallback processing on failure",
            List.of("fallback", "resilience", "error-handling"),
            null
        );
        this.primary = Objects.requireNonNull(builder.primary, "Primary operator required");
        this.fallbackOperator = builder.fallbackOperator;
        this.fallbackFunction = builder.fallbackFunction;
        this.defaultEvent = builder.defaultEvent;
        this.returnEmptyOnFailure = builder.returnEmptyOnFailure;

        // Validate: exactly one fallback strategy
        int strategies = 0;
        if (fallbackOperator != null) strategies++;
        if (fallbackFunction != null) strategies++;
        if (defaultEvent != null) strategies++;
        if (returnEmptyOnFailure) strategies++;

        if (strategies == 0) {
            throw new IllegalArgumentException("No fallback strategy configured");
        }
        if (strategies > 1) {
            throw new IllegalArgumentException("Multiple fallback strategies configured - use only one");
        }
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        logger.debug("Processing with primary operator");

        return primary.process(event)
            .then(result -> {
                if (result.isSuccess()) {
                    // Primary succeeded
                    logger.debug("Primary operator succeeded");
                    return Promise.of(result);
                }

                // Primary failed: use fallback
                logger.warn("Primary operator failed: {}, using fallback",
                           result.getErrorMessage());
                return executeFallback(event, result);
            })
            .whenException(ex -> {
                logger.error("Primary operator threw exception, using fallback", ex);
            });
    }

    /**
     * Execute fallback strategy.
     *
     * @param originalEvent Original input event
     * @param primaryResult Result from primary operator
     * @return Promise of fallback result
     */
    private Promise<OperatorResult> executeFallback(Event originalEvent,
                                                     OperatorResult primaryResult) {
        if (fallbackOperator != null) {
            // Strategy 1: Alternative operator
            logger.debug("Using fallback operator");
            return fallbackOperator.process(originalEvent)
                .whenResult(r -> logger.info("Fallback operator completed: success={}",
                                            r.isSuccess()))
                .whenException(ex -> logger.error("Fallback operator also failed", ex));
        }

        if (fallbackFunction != null) {
            // Strategy 2: Function-based fallback
            logger.debug("Using fallback function");
            try {
                Event fallbackEvent = fallbackFunction.apply(originalEvent);
                logger.info("Fallback function produced event");
                return Promise.of(OperatorResult.of(fallbackEvent));
            } catch (Exception ex) {
                logger.error("Fallback function failed", ex);
                return Promise.of(OperatorResult.failed("Fallback function failed: " + ex.getMessage()));
            }
        }

        if (defaultEvent != null) {
            // Strategy 3: Default event
            logger.debug("Using default event");
            return Promise.of(OperatorResult.of(defaultEvent));
        }

        if (returnEmptyOnFailure) {
            // Strategy 4: Empty result
            logger.debug("Returning empty result");
            return Promise.of(OperatorResult.empty());
        }

        // Should never reach here due to validation in constructor
        return Promise.of(primaryResult);
    }

    @Override
    protected Promise<Void> doInitialize(OperatorConfig config) {
        logger.debug("Initializing fallback operator");
        Promise<Void> primaryInit = primary.initialize(config);

        if (fallbackOperator != null) {
            Promise<Void> fallbackInit = fallbackOperator.initialize(config);
            return Promises.toList(primaryInit, fallbackInit).map(list -> null);
        }

        return primaryInit;
    }

    @Override
    protected Promise<Void> doStart() {
        logger.info("Starting fallback operator");
        Promise<Void> primaryStart = primary.start();

        if (fallbackOperator != null) {
            Promise<Void> fallbackStart = fallbackOperator.start();
            return Promises.toList(primaryStart, fallbackStart).map(list -> null);
        }

        return primaryStart;
    }

    @Override
    protected Promise<Void> doStop() {
        logger.info("Stopping fallback operator");
        Promise<Void> primaryStop = primary.stop();

        if (fallbackOperator != null) {
            Promise<Void> fallbackStop = fallbackOperator.stop();
            return Promises.toList(primaryStop, fallbackStop).map(list -> null);
        }

        return primaryStop;
    }

    @Override
    public boolean isHealthy() {
        // Healthy if primary is healthy (fallback doesn't affect health)
        return primary.isHealthy();
    }

    @Override
    public boolean isStateful() {
        return primary.isStateful() ||
               (fallbackOperator != null && fallbackOperator.isStateful());
    }

    @Override
    public Event toEvent() {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("type", "operator.fallback");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());

        var config = new java.util.HashMap<String, Object>();
        config.put("hasFallbackOperator", fallbackOperator != null);
        config.put("hasFallbackFunction", fallbackFunction != null);
        config.put("hasDefaultEvent", defaultEvent != null);
        config.put("returnEmptyOnFailure", returnEmptyOnFailure);
        payload.put("config", config);

        payload.put("capabilities", java.util.List.of("error.fallback", "resilience"));

        var headers = new java.util.HashMap<String, String>();
        headers.put("operatorId", getId().toString());
        headers.put("tenantId", getId().getNamespace());

        return com.ghatana.platform.domain.domain.event.GEvent.builder()
                .type("operator.registered")
                .headers(headers)
                .payload(payload)
                .time(com.ghatana.platform.domain.domain.event.EventTime.now())
                .build();
    }

    /**
     * Builder for FallbackOperator.
     */
    public static class Builder {
        private UnifiedOperator primary;
        private UnifiedOperator fallbackOperator;
        private Function<Event, Event> fallbackFunction;
        private Event defaultEvent;
        private boolean returnEmptyOnFailure;

        /**
         * Set primary operator.
         *
         * @param primary Primary operator to execute first
         * @return This builder
         */
        public Builder primary(UnifiedOperator primary) {
            this.primary = primary;
            return this;
        }

        /**
         * Set fallback operator.
         *
         * <p>If primary fails, fallback operator processes original event.
         *
         * @param fallback Fallback operator
         * @return This builder
         */
        public Builder fallback(UnifiedOperator fallback) {
            this.fallbackOperator = fallback;
            return this;
        }

        /**
         * Set fallback function.
         *
         * <p>If primary fails, function generates fallback event from original.
         *
         * @param fallbackFunction Function to generate fallback event
         * @return This builder
         */
        public Builder fallbackFunction(Function<Event, Event> fallbackFunction) {
            this.fallbackFunction = fallbackFunction;
            return this;
        }

        /**
         * Set default event.
         *
         * <p>If primary fails, return this default event.
         *
         * @param defaultEvent Default event to return
         * @return This builder
         */
        public Builder defaultEvent(Event defaultEvent) {
            this.defaultEvent = defaultEvent;
            return this;
        }

        /**
         * Return empty result on failure.
         *
         * <p>If primary fails, return empty result (no events).
         *
         * @return This builder
         */
        public Builder returnEmptyOnFailure() {
            this.returnEmptyOnFailure = true;
            return this;
        }

        /**
         * Build FallbackOperator.
         *
         * @return New FallbackOperator
         * @throws IllegalArgumentException if configuration invalid
         */
        public FallbackOperator build() {
            return new FallbackOperator(this);
        }
    }

    /**
     * Create builder for fallback operator.
     *
     * @return New builder
     */
    public static Builder builder() {
        return new Builder();
    }
}

