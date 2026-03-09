package com.ghatana.refactorer.orchestrator.report;

import com.ghatana.core.activej.eventloop.EventloopManager;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lightweight HTTP server that serves Polyfix reports via the shared HttpServerBuilder
 * abstraction. Exposes JSON data under <code>/api/report</code>, a health endpoint, and
 * serves the SPA bundle from the configured static directory.
 
 * @doc.type class
 * @doc.purpose Handles report server operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class ReportServer implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(ReportServer.class);

    private static final String HEALTH_ENDPOINT = "/api/health";
    private static final String REPORT_ENDPOINT = "/api/report";
    private static final String DEFAULT_HEALTH_PATH = "/health";
    private static final String INDEX_FILE = "index.html";

    private final Eventloop eventloop;
    private final ExecutorService executor;
    private final Path reportDataPath;
    private final Path staticFilesDir;
    private final HttpServer httpServer;
    private final int configuredPort;
    private final boolean randomPortRequested;

    private volatile int boundPort;
    private volatile boolean running;
    private volatile boolean started;

    public ReportServer(ReportServerConfig config) throws IOException {
        Objects.requireNonNull(config, "config");

        this.reportDataPath = Objects.requireNonNull(config.getReportDataPath(), "reportDataPath");
        this.staticFilesDir = Objects.requireNonNull(config.getStaticFilesDir(), "staticFilesDir");
        this.randomPortRequested = config.isUseRandomPort() || config.getPort() <= 0;
        this.configuredPort = randomPortRequested ? allocateEphemeralPort() : config.getPort();

        ensureStaticDirectory(staticFilesDir);
        ensurePortAvailable(configuredPort, randomPortRequested);

        this.executor = Executors.newFixedThreadPool(8);
        this.eventloop = EventloopManager.create("report-server");
        this.httpServer = buildServer();
        this.boundPort = configuredPort;
    }

    private HttpServer buildServer() {
        RoutingServlet apiServlet = new RoutingServlet();
        apiServlet.addAsyncRoute(HttpMethod.GET, REPORT_ENDPOINT, this::handleReportRequest);
        apiServlet.addRoute(
                HttpMethod.GET,
                HEALTH_ENDPOINT,
                request ->
                        ResponseBuilder.ok()
                                .json(Map.of("status", "UP"))
                                .noCache()
                                .cors("*")
                                .build());

        return HttpServerBuilder.create()
                .withEventloop(eventloop)
                .withPort(configuredPort)
                .withHealthCheck(DEFAULT_HEALTH_PATH)
                .withoutMetrics()
                .addServlet(apiServlet)
                .addFilter(
                        (request, next) -> {
                            String path = request.getPath();
                            if (path != null && path.startsWith("/api/")) {
                                return next.serve(request);
                            }
                            return serveStaticContent(path == null ? "/" : path);
                        })
                .build();
    }

    private Promise<HttpResponse> handleReportRequest(HttpRequest request) {
        return Promise.ofBlocking(
                executor,
                () -> {
                    if (!Files.exists(reportDataPath)) {
                        return ResponseBuilder.notFound()
                                .text("Report data not found")
                                .noCache()
                                .cors("*")
                                .build();
                    }

                    try {
                        byte[] payload = Files.readAllBytes(reportDataPath);
                        return ResponseBuilder.ok()
                                .bytes(payload, "application/json")
                                .noCache()
                                .cors("*")
                                .build();
                    } catch (IOException e) {
                        log.error("Error reading report data file {}", reportDataPath, e);
                        return ResponseBuilder.internalServerError()
                                .text("Failed to load report data")
                                .noCache()
                                .cors("*")
                                .build();
                    }
                });
    }

    private Promise<HttpResponse> serveStaticContent(String rawPath) {
        return Promise.ofBlocking(
                executor,
                () -> {
                    String resolved = normalizePath(rawPath);
                    Path filePath = staticFilesDir.resolve(resolved).normalize();

                    if (!filePath.startsWith(staticFilesDir)) {
                        return ResponseBuilder.forbidden()
                                .text("Forbidden")
                                .noCache()
                                .cors("*")
                                .build();
                    }

                    Path resource = filePath;
                    if (!Files.exists(resource) || Files.isDirectory(resource)) {
                        resource = staticFilesDir.resolve(INDEX_FILE);
                    }

                    if (!Files.exists(resource) || Files.isDirectory(resource)) {
                        return ResponseBuilder.notFound()
                                .text("Static resource not found")
                                .noCache()
                                .cors("*")
                                .build();
                    }

                    try {
                        byte[] body = Files.readAllBytes(resource);
                        return ResponseBuilder.ok()
                                .bytes(body, detectContentType(resource))
                                .noCache()
                                .cors("*")
                                .build();
                    } catch (IOException e) {
                        log.error("Error serving static asset {}", resource, e);
                        return ResponseBuilder.internalServerError()
                                .text("Failed to load static asset")
                                .noCache()
                                .cors("*")
                                .build();
                    }
                });
    }

    public void start() throws IOException {
        if (running) {
            return;
        }

        try {
            httpServer.listen();
            if (!httpServer.getListenAddresses().isEmpty()) {
                InetSocketAddress address = httpServer.getListenAddresses().get(0);
                boundPort = address.getPort();
            }
            started = true;
            running = true;

            log.info("Report server started on port {}", boundPort);
            log.info("Open http://localhost:{}/ to view the report", boundPort);

            eventloop.run();
        } catch (Exception ex) {
            running = false;
            throw ex instanceof IOException
                    ? (IOException) ex
                    : new IOException("Failed to start report server", ex);
        } finally {
            executor.shutdownNow();
            EventloopManager.clearCurrentEventloop();
            if (started) {
                started = false;
                running = false;
                log.info("Report server stopped");
            }
        }
    }

    @Override
    public void close() {
        if (!running) {
            return;
        }

        running = false;
        log.info("Stopping report server");
        eventloop.execute(httpServer::close);
        eventloop.execute(eventloop::breakEventloop);
    }

    public int getPort() {
        return boundPort;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return INDEX_FILE;
        }
        String sanitized = path.startsWith("/") ? path.substring(1) : path;
        return sanitized.isBlank() ? INDEX_FILE : sanitized;
    }

    private String detectContentType(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".woff")) return "font/woff";
        if (name.endsWith(".ttf")) return "font/ttf";
        if (name.endsWith(".eot")) return "application/vnd.ms-fontobject";
        return "application/octet-stream";
    }

    private void ensureStaticDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Static files directory not found: " + directory);
        }
    }

    private void ensurePortAvailable(int port, boolean random) throws IOException {
        if (random) {
            return;
        }

        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(port));
        } catch (IOException ex) {
            throw new IOException("Port " + port + " is not available", ex);
        }
    }

    private int allocateEphemeralPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /**
 * Configuration for the ReportServer. */
    public static final class ReportServerConfig {
        private final int port;
        private final Path reportDataPath;
        private final Path staticFilesDir;
        private final boolean useRandomPort;

        private ReportServerConfig(Builder builder) {
            this.port = builder.port;
            this.reportDataPath =
                    Objects.requireNonNull(builder.reportDataPath, "reportDataPath must be set");
            this.staticFilesDir =
                    Objects.requireNonNull(builder.staticFilesDir, "staticFilesDir must be set");
            this.useRandomPort = builder.useRandomPort;
        }

        public int getPort() {
            return port;
        }

        public Path getReportDataPath() {
            return reportDataPath;
        }

        public Path getStaticFilesDir() {
            return staticFilesDir;
        }

        public boolean isUseRandomPort() {
            return useRandomPort;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private int port = 8080;
            private boolean useRandomPort;
            private Path reportDataPath;
            private Path staticFilesDir;

            private Builder() {}

            public Builder port(int port) {
                this.port = port;
                this.useRandomPort = false;
                return this;
            }

            public Builder useRandomPort() {
                this.useRandomPort = true;
                return this;
            }

            public Builder reportDataPath(Path reportDataPath) {
                this.reportDataPath = reportDataPath;
                return this;
            }

            public Builder staticFilesDir(Path staticFilesDir) {
                this.staticFilesDir = staticFilesDir;
                return this;
            }

            public ReportServerConfig build() {
                return new ReportServerConfig(this);
            }
        }
    }
}
