package com.ghatana.products.multi.agent.orchestrator;

import com.ghatana.products.multi.agent.orchestrator.config.OrchestratorAppConfig;
import com.ghatana.products.multi.agent.orchestrator.http.HealthServlet;
import io.activej.http.AsyncServlet;
import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.inject.annotation.Provides;
import lombok.extern.slf4j.Slf4j;

/**
 * Main launcher for the orchestrator application.
 * 
 * <p>Uses ActiveJ Launcher, Inject, Promise, and HTTP modules to create
 * a fully-featured HTTP server with dependency injection.
 * 
 * <p>Usage:
 * <pre>{@code
 * public static void main(String[] args) throws Exception {
 *     OrchestratorLauncher launcher = new OrchestratorLauncher();
 *     launcher.launch(args);
 * }
 * }</pre>
 * 
 * @author Platform Team
 * @since 1.0.0
 */
@Slf4j
public class OrchestratorLauncher extends HttpServerLauncher {
    
    public static void main(String[] args) throws Exception {
        OrchestratorLauncher launcher = new OrchestratorLauncher();
        launcher.launch(args);
    }
    
    @Override
    protected Module getBusinessLogicModule() {
        return new OrchestratorModule();
    }

    private static final class OrchestratorModule extends AbstractModule {

        @Override
        protected void configure() {
            // no explicit bindings here; providers supply dependencies
        }

        @Provides
        OrchestratorAppConfig orchestratorConfig() {
            return new OrchestratorAppConfig();
        }

        @Provides
        HealthServlet healthServlet(OrchestratorAppConfig config) {
            return new HealthServlet(config);
        }

        @Provides
        AsyncServlet rootServlet(HealthServlet healthServlet) {
            return healthServlet;
        }
    }
}
