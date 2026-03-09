package com.ghatana.requirements.api;

import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.requirements.api.config.RequirementsConfig;
import com.ghatana.requirements.api.http.RequirementsHttpServer;
import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Main entry point for the AI Requirements Tool application.
 *
 * <p><b>Purpose:</b> Launches the complete requirements management system with:
 * - HTTP API server (REST + GraphQL)
 * - Authentication and authorization
 * - Workspace and project management
 * - AI-powered suggestion generation
 *
 * <p><b>Architecture:</b> Uses the unified launcher pattern for consistency
 * across all platform services.
 *
 * <p><b>Startup Sequence:</b>
 * 1. Load configuration (environment, credentials)
 * 2. Initialize dependency injection
 * 3. Setup services (database, AI models, cache)
 * 4. Start HTTP server
 * 5. Begin accepting requests
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   java -jar ai-requirements.jar
 * }</pre>
 *
 * <p><b>Environment Variables:</b>
 * <ul>
 *   <li>{@code DB_URL} - PostgreSQL connection URL
 *   <li>{@code DB_USER} - Database user
 *   <li>{@code DB_PASSWORD} - Database password
 *   <li>{@code OPENAI_API_KEY} - OpenAI API key
 *   <li>{@code JWT_SECRET} - JWT signing secret
 *   <li>{@code SERVER_PORT} - HTTP server port (default: 8080)
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Main application entry point
 * @doc.layer product
 * @doc.pattern Launcher
 * @since 1.0.0
 */
public final class RequirementsApplication extends UnifiedApplicationLauncher {
  private static final Logger logger = LoggerFactory.getLogger(RequirementsApplication.class);

  /**
   * Main entry point.
   *
   * @param args command-line arguments
   * @throws Exception if startup fails
   */
  public static void main(String[] args) throws Exception {
    new RequirementsApplication().launch(args);
  }

  @Override
  protected String getServiceName() {
    return "ai-requirements";
  }

  @Override
  protected void setupService(ModuleBuilder builder) {
    logger.info("Setting up AI Requirements service modules");

    // Configuration
    builder.bind(RequirementsConfig.class).to(RequirementsConfig::new);

    // Eventloop
    builder.bind(io.activej.eventloop.Eventloop.class).toInstance(io.activej.eventloop.Eventloop.create());

    // Executor for async operations
    builder
        .bind(Executor.class)
        .toInstance(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2));

    // HTTP Server (configured in this class's createHttpServer method)
  }

  @Override
  protected HttpServer createHttpServer(Injector injector) {
    logger.info("Creating HTTP server for AI Requirements service");
    return injector.getInstance(RequirementsHttpServer.class).create();
  }

  @Override
  protected void onApplicationStarted() {
    logger.info("AI Requirements service started successfully");
    logger.info("Available endpoints:");
    logger.info("  - REST API: http://localhost:8080/api/v1/*");
    logger.info("  - GraphQL: http://localhost:8080/graphql");
    logger.info("  - Health Check: http://localhost:8080/health");
  }

  @Override
  protected void onApplicationStopping() {
    logger.info("Shutting down AI Requirements service");
  }
}