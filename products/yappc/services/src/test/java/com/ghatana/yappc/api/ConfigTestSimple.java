/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api;

import com.ghatana.yappc.api.controller.ConfigController;
import com.ghatana.yappc.api.service.ConfigLoader;
import com.ghatana.yappc.api.service.ConfigService;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple test to verify config system instantiation. 
 * @doc.type class
 * @doc.purpose Handles config test simple operations
 * @doc.layer product
 * @doc.pattern ValueObject
*/
public class ConfigTestSimple {
  private static final Logger logger = LoggerFactory.getLogger(ConfigTestSimple.class);

  public static void main(String[] args) {
    logger.info("Testing ConfigController instantiation...");

    try {
      // Create config path
      var configPath = Paths.get("config").toAbsolutePath();
      logger.info("Config path: {}", configPath);

      // Create config loader
      ConfigLoader configLoader = new ConfigLoader(configPath);
      logger.info("ConfigLoader created");

      // Create config service
      ConfigService configService = new ConfigService(configLoader);
      logger.info("ConfigService created");

      // Create config controller
      ConfigController configController = new ConfigController(configService);
      logger.info("ConfigController created");

      logger.info("All config components instantiated successfully!");

    } catch (Exception e) {
      logger.error("Error during instantiation: {}", e.getMessage(), e);
    }
  }
}
