package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.CompiledCollectionConfig;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ConfigRegistry File Reload Tests")
class ConfigRegistryFileReloadTest extends EventloopTestBase {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DC-3: reloadAsync picks up modified collection file and updates cached config")
    void reloadAsync_updatesCachedConfigAfterFileChange() throws IOException {
        Path collectionFile = tempDir.resolve("collections/tenant-a/users.yaml");
        write(collectionFile, collectionYaml("users", "tenant-a", "Users V1"));

        MetricsCollector metrics = mock(MetricsCollector.class);
        ConfigLoader loader = new ConfigLoader(tempDir, DIRECT_EXECUTOR);
        ConfigRegistry registry = new ConfigRegistry(loader, new ConfigValidator(), new CollectionConfigCompiler(), metrics);

        CompiledCollectionConfig first = runPromise(() -> registry.getCollectionAsync("tenant-a", "users"));
        assertThat(first.displayName()).isEqualTo("Users V1");

        write(collectionFile, collectionYaml("users", "tenant-a", "Users V2"));
        runPromise(() -> registry.reloadAsync("tenant-a", "users"));

        CompiledCollectionConfig reloaded = registry.getCollectionIfCached("tenant-a", "users");
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.displayName()).isEqualTo("Users V2");
        assertThat(reloaded.configVersion()).isGreaterThan(first.configVersion());

        verify(metrics, atLeastOnce())
                .incrementCounter("config.load.success", "tenant", "tenant-a", "collection", "users");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static String collectionYaml(String name, String namespace, String displayName) {
        return """
                apiVersion: datacloud.ghatana.com/v1
                kind: Collection
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  recordType: ENTITY
                  displayName: %s
                  schema:
                    version: v1
                    fields:
                      - name: id
                        type: string
                        required: true
                  storage:
                    profile: default
                """.formatted(name, namespace, displayName);
    }
}


