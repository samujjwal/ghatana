/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api;

import com.ghatana.yappc.api.service.ConfigLoader;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple test to verify config loading works. 
 * @doc.type class
 * @doc.purpose Handles config test operations
 * @doc.layer product
 * @doc.pattern Test
*/
public class ConfigTest {
  private static final Logger logger = LoggerFactory.getLogger(ConfigTest.class);

  public static void main(String[] args) {
    logger.info("Testing YAPPC Configuration Loading...");

    // Initialize config loader
    ConfigLoader configLoader = new ConfigLoader(Paths.get("config").toAbsolutePath());

    logger.info("Config path: {}", Paths.get("config").toAbsolutePath());

    // Test loading domains
    configLoader
        .loadDomains()
        .then(
            domains -> {
              logger.info("Successfully loaded {} domains:", domains.size());
              for (var domain : domains) {
                logger.info("  - {} ({})", domain.name(), domain.order());
              }
              return configLoader.loadWorkflows();
            })
        .then(
            workflows -> {
              logger.info("Successfully loaded {} workflows:", workflows.size());
              for (var workflow : workflows) {
                logger.info("  - {}", workflow.name());
              }
              return configLoader.loadLifecycleConfig();
            })
        .then(
            lifecycle -> {
              logger.info(
                  "Successfully loaded lifecycle config with {} stages",
                  lifecycle.stages().size());
              return configLoader.loadAgentCapabilities();
            })
        .then(
            capabilities -> {
              logger.info(
                  "Successfully loaded agent capabilities with {} capabilities",
                  capabilities.capabilities().size());
              logger.info("All configuration loaded successfully!");
              return null;
            })
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                logger.error("Configuration loading failed: {}", error.getMessage(), error);
              } else {
                logger.info("Configuration test completed successfully.");
              }
            });
  }
}
