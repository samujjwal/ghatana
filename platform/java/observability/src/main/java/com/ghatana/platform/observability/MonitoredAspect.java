package com.ghatana.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AspectJ aspect for automatic metrics collection of {@code @Monitored} annotated methods.
 *
 * <p>MonitoredAspect intercepts method calls annotated with {@code @Monitored} and records
 * metrics such as execution time, success/failure counts, and throughput using Micrometer.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic timer recording for method execution time</li>
 *   <li>Counter recording for success/failure events</li>
 *   <li>Custom metric names (default: method name)</li>
 *   <li>Method parameter capture (opt-in via captureParameters=true)</li>
 *   <li>Exception recording with error types</li>
 *   <li>Custom tags support</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Monitored(value = "process-order", description = "Order processing metrics")
 * public Order processOrder(String orderId) {
 *     return orderService.process(orderId);
 * }
 * }</pre>
 *
 * <p><b>Metrics Emitted:</b></p>
 * <ul>
 *   <li>timer.method.{name} - Execution time histogram</li>
 *   <li>counter.method.{name}.success - Success counter</li>
 *   <li>counter.method.{name}.error - Error counter</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via ConcurrentHashMap for metric caching.</p>
 *
 * @see Monitored for annotation documentation
 *
 * @doc.type class
 * @doc.purpose Metrics collection aspect for @Monitored methods
 * @doc.layer core
 * @doc.pattern Aspect-Oriented Programming pattern, Decorator pattern, Interceptor pattern
 */
@Aspect
public class MonitoredAspect {

    private static final Logger LOG = LoggerFactory.getLogger(MonitoredAspect.class);

    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> successCounterCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounterCache = new ConcurrentHashMap<>();

    /**
     * Creates a new MonitoredAspect with the specified meter registry.
     *
     * @param meterRegistry The Micrometer meter registry
     */
    public MonitoredAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Intercepts method calls annotated with @Monitored and records metrics.
     *
     * @param joinPoint The join point
     * @return The result of the method call
     * @throws Throwable If an error occurs
     */
    @Around("@annotation(com.ghatana.platform.observability.Monitored) || @within(com.ghatana.platform.observability.Monitored)")
    public Object monitorMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        Monitored monitored = method.getAnnotation(Monitored.class);
        if (monitored == null) {
            monitored = method.getDeclaringClass().getAnnotation(Monitored.class);
        }

        if (monitored == null) {
            return joinPoint.proceed();
        }

        String metricName = monitored.value().isEmpty() ? method.getName() : monitored.value();
        String fullMetricName = "method." + metricName;

        // Build tags
        Tags tags = buildTags(monitored, method);

        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();

            // Record success counter
            if (monitored.recordCounters()) {
                getSuccessCounter(fullMetricName, tags).increment();
            }

            // Record execution time
            if (monitored.recordTiming()) {
                long duration = System.nanoTime() - startTime;
                getTimer(fullMetricName, tags).record(Duration.ofNanos(duration));
            }

            return result;
        } catch (Throwable t) {
            // Record error counter
            if (monitored.recordCounters()) {
                getErrorCounter(fullMetricName, tags).increment();
            }

            // Record execution time even on error
            if (monitored.recordTiming()) {
                long duration = System.nanoTime() - startTime;
                getTimer(fullMetricName, tags).record(Duration.ofNanos(duration));
            }

            LOG.debug("Monitored method {} threw exception: {}", metricName, t.getMessage());
            throw t;
        }
    }

    /**
     * Builds Micrometer tags from the @Monitored annotation and method metadata.
     *
     * @param monitored The @Monitored annotation
     * @param method The method being monitored
     * @return The tags
     */
    private Tags buildTags(Monitored monitored, Method method) {
        Tags tags = Tags.of("method", method.getName(), "class", method.getDeclaringClass().getSimpleName());

        // Add custom tags from annotation
        for (String tag : monitored.tags()) {
            String[] parts = tag.split(":", 2);
            if (parts.length == 2) {
                tags = tags.and(parts[0], parts[1]);
            }
        }

        return tags;
    }

    /**
     * Gets or creates a timer for the specified metric name and tags.
     *
     * @param metricName The metric name
     * @param tags The tags
     * @return The timer
     */
    private Timer getTimer(String metricName, Tags tags) {
        String cacheKey = metricName + tags.toString();
        return timerCache.computeIfAbsent(cacheKey, k -> 
            Timer.builder(metricName)
                    .description("Execution time for " + metricName)
                    .tags(tags)
                    .register(meterRegistry)
        );
    }

    /**
     * Gets or creates a success counter for the specified metric name and tags.
     *
     * @param metricName The metric name
     * @param tags The tags
     * @return The counter
     */
    private Counter getSuccessCounter(String metricName, Tags tags) {
        String cacheKey = metricName + ".success" + tags.toString();
        return successCounterCache.computeIfAbsent(cacheKey, k -> 
            Counter.builder(metricName + ".success")
                    .description("Success count for " + metricName)
                    .tags(tags)
                    .register(meterRegistry)
        );
    }

    /**
     * Gets or creates an error counter for the specified metric name and tags.
     *
     * @param metricName The metric name
     * @param tags The tags
     * @return The counter
     */
    private Counter getErrorCounter(String metricName, Tags tags) {
        String cacheKey = metricName + ".error" + tags.toString();
        return errorCounterCache.computeIfAbsent(cacheKey, k -> 
            Counter.builder(metricName + ".error")
                    .description("Error count for " + metricName)
                    .tags(tags)
                    .register(meterRegistry)
        );
    }
}
