/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api;

import com.ghatana.yappc.api.controller.ConfigController;
import com.ghatana.yappc.api.service.ConfigLoader;
import com.ghatana.yappc.api.service.ConfigService;
import java.nio.file.Paths;

/** Simple test to verify config system instantiation. 
 * @doc.type class
 * @doc.purpose Handles config test simple operations
 * @doc.layer product
 * @doc.pattern ValueObject
*/
public class ConfigTestSimple {
  public static void main(String[] args) {
    System.out.println("Testing ConfigController instantiation...");

    try {
      // Create config path
      var configPath = Paths.get("config").toAbsolutePath();
      System.out.println("Config path: " + configPath);

      // Create config loader
      ConfigLoader configLoader = new ConfigLoader(configPath);
      System.out.println("✅ ConfigLoader created");

      // Create config service
      ConfigService configService = new ConfigService(configLoader);
      System.out.println("✅ ConfigService created");

      // Create config controller
      ConfigController configController = new ConfigController(configService);
      System.out.println("✅ ConfigController created");

      System.out.println("🎉 All config components instantiated successfully!");

    } catch (Exception e) {
      System.err.println("❌ Error during instantiation: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
