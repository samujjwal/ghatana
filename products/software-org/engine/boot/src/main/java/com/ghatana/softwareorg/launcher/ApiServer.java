package com.ghatana.softwareorg.launcher;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpMethod;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP API server for Software-Org.
 *
 * <p><b>Purpose</b><br>
 * Provides REST API endpoints for accessing and managing configuration entities.
 * Serves as the bridge between the Fastify backend and Java services.
 * Uses {@code libs:http-server} for HTTP functionality.
 *
 * <p><b>Endpoints</b><br>
 * - GET /health - Health check
 * - GET /metrics - Prometheus metrics
 * - GET /api/config/org - Organization configuration
 * - GET /api/personas - List personas
 * - GET /api/departments - List departments
 * - GET /api/agents - List agents
 * - GET /api/workflows - List workflows
 * - ... (see RESTRUCTURING_PLAN.md for full list)
 *
 * @doc.type class
 * @doc.purpose HTTP API server for configuration management
 * @doc.layer product
 * @doc.pattern Facade
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    private final int port;
    private final ConfigurationLoader configLoader;
    private final VirtualAppBootstrap virtualAppBootstrap;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Eventloop eventloop;
    private HttpServer httpServer;
    private boolean running = false;

    /**
     * Creates a new API server.
     *
     * @param eventloop Eventloop
     * @param port Server port
     * @param configLoader Configuration loader
     * @param virtualAppBootstrap Virtual-app bootstrap
     */
    public ApiServer(Eventloop eventloop, int port, ConfigurationLoader configLoader, VirtualAppBootstrap virtualAppBootstrap) {
        this.eventloop = eventloop;
        this.port = port;
        this.configLoader = configLoader;
        this.virtualAppBootstrap = virtualAppBootstrap;
    }

    /**
     * Starts the API server.
     * Registers routes and starts listening on the configured port.
     * Note: The eventloop must be run by the caller.
     *
     * @throws Exception if server fails to start
     */
    public void start() throws Exception {
        if (running) {
            logger.warn("API server already running");
            return;
        }

        logger.info("Starting API server on port {}...", port);

        // Create HTTP server with routes (using the eventloop)
        httpServer = HttpServerBuilder.create()
            .withEventloop(eventloop)
            .withHost("0.0.0.0")
            .withPort(port)
            
            // Health check endpoint
            .withHealthCheck("/health")
            
            // Configuration endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/config/org", this::getOrgConfig)
            .addAsyncRoute(HttpMethod.POST, "/api/config/reload", this::reloadConfig)
            
            // Persona endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/personas", this::listPersonas)
            .addAsyncRoute(HttpMethod.GET, "/api/personas/:id", this::getPersona)
            
            // Department endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/departments", this::listDepartments)
            .addAsyncRoute(HttpMethod.GET, "/api/departments/:id", this::getDepartment)
            
            // Agent endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/agents", this::listAgents)
            .addAsyncRoute(HttpMethod.GET, "/api/agents/:id", this::getAgent)
            
            // Workflow endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/workflows", this::listWorkflows)
            .addAsyncRoute(HttpMethod.GET, "/api/workflows/:id", this::getWorkflow)
            
            // Phase endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/phases", this::listPhases)
            
            // Stage endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/stages", this::listStages)
            
            // Operator endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/operators", this::listOperators)
            
            // Service endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/services", this::listServices)
            
            // Integration endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/integrations", this::listIntegrations)
            
            // Flow endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/flows", this::listFlows)
            
            // KPI endpoints
            .addAsyncRoute(HttpMethod.GET, "/api/kpis", this::listKpis)
            
            .build();

        // Start server (binds to port)
        httpServer.listen();
        
        running = true;
        logger.info("✓ API server started successfully on port {}", port);
    }

    /**
     * Stops the API server.
     */
    public void stop() {
        if (!running) {
            logger.warn("API server not running");
            return;
        }

        logger.info("Stopping API server...");

        // Stop HTTP server
        if (httpServer != null) {
            try {
                httpServer.close();
                logger.info("HTTP server closed");
            } catch (Exception e) {
                logger.error("Error closing HTTP server", e);
            }
        }
        
        executor.shutdown();
        running = false;
        logger.info("✓ API server stopped successfully");
    }
    
    // ========================================================================
    // Configuration Endpoints
    // ========================================================================
    
    private Promise<io.activej.http.HttpResponse> getOrgConfig(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                OrgConfiguration config = virtualAppBootstrap.getOrgConfig();
                String json = JsonUtils.toJson(Map.of(
                    "name", config.getName(),
                    "version", config.getVersion(),
                    "description", config.getDescription(),
                    "counts", Map.ofEntries(
                        Map.entry("personas", config.getPersonas().size()),
                        Map.entry("departments", config.getDepartments().size()),
                        Map.entry("agents", config.getAgents().size()),
                        Map.entry("workflows", config.getWorkflows().size()),
                        Map.entry("phases", config.getPhases().size()),
                        Map.entry("stages", config.getStages().size()),
                        Map.entry("operators", config.getOperators().size()),
                        Map.entry("services", config.getServices().size()),
                        Map.entry("integrations", config.getIntegrations().size()),
                        Map.entry("flows", config.getFlows().size()),
                        Map.entry("kpis", config.getKpis().size())
                    )
                ));
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                logger.error("Error getting org config", e);
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> reloadConfig(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                configLoader.reload();
                return ResponseBuilder.ok().rawJson("{\"status\":\"reloaded\"}").build();
            } catch (Exception e) {
                logger.error("Error reloading config", e);
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    // ========================================================================
    // Entity List Endpoints
    // ========================================================================
    
    private Promise<io.activej.http.HttpResponse> listPersonas(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getPersonas());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> getPersona(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String id = request.getPathParameter("id");
            try {
                var persona = virtualAppBootstrap.getOrgConfig().getPersonas().stream()
                    .filter(p -> id.equals(p.get("id")))
                    .findFirst();
                if (persona.isPresent()) {
                    String json = JsonUtils.toJson(persona.get());
                    return ResponseBuilder.ok().rawJson(json).build();
                } else {
                    return ResponseBuilder.status(404).rawJson("{\"error\":\"Persona not found\"}").build();
                }
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listDepartments(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getDepartments());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> getDepartment(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String id = request.getPathParameter("id");
            try {
                var dept = virtualAppBootstrap.getOrgConfig().getDepartments().stream()
                    .filter(d -> id.equals(d.get("id")))
                    .findFirst();
                if (dept.isPresent()) {
                    String json = JsonUtils.toJson(dept.get());
                    return ResponseBuilder.ok().rawJson(json).build();
                } else {
                    return ResponseBuilder.status(404).rawJson("{\"error\":\"Department not found\"}").build();
                }
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listAgents(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getAgents());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> getAgent(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String id = request.getPathParameter("id");
            try {
                var agent = virtualAppBootstrap.getOrgConfig().getAgents().stream()
                    .filter(a -> id.equals(a.get("id")))
                    .findFirst();
                if (agent.isPresent()) {
                    String json = JsonUtils.toJson(agent.get());
                    return ResponseBuilder.ok().rawJson(json).build();
                } else {
                    return ResponseBuilder.status(404).rawJson("{\"error\":\"Agent not found\"}").build();
                }
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listWorkflows(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getWorkflows());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> getWorkflow(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String id = request.getPathParameter("id");
            try {
                var workflow = virtualAppBootstrap.getOrgConfig().getWorkflows().stream()
                    .filter(w -> id.equals(w.get("id")))
                    .findFirst();
                if (workflow.isPresent()) {
                    String json = JsonUtils.toJson(workflow.get());
                    return ResponseBuilder.ok().rawJson(json).build();
                } else {
                    return ResponseBuilder.status(404).rawJson("{\"error\":\"Workflow not found\"}").build();
                }
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listPhases(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getPhases());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listStages(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getStages());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listOperators(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getOperators());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listServices(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getServices());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listIntegrations(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getIntegrations());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listFlows(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getFlows());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }
    
    private Promise<io.activej.http.HttpResponse> listKpis(io.activej.http.HttpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            try {
                String json = JsonUtils.toJson(virtualAppBootstrap.getOrgConfig().getKpis());
                return ResponseBuilder.ok().rawJson(json).build();
            } catch (Exception e) {
                return ResponseBuilder.status(500).rawJson("{\"error\":\"" + e.getMessage() + "\"}").build();
            }
        });
    }

    /**
     * Checks if the server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the server port.
     *
     * @return Server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the configuration loader.
     *
     * @return Configuration loader
     */
    public ConfigurationLoader getConfigLoader() {
        return configLoader;
    }

    /**
     * Gets the virtual-app bootstrap.
     *
     * @return Virtual-app bootstrap
     */
    public VirtualAppBootstrap getVirtualAppBootstrap() {
        return virtualAppBootstrap;
    }
}
