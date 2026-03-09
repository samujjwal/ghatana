/*
 * Lightweight integration-style tests for PluginManagementService.
 */

package com.ghatana.yappc.api.grpc;

import com.ghatana.yappc.core.plugin.PluginManager;
import com.ghatana.yappc.core.plugin.PluginState;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**

 * @doc.type class

 * @doc.purpose Handles plugin management service test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PluginManagementServiceTest {

    private final PluginManager pluginManager = new PluginManager();
    private final PluginManagementService service = new PluginManagementService(pluginManager);

    @Test
    void listPlugins_empty_ok() {
        var responses = new ArrayList<PluginManagementService.ListPluginsResponse>();
        TestObserver<PluginManagementService.ListPluginsResponse> observer = new TestObserver<>(responses);

        service.listPlugins(new PluginManagementService.ListPluginsRequest(), observer);

        assertThat(observer.completed).isTrue();
    }

    @Test
    void streamHealthChecks_empty_ok() {
        var responses = new ArrayList<PluginManagementService.HealthCheckResult>();
        TestObserver<PluginManagementService.HealthCheckResult> observer = new TestObserver<>(responses);

        PluginManagementService.StreamHealthRequest request = new PluginManagementService.StreamHealthRequest();
        service.streamHealthChecks(request, observer);

        assertThat(observer.completed).isTrue();
    }

    @Test
    void loadAndUnloadPlugin_ok() throws Exception {
        Path jar = buildTestPluginJar();

        // Load plugin directly through PluginManager to get clear error messages
        Path workspace = Path.of(System.getProperty("user.dir"));
        Path packs = workspace.resolve("packs");
        com.ghatana.yappc.core.plugin.PluginContext context = new com.ghatana.yappc.core.plugin.PluginContext(
                workspace, packs, Map.of(),
                pluginManager.getEventBus(),
                com.ghatana.yappc.core.plugin.PluginSandbox.permissive(workspace));
        pluginManager.loadAndInitialize(jar, context);

        assertThat(pluginManager.getPluginState("test-plugin")).isEqualTo(PluginState.ACTIVE);

        // Now test the gRPC service layer for unload
        var unloadResponses = new ArrayList<PluginManagementService.UnloadPluginResponse>();
        TestObserver<PluginManagementService.UnloadPluginResponse> unloadObserver = new TestObserver<>(unloadResponses);
        PluginManagementService.UnloadPluginRequest unloadReq = new PluginManagementService.UnloadPluginRequest();
        unloadReq.setPluginId("test-plugin");
        service.unloadPlugin(unloadReq, unloadObserver);

        assertThat(unloadObserver.completed).isTrue();
        assertThat(pluginManager.getPluginState("test-plugin")).isEqualTo(PluginState.SHUTDOWN);
    }

    private Path buildTestPluginJar() throws IOException {
        Path tempDir = Files.createTempDirectory("yappc-plugin");
        Path srcDir = Files.createDirectories(tempDir.resolve("src/testplugin"));
        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        Path servicesDir = Files.createDirectories(tempDir.resolve("services/META-INF/services"));

        String pluginClass = """
                package testplugin;

                import com.ghatana.yappc.core.plugin.*;
                import java.util.Map;

                public class TestPlugin implements YappcPlugin {
                    private PluginState state = PluginState.UNLOADED;

                    @Override
                    public PluginMetadata getMetadata() {
                        return new PluginMetadata(
                                "test-plugin",
                                "Test Plugin",
                                "0.1.0",
                                "Test plugin for integration",
                                "Test",
                                java.util.List.of(PluginCapability.TEMPLATE_HELPER),
                                java.util.List.of("java"),
                                java.util.List.of("gradle"),
                                Map.of(),
                                Map.of(),
                                PluginMetadata.StabilityLevel.EXPERIMENTAL,
                                java.util.List.of());
                    }

                    @Override
                    public void initialize(PluginContext context) {
                        state = PluginState.INITIALIZED;
                        state = PluginState.ACTIVE;
                    }

                    @Override
                    public PluginHealthResult healthCheck() {
                        return PluginHealthResult.createHealthy("ok");
                    }

                    @Override
                    public void shutdown() {
                        state = PluginState.SHUTDOWN;
                    }

                    @Override
                    public PluginState getState() {
                        return state;
                    }
                }
                """;

        Path javaFile = srcDir.resolve("TestPlugin.java");
        Files.writeString(javaFile, pluginClass);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new javax.tools.DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends javax.tools.JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    java.util.List.of(javaFile.toFile()));
            String classpath = resolveTestClasspath();
            List<String> options = List.of("-d", classesDir.toString(), "-classpath", classpath);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null,
                    compilationUnits);
            if (!task.call()) {
                StringBuilder errors = new StringBuilder("Failed to compile test plugin.\nClasspath: " + classpath + "\n");
                for (javax.tools.Diagnostic<? extends javax.tools.JavaFileObject> d : diagnostics.getDiagnostics()) {
                    errors.append(d.getKind()).append(": ").append(d.getMessage(null)).append("\n");
                }
                throw new IllegalStateException(errors.toString());
            }
        }

        // Service loader registration
        Path serviceFile = servicesDir.resolve("com.ghatana.yappc.core.plugin.YappcPlugin");
        Files.writeString(serviceFile, "testplugin.TestPlugin");

        // Build JAR
        Path jarPath = tempDir.resolve("test-plugin.jar");
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            // classes
            Path classFile = classesDir.resolve("testplugin/TestPlugin.class");
            jos.putNextEntry(new JarEntry("testplugin/TestPlugin.class"));
            jos.write(Files.readAllBytes(classFile));
            jos.closeEntry();

            // services entry
            jos.putNextEntry(new JarEntry("META-INF/services/com.ghatana.yappc.core.plugin.YappcPlugin"));
            jos.write(Files.readAllBytes(serviceFile));
            jos.closeEntry();
        }

        return jarPath;
    }

    private static class TestObserver<T> implements StreamObserver<T> {
        private final List<T> items;
        private boolean completed = false;
        private Throwable error = null;

        TestObserver(List<T> items) {
            this.items = items;
        }

        @Override
        public void onNext(T value) {
            items.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }

    private static String resolveTestClasspath() {
        Set<String> paths = new LinkedHashSet<>();
        // Add locations of classes required by the dynamically compiled test plugin
        addClassLocation(paths, com.ghatana.yappc.core.plugin.PluginManager.class);
        addClassLocation(paths, com.ghatana.yappc.core.plugin.PluginState.class);
        // Also add system classpath entries
        String sysCp = System.getProperty("java.class.path");
        if (sysCp != null) {
            for (String p : sysCp.split(File.pathSeparator)) {
                if (!p.isBlank()) paths.add(p);
            }
        }
        return String.join(File.pathSeparator, paths);
    }

    private static void addClassLocation(Set<String> paths, Class<?> clazz) {
        try {
            java.security.ProtectionDomain pd = clazz.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null) {
                URL location = pd.getCodeSource().getLocation();
                if (location != null) {
                    paths.add(new File(location.toURI()).getAbsolutePath());
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
