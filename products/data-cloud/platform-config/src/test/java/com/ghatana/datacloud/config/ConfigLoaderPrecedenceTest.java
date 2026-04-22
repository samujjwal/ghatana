package com.ghatana.datacloud.config;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.datacloud.config.model.RawPluginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigLoader Precedence Tests")
class ConfigLoaderPrecedenceTest extends EventloopTestBase {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DC-2: tenant plugin file takes precedence over default plugin file")
    void tenantPlugin_precedesDefaultPlugin() throws IOException {
        Path defaultFile = tempDir.resolve("plugins/default/storage.yaml");
        Path tenantFile = tempDir.resolve("plugins/tenant-a/storage.yaml");
        write(defaultFile, pluginYaml("storage", "default", "Default Storage"));
        write(tenantFile, pluginYaml("storage", "tenant-a", "Tenant Storage"));

        ConfigLoader loader = new ConfigLoader(tempDir, DIRECT_EXECUTOR);
        RawPluginConfig plugin = runPromise(() -> loader.loadPluginAsync("tenant-a", "storage"));

        assertThat(plugin.metadata().namespace()).isEqualTo("tenant-a");
        assertThat(plugin.spec().displayName()).isEqualTo("Tenant Storage");
    }

    @Test
    @DisplayName("DC-2: default plugin file is used when tenant-specific file is absent")
    void defaultPlugin_usedAsFallback() throws IOException {
        Path defaultFile = tempDir.resolve("plugins/default/storage.yaml");
        write(defaultFile, pluginYaml("storage", "default", "Default Storage"));

        ConfigLoader loader = new ConfigLoader(tempDir, DIRECT_EXECUTOR);
        RawPluginConfig plugin = runPromise(() -> loader.loadPluginAsync("tenant-a", "storage"));

        assertThat(plugin.metadata().namespace()).isEqualTo("default");
        assertThat(plugin.spec().displayName()).isEqualTo("Default Storage");
    }

    @Test
    @DisplayName("DC-2: listPlugins merges tenant and default entries without duplicates")
    void listPlugins_mergesAndDeduplicates() throws IOException {
        write(tempDir.resolve("plugins/default/storage.yaml"), pluginYaml("storage", "default", "Default Storage"));
        write(tempDir.resolve("plugins/default/audit.yaml"), pluginYaml("audit", "default", "Default Audit"));
        write(tempDir.resolve("plugins/tenant-a/storage.yaml"), pluginYaml("storage", "tenant-a", "Tenant Storage"));

        ConfigLoader loader = new ConfigLoader(tempDir, DIRECT_EXECUTOR);
        List<String> pluginNames = runPromise(() -> loader.listPluginsAsync("tenant-a"));

        assertThat(pluginNames)
                .contains("storage", "audit")
                .doesNotHaveDuplicates();
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static String pluginYaml(String name, String namespace, String displayName) {
        return """
                apiVersion: datacloud.ghatana.com/v1
                kind: Plugin
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  type: STORAGE
                  displayName: %s
                  enabled: true
                """.formatted(name, namespace, displayName);
    }
}

