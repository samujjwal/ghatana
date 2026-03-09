package com.ghatana.servicemanager;

import com.ghatana.servicemanager.config.ServiceManagerConfig;
import com.ghatana.servicemanager.service.ServiceManager;
import com.ghatana.servicemanager.service.ServiceManagerImpl;

import com.sun.net.httpserver.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service Manager Application.
 * 
 * Orchestrates the startup and management of all AEP services
 * based on configuration. Provides a single entrypoint for
 * running multiple services.
 * 
 * @doc.type class
 * @doc.purpose Service orchestration and management
 * @doc.layer orchestration
 * @doc.pattern Application
 */
public class ServiceManagerApplication {

    private ServiceManagerApplication() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServiceManagerApplication.class);

    public static void main(String[] args) throws Exception {
        LOG.info("Starting AEP Service Manager");

        // Create service manager module
        Module mainModule = ModuleBuilder.create()
                .install(new ServiceManagerConfig())
                .build();

        // Create injector and get service manager
        Injector injector = Injector.of(mainModule);
        ServiceManager serviceManager = injector.getInstance(ServiceManager.class);

        // Start health/readiness/metrics HTTP server
        HttpServer healthServer = startHealthServer(serviceManager);

        // Register shutdown hook
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Shutting down all AEP services");
                serviceManager.shutdownAll();
                healthServer.stop(2);
                shutdownLatch.countDown();
            } catch (Exception e) {
                LOG.error("Error during shutdown", e);
            }
        }));

        try {
            // Start enabled services
            serviceManager.startEnabledServices();
            
            LOG.info("All enabled services started successfully");
            LOG.info("Services running: {}", serviceManager.getRunningServices());
            
            // Wait for shutdown signal
            shutdownLatch.await();
            
        } catch (Exception e) {
            LOG.error("Error starting services", e);
            System.exit(1);
        }
    }

    /**
     * Starts a lightweight HTTP server exposing health, readiness, and metrics endpoints.
     * Port is configurable via {@code AEP_HEALTH_PORT} (default: 8090).
     */
    private static HttpServer startHealthServer(ServiceManager serviceManager) throws IOException {
        int port = resolveHealthPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newSingleThreadExecutor(
                r -> { Thread t = new Thread(r, "aep-health"); t.setDaemon(true); return t; }));

        // GET /health — liveness
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // GET /ready — readiness (true when at least one service is running)
        server.createContext("/ready", exchange -> {
            boolean ready = !serviceManager.getRunningServices().isEmpty();
            int code = ready ? 200 : 503;
            byte[] body = (ready
                    ? "{\"status\":\"READY\",\"running\":" + serviceManager.getRunningServices().size() + "}"
                    : "{\"status\":\"NOT_READY\",\"running\":0}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        // GET /metrics — Prometheus text format (basic JVM + service counts)
        server.createContext("/metrics", exchange -> {
            int running = serviceManager.getRunningServices().size();
            int total = serviceManager.getAllServices().size();
            String metrics = String.format(
                    "# HELP aep_services_running Number of currently running AEP services%n"
                    + "# TYPE aep_services_running gauge%n"
                    + "aep_services_running %d%n"
                    + "# HELP aep_services_total Total configured AEP services%n"
                    + "# TYPE aep_services_total gauge%n"
                    + "aep_services_total %d%n"
                    + "# HELP jvm_memory_used_bytes JVM used heap memory%n"
                    + "# TYPE jvm_memory_used_bytes gauge%n"
                    + "jvm_memory_used_bytes %d%n",
                    running, total,
                    Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            byte[] body = metrics.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.start();
        LOG.info("AEP health server started on port {} (GET /health, /ready, /metrics)", port);
        return server;
    }

    private static int resolveHealthPort() {
        String envPort = System.getenv("AEP_HEALTH_PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.strip());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 8090;
    }
}
