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

package com.ghatana.yappc.cli;

import com.ghatana.yappc.core.plugin.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for plugin management.
 *
 * @doc.type class
 * @doc.purpose Plugin management CLI commands
 * @doc.layer presentation
 * @doc.pattern Command
 */
@Command(name = "plugins", description = "Manage YAPPC plugins", subcommands = {
        PluginsCommand.ListCommand.class,
        PluginsCommand.InstallCommand.class,
        PluginsCommand.EnableCommand.class,
        PluginsCommand.DisableCommand.class,
        PluginsCommand.InfoCommand.class,
        PluginsCommand.HealthCommand.class,
        PluginsCommand.UninstallCommand.class
})
public class PluginsCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(PluginsCommand.class);

    private final PluginManager pluginManager;

    public PluginsCommand() {
        this.pluginManager = new PluginManager();
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "list", description = "List all loaded plugins")
    static class ListCommand implements Callable<Integer> {

        @Option(names = { "-c", "--capability" }, description = "Filter by capability")
        private PluginCapability capability;

        @Option(names = { "-l", "--language" }, description = "Filter by language")
        private String language;

        @Option(names = { "-b", "--build-system" }, description = "Filter by build system")
        private String buildSystem;

        @Option(names = { "--verbose" }, description = "Show detailed information")
        private boolean verbose;

        @Override
        public Integer call() {
            PluginManager manager = new PluginManager();
            List<YappcPlugin> plugins;

            if (capability != null) {
                plugins = manager.getRegistry().getPluginsByCapability(capability);
            } else if (language != null) {
                plugins = manager.getRegistry().getPluginsByLanguage(language);
            } else if (buildSystem != null) {
                plugins = manager.getRegistry().getPluginsByBuildSystem(buildSystem);
            } else {
                plugins = manager.getRegistry().getAllPlugins();
            }

            if (plugins.isEmpty()) {
                log.info("No plugins found.");
                return 0;
            }

            log.info("Loaded plugins: {}", plugins.size());
            log.info("");;

            for (YappcPlugin plugin : plugins) {
                PluginMetadata metadata = plugin.getMetadata();
                PluginState state = manager.getPluginState(metadata.id());

                log.info("  {} v{}", metadata.name(), metadata.version());
                log.info("    ID: {}", metadata.id());
                log.info("    State: {}", state);
                log.info("    Stability: {}", metadata.stability());

                if (verbose) {
                    log.info("    Description: {}", metadata.description());
                    log.info("    Author: {}", metadata.author());
                    log.info("    Capabilities: {}", metadata.capabilities());
                    log.info("    Languages: {}", metadata.supportedLanguages());
                    log.info("    Build Systems: {}", metadata.supportedBuildSystems());
                }
                log.info("");;
            }

            return 0;
        }
    }

    @Command(name = "install", description = "Install a plugin from JAR file")
    static class InstallCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to plugin JAR file")
        private String jarPath;

        @Option(names = { "--workspace" }, description = "Workspace path", defaultValue = ".")
        private String workspacePath;

        @Option(names = { "--packs" }, description = "Packs path")
        private String packsPath;

        @Override
        public Integer call() {
            try {
                PluginManager manager = new PluginManager();
                Path jar = Paths.get(jarPath);
                Path workspace = Paths.get(workspacePath);
                Path packs = packsPath != null ? Paths.get(packsPath) : workspace.resolve("packs");

                PluginContext context = new PluginContext(
                        workspace,
                        packs,
                        Map.of(),
                        new PluginEventBus(),
                        PluginSandbox.permissive(workspace));

                YappcPlugin plugin = manager.loadAndInitialize(jar, context);
                PluginMetadata metadata = plugin.getMetadata();

                log.info("✓ Plugin installed successfully");
                log.info("  Name: {}", metadata.name());
                log.info("  Version: {}", metadata.version());
                log.info("  ID: {}", metadata.id());
                log.info("  Capabilities: {}", metadata.capabilities());

                return 0;

            } catch (PluginException e) {
                log.error("✗ Failed to install plugin: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "enable", description = "Enable a plugin")
    static class EnableCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Plugin ID")
        private String pluginId;

        @Override
        public Integer call() {
            log.info("✓ Plugin enabled: {}", pluginId);
            log.info("  (Plugin enable/disable state management to be implemented)");
            return 0;
        }
    }

    @Command(name = "disable", description = "Disable a plugin")
    static class DisableCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Plugin ID")
        private String pluginId;

        @Override
        public Integer call() {
            log.info("✓ Plugin disabled: {}", pluginId);
            log.info("  (Plugin enable/disable state management to be implemented)");
            return 0;
        }
    }

    @Command(name = "info", description = "Show detailed plugin information")
    static class InfoCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Plugin ID")
        private String pluginId;

        @Override
        public Integer call() {
            PluginManager manager = new PluginManager();
            YappcPlugin plugin = manager.getRegistry().getPlugin(pluginId).orElse(null);

            if (plugin == null) {
                log.error("✗ Plugin not found: {}", pluginId);
                return 1;
            }

            PluginMetadata metadata = plugin.getMetadata();
            PluginState state = manager.getPluginState(pluginId);

            log.info("Plugin Information:");
            log.info("  Name: {}", metadata.name());
            log.info("  ID: {}", metadata.id());
            log.info("  Version: {}", metadata.version());
            log.info("  State: {}", state);
            log.info("  Stability: {}", metadata.stability());
            log.info("");;
            log.info("  Description: {}", metadata.description());
            log.info("  Author: {}", metadata.author());
            log.info("");;
            log.info("  Capabilities: {}", metadata.capabilities());
            log.info("  Supported Languages: {}", metadata.supportedLanguages());
            log.info("  Supported Build Systems: {}", metadata.supportedBuildSystems());
            log.info("");;
            log.info("  Required Config: {}", metadata.requiredConfig());
            log.info("  Optional Config: {}", metadata.optionalConfig());
            log.info("  Dependencies: {}", metadata.dependencies());

            return 0;
        }
    }

    @Command(name = "health", description = "Run health checks on plugins")
    static class HealthCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Plugin ID (optional, checks all if not specified)", arity = "0..1")
        private String pluginId;

        @Override
        public Integer call() {
            PluginManager manager = new PluginManager();

            if (pluginId != null) {
                try {
                    PluginHealthResult result = manager.healthCheck(pluginId);
                    printHealthResult(pluginId, result);
                    return result.healthy() ? 0 : 1;
                } catch (PluginException e) {
                    log.error("✗ Health check failed: {}", e.getMessage());
                    return 1;
                }
            } else {
                Map<String, PluginHealthResult> results = manager.healthCheckAll();
                int unhealthy = 0;

                log.info("Plugin Health Check Results:");
                log.info("");;

                for (Map.Entry<String, PluginHealthResult> entry : results.entrySet()) {
                    printHealthResult(entry.getKey(), entry.getValue());
                    if (!entry.getValue().healthy()) {
                        unhealthy++;
                    }
                }

                log.info("");;
                log.info("Total: {} plugins, {} unhealthy", results.size(), unhealthy);

                return unhealthy > 0 ? 1 : 0;
            }
        }

        private void printHealthResult(String id, PluginHealthResult result) {
            String status = result.healthy() ? "✓" : "✗";
            log.info("  {} {}", status, id);
            log.info("    {}", result.message());
            if (!result.details().isEmpty()) {
                result.details().forEach(detail ->
                    log.info("      - {}", detail));
            }
            log.info("");
        }
    }

    @Command(name = "uninstall", description = "Uninstall a plugin")
    static class UninstallCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Plugin ID")
        private String pluginId;

        @Override
        public Integer call() {
            try {
                PluginManager manager = new PluginManager();
                manager.shutdown(pluginId);
                log.info("✓ Plugin uninstalled: {}", pluginId);
                return 0;
            } catch (PluginException e) {
                log.error("✗ Failed to uninstall plugin: {}", e.getMessage());
                return 1;
            }
        }
    }
}
