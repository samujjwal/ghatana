package com.ghatana.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared metrics collection facade for cross-product metric standardization (CP-001.4).
 *
 * <p>Wraps Micrometer's {@link MeterRegistry} with convenience factory methods that
 * enforce consistent naming conventions, required tags, and observability best practices
 * across all Ghatana products.
 *
 * <h3>Naming conventions enforced</h3>
 * <ul>
 *   <li>All metric names use {@code snake_case} with dot separators.</li>
 *   <li>All metrics include a {@code product} tag to disambiguate across products.</li>
 *   <li>All metrics include a {@code tenant_id} tag where tenant-scoped.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * SharedMetricsRegistry metrics = new SharedMetricsRegistry(meterRegistry, "aep");
 *
 * // Counter
 * metrics.counter("events.processed", "tenant_id", tenantId).increment();
 *
 * // Timer
 * metrics.timer("pipeline.stage.latency", "stage", "enrichment")
 *     .record(Duration.ofMillis(12));
 *
 * // Gauge
 * metrics.gauge("queue.depth", () -> queue.size(), "tenant_id", tenantId);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Shared metrics collection facade for cross-product metric standardization
 * @doc.layer observability
 * @doc.pattern Facade
 */
public final class SharedMetricsRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SharedMetricsRegistry.class);

    private final MeterRegistry registry;
    private final String product;
    private final Set<String> registeredMetrics;

    /**
     * Creates a shared metrics registry for the given product.
     *
     * @param registry the Micrometer meter registry to delegate to
     * @param product  the product name tag value (e.g. {@code "aep"}, {@code "audio-video"})
     * @throws NullPointerException if registry or product is null
     */
    public SharedMetricsRegistry(MeterRegistry registry, String product) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(product, "product must not be null");
        this.registry = registry;
        this.product = product;
        this.registeredMetrics = ConcurrentHashMap.newKeySet();
    }

    // ─── counter ──────────────────────────────────────────────────────────────

    /**
     * Gets or creates a named counter with the product tag automatically applied.
     *
     * @param name       metric name (dot.separated.snake_case)
     * @param tags       additional key-value pairs to apply as tags (must be even length)
     * @return the Micrometer {@link Counter}
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if tags array has odd length
     */
    public Counter counter(String name, String... tags) {
        Objects.requireNonNull(name, "name must not be null");
        validateTagPairs(tags);
        registeredMetrics.add(name);
        return Counter.builder(name)
                .tag("product", product)
                .tags(tags)
                .description("Counter: " + name)
                .register(registry);
    }

    /**
     * Increments a named counter by 1.
     *
     * @param name metric name
     * @param tags additional key-value tag pairs
     * @throws NullPointerException if name is null
     */
    public void increment(String name, String... tags) {
        counter(name, tags).increment();
        LOG.trace("[metrics] counter {} +1 product={}", name, product);
    }

    /**
     * Increments a named counter by the given amount.
     *
     * @param name   metric name
     * @param amount the amount to increment by
     * @param tags   additional key-value tag pairs
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if amount is negative
     */
    public void increment(String name, double amount, String... tags) {
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative: " + amount);
        counter(name, tags).increment(amount);
        LOG.trace("[metrics] counter {} +{} product={}", name, amount, product);
    }

    // ─── timer ────────────────────────────────────────────────────────────────

    /**
     * Gets or creates a named timer with the product tag automatically applied.
     *
     * @param name metric name
     * @param tags additional key-value tag pairs
     * @return the Micrometer {@link Timer}
     * @throws NullPointerException if name is null
     */
    public Timer timer(String name, String... tags) {
        Objects.requireNonNull(name, "name must not be null");
        validateTagPairs(tags);
        registeredMetrics.add(name);
        return Timer.builder(name)
                .tag("product", product)
                .tags(tags)
                .description("Timer: " + name)
                .register(registry);
    }

    /**
     * Records a duration against a named timer.
     *
     * @param name     metric name
     * @param duration the duration to record
     * @param tags     additional key-value tag pairs
     * @throws NullPointerException if name or duration is null
     */
    public void record(String name, Duration duration, String... tags) {
        Objects.requireNonNull(duration, "duration must not be null");
        timer(name, tags).record(duration);
        LOG.trace("[metrics] timer {} {}ms product={}", name, duration.toMillis(), product);
    }

    // ─── gauge ────────────────────────────────────────────────────────────────

    /**
     * Registers a gauge backed by a supplier.
     *
     * @param name     metric name
     * @param supplier value supplier (called at scrape time)
     * @param tags     additional key-value tag pairs
     * @throws NullPointerException if name or supplier is null
     */
    public void gauge(String name, Supplier<Number> supplier, String... tags) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        validateTagPairs(tags);
        registeredMetrics.add(name);
        Gauge.builder(name, supplier, n -> n.get().doubleValue())
                .tag("product", product)
                .tags(tags)
                .description("Gauge: " + name)
                .register(registry);
        LOG.debug("[metrics] gauge {} registered product={}", name, product);
    }

    // ─── query API ────────────────────────────────────────────────────────────

    /**
     * @return the product name this registry is bound to
     */
    public String product() {
        return product;
    }

    /**
     * @return an unmodifiable snapshot of all metric names registered through this facade
     */
    public Set<String> registeredMetrics() {
        return Set.copyOf(registeredMetrics);
    }

    /**
     * @return the underlying Micrometer {@link MeterRegistry}
     */
    public MeterRegistry meterRegistry() {
        return registry;
    }

    // ─── internal ─────────────────────────────────────────────────────────────

    private static void validateTagPairs(String[] tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "tags must be provided as key-value pairs (even length), got: " + tags.length);
        }
    }
}
