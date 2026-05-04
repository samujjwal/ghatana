package com.ghatana.digitalmarketing.api;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production composition root for DMOS API server.
 *
 * <p>This class serves as the main entry point for the DMOS API server in production.
 * It validates the environment profile, wires all servlets, services, and repositories,
 * and starts the server with proper dependency injection.</p>
 *
 * <p>Environment validation:</p>
 * <ul>
 *   <li>DMOS_ENV must be set (production, staging, or development)</li>
 *   <li>Production profile requires PostgreSQL persistence (no in-memory adapters)</li>
 *   <li>Required environment variables must be set for production</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Production composition root with environment validation
 * @doc.layer product
 * @doc.pattern Composition Root
 */
public final class DmosApiServer extends Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(DmosApiServer.class);

    private static final String DMOS_ENV = "DMOS_ENV";
    private static final String PRODUCTION = "production";
    private static final String STAGING = "staging";
    private static final String DEVELOPMENT = "development";

    private final String environment;
    private final ServiceGraph serviceGraph;

    public DmosApiServer() {
        this.environment = validateEnvironment();
        this.serviceGraph = new ServiceGraph();
    }

    /**
     * Validates the deployment environment and returns the environment name.
     *
     * @return environment name
     * @throws IllegalStateException if environment is invalid
     */
    private static String validateEnvironment() {
        String env = System.getenv(DMOS_ENV);
        if (env == null || env.isBlank()) {
            LOG.warn("{} not set, defaulting to development", DMOS_ENV);
            return DEVELOPMENT;
        }

        String normalized = env.trim().toLowerCase();
        if (!normalized.equals(PRODUCTION) && !normalized.equals(STAGING) && !normalized.equals(DEVELOPMENT)) {
            throw new IllegalStateException(
                "Invalid " + DMOS_ENV + ": " + env + ". Must be one of: production, staging, development"
            );
        }

        return normalized;
    }

    /**
     * Validates production-specific requirements.
     *
     * @throws IllegalStateException if production requirements are not met
     */
    private void validateProductionRequirements() {
        if (!environment.equals(PRODUCTION)) {
            return;
        }

        // P0-5: Ensure PostgreSQL persistence is used in production
        String persistenceType = System.getenv("DMOS_PERSISTENCE_TYPE");
        if ("in-memory".equalsIgnoreCase(persistenceType)) {
            throw new IllegalStateException(
                "In-memory persistence is not allowed in production. " +
                "Set DMOS_PERSISTENCE_TYPE=postgresql"
            );
        }

        // Validate required production environment variables
        String[] requiredVars = {
            "DATABASE_URL",
            "DMOS_PII_HMAC_KEY",
        };

        for (String var : requiredVars) {
            String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                    "Required environment variable not set for production: " + var
                );
            }
        }

        LOG.info("Production requirements validated successfully");
    }

    @Override
    protected void run() throws Exception {
        LOG.info("Starting DMOS API server in {} environment", environment);

        // Validate production requirements
        validateProductionRequirements();

        // Build service graph
        buildServiceGraph();

        // Start services
        serviceGraph.startFuture().await();

        LOG.info("DMOS API server started successfully on port {}", getListenPort());
    }

    private void buildServiceGraph() {
        // TODO: Wire actual services, servlets, and repositories
        // This is a placeholder for the full composition root
        
        // Example wiring:
        // serviceGraph.add(DataSource.class, () -> createDataSource());
        // serviceGraph.add(CampaignRepository.class, () -> new PostgresCampaignRepository(...));
        // serviceGraph.add(CampaignService.class, () -> new CampaignServiceImpl(...));
        // serviceGraph.add(DmosCampaignServlet.class, () -> new DmosCampaignServlet(...));
        
        LOG.info("Service graph built (placeholder - full wiring pending)");
    }

    private int getListenPort() {
        String port = System.getenv("PORT");
        if (port != null && !port.isBlank()) {
            return Integer.parseInt(port);
        }
        return 8080; // Default port
    }

    /**
     * Main entry point for running the server.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws Exception {
        DmosApiServer server = new DmosApiServer();
        server.launch(args);
    }
}
