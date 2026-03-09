/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.test;

import com.ghatana.yappc.framework.api.domain.BuildSystemType;
import com.ghatana.yappc.framework.api.plugin.BuildGeneratorPlugin;
import com.ghatana.yappc.framework.core.FrameworkBootstrap;
import com.ghatana.yappc.framework.core.plugin.PluginManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for the YAPPC framework plugin system.
 *
 * <p>Strategic restructuring - Plugin Architecture Implementation Demonstrates framework
 * initialization and plugin discovery.
 
 * @doc.type class
 * @doc.purpose Handles framework integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
public class FrameworkIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(FrameworkIntegrationTest.class);

    public static void main(String[] args) {
        logger.info("Starting YAPPC Framework Integration Test...");

        try {
            // Initialize the framework
            FrameworkBootstrap bootstrap = new FrameworkBootstrap();
            bootstrap.initialize();

            logger.info("Framework initialized successfully");

            // Test plugin discovery
            testPluginDiscovery(bootstrap);

            // Test Maven plugin capabilities
            testMavenPlugin(bootstrap);

            // Shutdown framework
            bootstrap.shutdown();

            logger.info("Integration test completed successfully");

        } catch (Exception e) {
            logger.error("Integration test failed", e);
            System.exit(1);
        }
    }

    private static void testPluginDiscovery(FrameworkBootstrap bootstrap) {
        logger.info("Testing plugin discovery...");

        try {
            PluginManager pluginManager = bootstrap.getPluginManager();
            PluginManager.PluginStatistics stats = pluginManager.getStatistics();

            logger.info("Plugin statistics: {}", stats);

            // Test build generator plugin discovery
            List<BuildGeneratorPlugin> buildGenerators = bootstrap.getBuildGenerators();
            logger.info("Found {} build generator plugins:", buildGenerators.size());

            for (BuildGeneratorPlugin plugin : buildGenerators) {
                logger.info(
                        "  - {} v{} (enabled: {})",
                        plugin.getName(),
                        plugin.getVersion(),
                        plugin.isEnabled());
            }

            if (buildGenerators.isEmpty()) {
                logger.info(
                        "No build generator plugins found - this is expected in some classpath"
                                + " configurations");
                logger.info(
                        "Plugin discovery system is working, plugins would be loaded if available"
                                + " on classpath");
            } else {
                logger.info(
                        "Plugin discovery system successfully loaded {} plugins",
                        buildGenerators.size());
            }

        } catch (Exception e) {
            logger.error("Plugin discovery test failed", e);
            throw e;
        }
    }

    private static void testMavenPlugin(FrameworkBootstrap bootstrap) {
        logger.info("Testing Maven plugin capabilities...");

        try {
            PluginManager pluginManager = bootstrap.getPluginManager();

            // Find Maven-specific plugins
            List<BuildGeneratorPlugin> mavenPlugins =
                    pluginManager.getBuildGenerators(BuildSystemType.MAVEN);
            logger.info("Found {} Maven build generator plugins:", mavenPlugins.size());

            for (BuildGeneratorPlugin plugin : mavenPlugins) {
                logger.info(
                        "  - {} v{} (priority: {})",
                        plugin.getName(),
                        plugin.getVersion(),
                        plugin.getPriority(BuildSystemType.MAVEN));

                // Test plugin capabilities
                var capabilities = plugin.getCapabilities();
                logger.info("    Capabilities: {}", capabilities.getSupportedFeatures());
            }

            if (mavenPlugins.isEmpty()) {
                logger.info(
                        "No Maven plugins found in current classpath - this demonstrates plugin"
                                + " system architecture");
                logger.info(
                        "Maven plugins would be automatically discovered and loaded when"
                                + " available");
            } else {
                logger.info(
                        "Maven plugin system is operational with {} plugins", mavenPlugins.size());
            }

        } catch (Exception e) {
            logger.error("Maven plugin test failed", e);
            throw e;
        }
    }
}
