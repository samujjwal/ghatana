package com.ghatana.appplatform.template;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Reference implementation of a minimal ActiveJ HTTP kernel service.
 *
 * <p>Demonstrates the standard anatomy all kernel services must follow (TC-P0-011):
 * <ul>
 *   <li>Non-blocking ActiveJ {@link Eventloop} — never block the eventloop thread.</li>
 *   <li>Health ({@code GET /health}) and readiness ({@code GET /ready}) endpoints.</li>
 *   <li>Clean {@link #start()} / {@link #stop()} lifecycle.</li>
 *   <li>No Spring, no Reactor — ActiveJ only.</li>
 * </ul>
 *
 * <h2>Extending this template</h2>
 * <ol>
 *   <li>Replace the stub {@link #buildServlet()} with your domain routing.</li>
 *   <li>Inject your ports (event-store, audit-trail, etc.) via constructor.</li>
 *   <li>Run integration tests by extending {@code EventloopTestBase}.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Template for all AppPlatform kernel HTTP services (Phase 0 TC-P0-011)
 * @doc.layer product
 * @doc.pattern Template, Facade
 */
public final class KernelServiceTemplate {

    private static final Logger log = LoggerFactory.getLogger(KernelServiceTemplate.class);

    private static final String VERSION = "0.1.0-SNAPSHOT";

    private final Eventloop eventloop;
    private final int port;
    private final String serviceName;
    /** Optional extra readiness check supplied by the subclass, e.g. DB connectivity. */
    private final Supplier<Promise<Boolean>> readinessCheck;

    private volatile HttpServer server;
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Creates the template service.
     *
     * @param eventloop      shared ActiveJ eventloop
     * @param serviceName    display name used in health responses
     * @param port           TCP port to bind
     * @param readinessCheck async check returning {@code true} when dependencies are up
     */
    public KernelServiceTemplate(
            Eventloop eventloop,
            String serviceName,
            int port,
            Supplier<Promise<Boolean>> readinessCheck) {
        this.eventloop      = eventloop;
        this.serviceName    = serviceName;
        this.port           = port;
        this.readinessCheck = readinessCheck;
    }

    /**
     * Convenience constructor with a simple always-ready check.
     */
    public KernelServiceTemplate(Eventloop eventloop, String serviceName, int port) {
        this(eventloop, serviceName, port, () -> Promise.of(true));
    }

    /**
     * Builds the routing servlet. Override in concrete service classes to add
     * domain routes while keeping the base {@code /health} and {@code /ready} endpoints.
     *
     * @return root servlet
     */
    protected AsyncServlet buildServlet() {
        return request -> {
            String path = request.getPath();

            if ("/health".equals(path)) {
                return Promise.of(healthResponse());
            }
            if ("/ready".equals(path)) {
                return readinessCheck.get().map(ready ->
                        ready ? healthResponse() : HttpResponse.ofCode(503).withPlainText("not ready"));
            }

            return Promise.of(HttpResponse.ofCode(404).withPlainText("not found"));
        };
    }

    /**
     * Starts the HTTP server on the configured port.
     *
     * <p>Must be called from inside the eventloop thread or via
     * {@code eventloop.execute(server::start)}.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            log.warn("Service {} already started", serviceName);
            return;
        }
        server = HttpServer.builder(eventloop, buildServlet())
                .withListenPort(port)
                .build();
        server.listen();
        log.info("KernelService '{}' v{} listening on port {}", serviceName, VERSION, port);
    }

    /**
     * Stops the HTTP server and releases resources.
     */
    public void stop() {
        if (server != null) {
            server.close();
            log.info("KernelService '{}' stopped", serviceName);
        }
        started.set(false);
    }

    /** Returns the configured port (useful in tests when port = 0). */
    public int getPort() {
        return server != null ? server.getListenAddresses()
                .stream().findFirst()
                .map(addr -> ((java.net.InetSocketAddress) addr).getPort())
                .orElse(port) : port;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private HttpResponse healthResponse() {
        String body = """
                {"status":"UP","service":"%s","version":"%s"}""".formatted(serviceName, VERSION);
        return HttpResponse.ok200()
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(body.getBytes());
    }
}
