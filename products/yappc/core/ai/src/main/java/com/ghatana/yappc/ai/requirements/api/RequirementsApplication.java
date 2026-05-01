package com.ghatana.yappc.ai.requirements.api;

import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.ai.requirements.api.config.RequirementsConfig;
import com.ghatana.yappc.ai.requirements.api.http.RequirementsHttpServer;
import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
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
 *   <li>{@code SERVER_PORT} - HTTP server port (default: 8082)
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

    // Observability metrics collector for feedback learning
    builder.bind(MetricsCollector.class).toInstance(MetricsCollector.create());

    // Durable DataSource for feedback learning counters (P0: persist across restarts)
    builder.bind(DataSource.class).toInstance(createDataSource());

    // HTTP Server (configured in this class's createHttpServer method)
  }

  private static DataSource createDataSource() {
    String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/yappc_ai");
    String user = System.getenv().getOrDefault("DB_USER", "yappc");
    String pass = System.getenv().getOrDefault("DB_PASSWORD", "yappc");
    com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(user);
    config.setPassword(pass);
    config.setMaximumPoolSize(10);
    return new com.zaxxer.hikari.HikariDataSource(config);
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
    logger.info("  - REST API: http://localhost:8082/api/v1/*");
    logger.info("  - GraphQL: http://localhost:8082/graphql");
    logger.info("  - Health Check: http://localhost:8082/health");
  }

  @Override
  protected void onApplicationStopping() {
    logger.info("Shutting down AI Requirements service");
  }
}
