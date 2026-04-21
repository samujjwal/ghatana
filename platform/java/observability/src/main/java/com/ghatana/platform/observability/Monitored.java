package com.ghatana.platform.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic method metrics collection via AOP.
 *
 * <p>When applied to a method or class, the MonitoredAspect intercepts calls
 * and records metrics such as execution time, success/failure counts, and
 * throughput using Micrometer.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Monitored(value = "process-order", description = "Order processing metrics")
 * public Order processOrder(String orderId) {
 *     return orderService.process(orderId);
 * }
 * }</pre>
 *
 * @doc.type annotation
 * @doc.purpose Automatic method metrics collection via AOP
 * @doc.layer core
 * @doc.pattern Decorator
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Monitored {

    /**
     * Custom metric name (default: method name).
     *
     * @return The metric name
     */
    String value() default "";

    /**
     * Description of what is being monitored.
     *
     * @return The description
     */
    String description() default "";

    /**
     * Whether to record execution time as a timer.
     *
     * @return true to record timing, false otherwise
     */
    boolean recordTiming() default true;

    /**
     * Whether to record success/failure as a counter.
     *
     * @return true to record counts, false otherwise
     */
    boolean recordCounters() default true;

    /**
     * Whether to capture method parameters in metrics (simple types only).
     *
     * @return true to capture parameters, false otherwise
     */
    boolean captureParameters() default false;

    /**
     * Custom tags to add to all metrics.
     *
     * @return Array of tag key-value pairs (format: "key:value")
     */
    String[] tags() default {};
}
