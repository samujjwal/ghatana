/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api;

import com.ghatana.yappc.api.service.ConfigLoader;
import java.nio.file.Paths;

/** Simple test to verify config loading works. 
 * @doc.type class
 * @doc.purpose Handles config test operations
 * @doc.layer product
 * @doc.pattern Test
*/
public class ConfigTest {
  public static void main(String[] args) {
    System.out.println("Testing YAPPC Configuration Loading...");

    // Initialize config loader
    ConfigLoader configLoader = new ConfigLoader(Paths.get("config").toAbsolutePath());

    System.out.println("Config path: " + Paths.get("config").toAbsolutePath());

    // Test loading domains
    configLoader
        .loadDomains()
        .then(
            domains -> {
              System.out.println("✅ Successfully loaded " + domains.size() + " domains:");
              for (var domain : domains) {
                System.out.println("  - " + domain.name() + " (" + domain.order() + ")");
              }
              return configLoader.loadWorkflows();
            })
        .then(
            workflows -> {
              System.out.println("✅ Successfully loaded " + workflows.size() + " workflows:");
              for (var workflow : workflows) {
                System.out.println("  - " + workflow.name());
              }
              return configLoader.loadLifecycleConfig();
            })
        .then(
            lifecycle -> {
              System.out.println(
                  "✅ Successfully loaded lifecycle config with "
                      + lifecycle.stages().size()
                      + " stages");
              return configLoader.loadAgentCapabilities();
            })
        .then(
            capabilities -> {
              System.out.println(
                  "✅ Successfully loaded agent capabilities with "
                      + capabilities.capabilities().size()
                      + " capabilities");
              System.out.println("\n🎉 All configuration loaded successfully!");
              return null;
            })
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                System.err.println("❌ Configuration loading failed: " + error.getMessage());
                error.printStackTrace();
              } else {
                System.out.println("Configuration test completed successfully.");
              }
            });
  }
}
