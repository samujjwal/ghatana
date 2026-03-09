package com.ghatana.refactorer.server.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configuration loader using Typesafe Config. Loads configuration from application.conf with

 * environment variable overrides.

 *

 * @doc.type class

 * @doc.purpose Translate Typesafe Config into strongly typed runtime settings.

 * @doc.layer product

 * @doc.pattern Factory

 */

public final class ConfigLoader {
    private static final Logger logger = LogManager.getLogger(ConfigLoader.class);

    private ConfigLoader() {
        // Utility class
    }

    /**
     * Loads server configuration from application.conf and environment variables.
     *
     * @return ServerConfig instance
     * @throws RuntimeException if configuration loading fails
     */
    public static ServerConfig load() {
        try {
            logger.info("Loading server configuration...");

            // Load configuration with environment variable overrides
            Config config = ConfigFactory.load();

            // Resolve all substitutions
            config = config.resolve();

            ServerConfig serverConfig = ServerConfig.fromConfig(config);

            logger.info("Server configuration loaded successfully");
            logger.debug(
                    "HTTP port: {}, gRPC port: {}, TLS enabled: {}",
                    serverConfig.httpPort(),
                    serverConfig.grpcPort(),
                    serverConfig.tlsEnabled());

            return serverConfig;

        } catch (Exception e) {
            logger.error("Failed to load server configuration", e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }

    /**
     * Validates the loaded configuration for required fields.
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateConfig(ServerConfig config) {
        if (config.httpPort() <= 0 || config.httpPort() > 65535) {
            throw new IllegalArgumentException("Invalid HTTP port: " + config.httpPort());
        }

        if (config.grpcPort() <= 0 || config.grpcPort() > 65535) {
            throw new IllegalArgumentException("Invalid gRPC port: " + config.grpcPort());
        }

        if (config.jwt().issuer() == null || config.jwt().issuer().isEmpty()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }

        if (config.jwt().audience() == null || config.jwt().audience().isEmpty()) {
            throw new IllegalArgumentException("JWT audience is required");
        }

        logger.debug("Configuration validation passed");
    }
}
