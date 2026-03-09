package com.ghatana.pipeline.registry.connector;

import com.ghatana.pipeline.registry.connector.auth.IngressAuthValidator;
import com.ghatana.pipeline.registry.connector.auth.IngressAuthValidator.AuthResult;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.ghatana.pipeline.registry.ingress.IngressConnectorRouter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP ingress connector operator.
 *
 * <p>
 * <b>Purpose</b><br>
 * Ingests events from HTTP endpoints (webhooks, REST APIs). Acts as entry point
 * for HTTP-sourced events into the processing pipeline.
 *
 * <p>
 * <b>Lifecycle</b><br>
 * GIVEN: HTTP listener configured<br>
 * WHEN: initialize() is called<br>
 * THEN: HTTP server/handler ready to receive events<br>
 *
 * <p>
 * <b>Operations</b><br>
 * - initialize(): Setup HTTP listener<br>
 * - connect(): Verify listener is active<br>
 * - disconnect(): Pause listener temporarily<br>
 * - close(): Shutdown HTTP server<br>
 * - ingest(request): Accept HTTP event payload<br>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Ingress operator in connector layer (P3-02 HTTP ingress). Converts HTTP
 * requests to Event domain objects. Validates content-type and payload. Enables
 * webhook subscriptions and REST event intake. Metrics tracked:
 * aep.connector.http_ingress.requests.received, .validation_failures,
 * .latency_ms
 *
 * @doc.type class
 * @doc.purpose HTTP event ingress connector
 * @doc.layer product
 * @doc.pattern Operator
 */
@Slf4j
public class HttpIngressConnector implements ConnectorOperator {

    /**
     * Connector lifecycle states.
     */
    private enum ConnectorState {
        CREATED, INITIALIZED, CONNECTED, PAUSED, CLOSED
    }

    private final String id;
    private final ConnectorSpec spec;
    private final MetricsCollector metricsCollector;
    private final IngressConnectorRouter router;
    private final IngressAuthValidator authValidator;
    private final AtomicReference<ConnectorState> state = new AtomicReference<>(ConnectorState.CREATED);
    private final AtomicBoolean accepting = new AtomicBoolean(false);

    /**
     * All-args constructor.
     *
     * @param id               connector identifier
     * @param spec             connector specification
     * @param metricsCollector metrics collection service
     * @param router           ingress connector router (nullable)
     */
    public HttpIngressConnector(String id, ConnectorSpec spec,
                                MetricsCollector metricsCollector,
                                IngressConnectorRouter router) {
        this(id, spec, metricsCollector, router, IngressAuthValidator.fromSpec(spec));
    }

    public HttpIngressConnector(String id, ConnectorSpec spec,
                                MetricsCollector metricsCollector,
                                IngressConnectorRouter router,
                                IngressAuthValidator authValidator) {
        this.id = id;
        this.spec = spec;
        this.metricsCollector = metricsCollector;
        this.router = router;
        this.authValidator = authValidator;
    }

    /**
     * Create HTTP ingress connector from spec.
     *
     * @param spec             the connector specification
     * @param metricsCollector metrics collection service
     * @return configured ingress connector
     */
    public static HttpIngressConnector of(
            ConnectorSpec spec,
            MetricsCollector metricsCollector) {
        return new HttpIngressConnector(spec.getId(), spec, metricsCollector, null,
                IngressAuthValidator.fromSpec(spec));
    }

    /**
     * Create HTTP ingress connector from spec with router.
     *
     * @param spec             the connector specification
     * @param metricsCollector metrics collection service
     * @param router           ingress router
     * @return configured ingress connector
     */
    public static HttpIngressConnector of(
            ConnectorSpec spec,
            MetricsCollector metricsCollector,
            IngressConnectorRouter router) {
        return new HttpIngressConnector(spec.getId(), spec, metricsCollector, router);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.HTTP_INGRESS;
    }

    /**
     * Ingest event from HTTP request.
     *
     * @param event the event to ingest
     * @return Promise that completes when event is accepted
     */
    /**
     * Initialize HTTP ingress connector.
     *
     * GIVEN: Valid HTTP path/port specified<br>
     * WHEN: initialize() is called<br>
     * THEN: HTTP listener registered and ready to accept events<br>
     *
     * @return Promise that completes when ready to accept HTTP requests
     */
    @Override
    public Promise<Void> initialize() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Initializing HTTP ingress connector. path={}, protocol={}",
                    spec.getEndpoint(),
                    spec.getProperties() != null ? spec.getProperties().getOrDefault("protocol", "unknown")
                            : "unknown");

            if (!state.compareAndSet(ConnectorState.CREATED, ConnectorState.INITIALIZED)) {
                ConnectorState current = state.get();
                log.warn("Cannot initialize connector in state: {}", current);
                return Promise.ofException(
                        new IllegalStateException("Cannot initialize connector in state: " + current));
            }

            if (router != null) {
                router.registerActiveConnector(this);
            }

            metricsCollector.incrementCounter("aep.connector.http_ingress.initialize.count",
                    "connector_id", id,
                    "status", "initialized");

            return Promise.complete();
        }
    }

    /**
     * Ingest event from HTTP request with authentication token.
     *
     * <p>
     * GIVEN: HTTP request with JSON event payload and optional auth token<br>
     * WHEN: ingest(event, authToken) is called<br>
     * THEN: Auth validated, event validated, queued, and HTTP response sent<br>
     *
     * @param event     the event parsed from HTTP request
     * @param authToken the raw Authorization header value (may be null if auth is NONE)
     * @return Promise that completes when event is queued for processing
     */
    public Promise<Void> ingest(Event event, String authToken) {
        if (event == null) {
            return Promise.ofException(new IllegalArgumentException("Event cannot be null"));
        }

        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id);
                MDC.MDCCloseable ignored2 = MDC.putCloseable("eventId", event.getId().toString())) {

            // ── Authentication gate ──────────────────────────────────
            AuthResult auth = authValidator.validate(authToken);
            if (!auth.isAuthorized()) {
                metricsCollector.incrementCounter("aep.connector.http_ingress.auth_failures",
                        "connector_id", id,
                        "reason", auth.reason());
                log.warn("Ingress auth rejected: connectorId={} reason={}", id, auth.reason());
                return Promise.ofException(
                        new IllegalAccessException("Unauthorized: " + auth.reason()));
            }

            if (!accepting.get()) {
                metricsCollector.incrementCounter("aep.connector.http_ingress.events.rejected",
                        "connector_id", id,
                        "reason", "not_accepting");
                return Promise.ofException(
                        new IllegalStateException("Connector is not accepting events (state: " + state.get() + ")"));
            }

            long startTime = System.currentTimeMillis();
            log.debug("Ingesting event from HTTP. type={}", event.getType());

            // Validate event type against connector's expected types
            String expectedType = spec.getProperties() != null
                    ? spec.getProperties().get("expected_event_type")
                    : null;
            if (expectedType != null && !expectedType.equals(event.getType())) {
                metricsCollector.incrementCounter("aep.connector.http_ingress.validation_failures",
                        "connector_id", id,
                        "expected_type", expectedType,
                        "actual_type", event.getType());
                log.warn("Event type mismatch: expected={}, actual={}", expectedType, event.getType());
                return Promise.ofException(
                        new IllegalArgumentException(
                                "Event type '" + event.getType() + "' does not match expected '" + expectedType + "'"));
            }

            // Route event through the ingress router if available
            Promise<Void> routePromise;
            if (router != null) {
                String tenant = spec.getTenantId();
                String path = spec.getEndpoint();
                routePromise = router.routeHttpRequest(path, tenant, event)
                        .map(routed -> {
                            if (!routed) {
                                log.warn("Event not routed: no matching pipeline. connectorId={}", id);
                            }
                            return (Void) null;
                        });
            } else {
                log.debug("No router configured; event accepted but not routed");
                routePromise = Promise.complete();
            }

            return routePromise.whenResult(v -> {
                long latencyMs = System.currentTimeMillis() - startTime;
                metricsCollector.incrementCounter("aep.connector.http_ingress.events.received",
                        "connector_id", id,
                        "event_type", event.getType());
                metricsCollector.recordTimer("aep.connector.http_ingress.latency_ms",
                        latencyMs,
                        "connector_id", id);
            }).whenException(ex -> {
                metricsCollector.incrementCounter("aep.connector.http_ingress.errors",
                        "connector_id", id,
                        "phase", "ingest",
                        "error_type", ex.getClass().getSimpleName());
                log.error("Failed to ingest event: {}", ex.getMessage(), ex);
            });
        }
    }

    /**
     * Connect HTTP ingress connector.
     *
     * GIVEN: Connector initialized<br>
     * WHEN: connect() is called<br>
     * THEN: HTTP listener active and accepting requests<br>
     *
     * @return Promise that completes when listener is active
     */
    @Override
    public Promise<Void> connect() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Connecting HTTP ingress listener. path={}", spec.getEndpoint());

            ConnectorState current = state.get();
            if (current != ConnectorState.INITIALIZED && current != ConnectorState.PAUSED) {
                return Promise.ofException(
                        new IllegalStateException("Cannot connect in state: " + current));
            }

            state.set(ConnectorState.CONNECTED);
            accepting.set(true);

            metricsCollector.incrementCounter("aep.connector.http_ingress.connect.count",
                    "connector_id", id,
                    "status", "connected");
            log.info("HTTP ingress connector listening. path={}", spec.getEndpoint());

            return Promise.complete();
        }
    }

    /**
     * Disconnect HTTP ingress connector.
     *
     * GIVEN: Connector connected<br>
     * WHEN: disconnect() is called<br>
     * THEN: Listener paused, no new requests accepted<br>
     *
     * @return Promise that completes when listener is paused
     */
    @Override
    public Promise<Void> disconnect() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Disconnecting HTTP ingress listener");

            if (state.get() != ConnectorState.CONNECTED) {
                log.debug("Connector not connected, skipping disconnect");
                return Promise.complete();
            }

            // Stop accepting new events first
            accepting.set(false);
            state.set(ConnectorState.PAUSED);

            metricsCollector.incrementCounter("aep.connector.http_ingress.disconnect.count",
                    "connector_id", id);
            log.info("HTTP ingress connector paused");

            return Promise.complete();
        }
    }

    /**
     * Close HTTP ingress connector.
     *
     * GIVEN: Connector connected or initialized<br>
     * WHEN: close() is called<br>
     * THEN: HTTP listener stopped and resources released<br>
     *
     * @return Promise that completes when closed
     */
    @Override
    public Promise<Void> close() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Closing HTTP ingress connector");

            ConnectorState current = state.get();
            if (current == ConnectorState.CLOSED) {
                return Promise.complete();
            }

            // Cease accepting events
            accepting.set(false);

            // Unregister from router
            if (router != null) {
                router.unregisterActiveConnector(id);
            }

            state.set(ConnectorState.CLOSED);
            metricsCollector.incrementCounter("aep.connector.http_ingress.close.count",
                    "connector_id", id);
            log.info("HTTP ingress connector closed");

            return Promise.complete();
        }
    }

    /**
     * Check HTTP ingress connector health.
     *
     * GIVEN: Connector connected<br>
     * WHEN: isHealthy() is called<br>
     * THEN: Return listener status<br>
     *
     * @return Promise containing health status (true = listener active)
     */
    @Override
    public Promise<Boolean> isHealthy() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            ConnectorState current = state.get();
            boolean healthy = current == ConnectorState.CONNECTED && accepting.get();
            if (!healthy) {
                log.debug("HTTP ingress unhealthy: state={}, accepting={}", current, accepting.get());
            }
            return Promise.of(healthy);
        }
    }
}
