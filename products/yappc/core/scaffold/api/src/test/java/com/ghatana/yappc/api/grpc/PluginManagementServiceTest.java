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

    private final PluginManager pluginManager = new PluginManager(); // GH-90000
    private final PluginManagementService service = new PluginManagementService(pluginManager); // GH-90000

    @Test
    void listPlugins_empty_ok() { // GH-90000
        var responses = new ArrayList<PluginManagementService.ListPluginsResponse>(); // GH-90000
        TestObserver<PluginManagementService.ListPluginsResponse> observer = new TestObserver<>(responses); // GH-90000

        service.listPlugins(new PluginManagementService.ListPluginsRequest(), observer); // GH-90000

        assertThat(observer.completed).isTrue(); // GH-90000
    }

    @Test
    void streamHealthChecks_empty_ok() { // GH-90000
        var responses = new ArrayList<PluginManagementService.HealthCheckResult>(); // GH-90000
        TestObserver<PluginManagementService.HealthCheckResult> observer = new TestObserver<>(responses); // GH-90000

        PluginManagementService.StreamHealthRequest request = new PluginManagementService.StreamHealthRequest(); // GH-90000
        service.streamHealthChecks(request, observer); // GH-90000

        assertThat(observer.completed).isTrue(); // GH-90000
    }

    @Test
    void loadAndUnloadPlugin_ok() throws Exception { // GH-90000
        Path jar = buildTestPluginJar(); // GH-90000

        // Load plugin directly through PluginManager to get clear error messages
        Path workspace = Path.of(System.getProperty("user.dir"));
        Path packs = workspace.resolve("packs");
        com.ghatana.yappc.core.plugin.PluginContext context = new com.ghatana.yappc.core.plugin.PluginContext( // GH-90000
                workspace, packs, Map.of(), // GH-90000
                pluginManager.getEventBus(), // GH-90000
                com.ghatana.yappc.core.plugin.PluginSandbox.permissive(workspace)); // GH-90000
        pluginManager.loadAndInitialize(jar, context); // GH-90000

        assertThat(pluginManager.getPluginState("test-plugin")).isEqualTo(PluginState.ACTIVE);

        // Now test the gRPC service layer for unload
        var unloadResponses = new ArrayList<PluginManagementService.UnloadPluginResponse>(); // GH-90000
        TestObserver<PluginManagementService.UnloadPluginResponse> unloadObserver = new TestObserver<>(unloadResponses); // GH-90000
        PluginManagementService.UnloadPluginRequest unloadReq = new PluginManagementService.UnloadPluginRequest(); // GH-90000
        unloadReq.setPluginId("test-plugin");
        service.unloadPlugin(unloadReq, unloadObserver); // GH-90000

        assertThat(unloadObserver.completed).isTrue(); // GH-90000
        assertThat(pluginManager.getPluginState("test-plugin")).isEqualTo(PluginState.SHUTDOWN);
    }

    private Path buildTestPluginJar() throws IOException { // GH-90000
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
                    public PluginMetadata getMetadata() { // GH-90000
                        return new PluginMetadata( // GH-90000
                                "test-plugin",
                                "Test Plugin",
                                "0.1.0",
                                "Test plugin for integration",
                                "Test",
                                java.util.List.of(PluginCapability.TEMPLATE_HELPER), // GH-90000
                                java.util.List.of("java"),
                                java.util.List.of("gradle"),
                                Map.of(), // GH-90000
                                Map.of(), // GH-90000
                                PluginMetadata.StabilityLevel.EXPERIMENTAL,
                                java.util.List.of()); // GH-90000
                    }

                    @Override
                    public void initialize(PluginContext context) { // GH-90000
                        state = PluginState.INITIALIZED;
                        state = PluginState.ACTIVE;
                    }

                    @Override
                    public PluginHealthResult healthCheck() { // GH-90000
                        return PluginHealthResult.createHealthy("ok");
                    }

                    @Override
                    public void shutdown() { // GH-90000
                        state = PluginState.SHUTDOWN;
                    }

                    @Override
                    public PluginState getState() { // GH-90000
                        return state;
                    }
                }
                """;

        Path javaFile = srcDir.resolve("TestPlugin.java");
        Files.writeString(javaFile, pluginClass); // GH-90000

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler(); // GH-90000
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new javax.tools.DiagnosticCollector<>(); // GH-90000
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) { // GH-90000
            Iterable<? extends javax.tools.JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles( // GH-90000
                    java.util.List.of(javaFile.toFile())); // GH-90000
            String classpath = resolveTestClasspath(); // GH-90000
            List<String> options = List.of("-d", classesDir.toString(), "-classpath", classpath); // GH-90000
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, // GH-90000
                    compilationUnits);
            if (!task.call()) { // GH-90000
                StringBuilder errors = new StringBuilder("Failed to compile test plugin.\nClasspath: " + classpath + "\n"); // GH-90000
                for (javax.tools.Diagnostic<? extends javax.tools.JavaFileObject> d : diagnostics.getDiagnostics()) { // GH-90000
                    errors.append(d.getKind()).append(": ").append(d.getMessage(null)).append("\n");
                }
                throw new IllegalStateException(errors.toString()); // GH-90000
            }
        }

        // Service loader registration
        Path serviceFile = servicesDir.resolve("com.ghatana.yappc.core.plugin.YappcPlugin");
        Files.writeString(serviceFile, "testplugin.TestPlugin"); // GH-90000

        // Build JAR
        Path jarPath = tempDir.resolve("test-plugin.jar");
        java.util.jar.Manifest manifest = new java.util.jar.Manifest(); // GH-90000
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0"); // GH-90000
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) { // GH-90000
            // classes
            Path classFile = classesDir.resolve("testplugin/TestPlugin.class");
            jos.putNextEntry(new JarEntry("testplugin/TestPlugin.class"));
            jos.write(Files.readAllBytes(classFile)); // GH-90000
            jos.closeEntry(); // GH-90000

            // services entry
            jos.putNextEntry(new JarEntry("META-INF/services/com.ghatana.yappc.core.plugin.YappcPlugin"));
            jos.write(Files.readAllBytes(serviceFile)); // GH-90000
            jos.closeEntry(); // GH-90000
        }

        return jarPath;
    }

    private static class TestObserver<T> implements StreamObserver<T> {
        private final List<T> items;
        private boolean completed = false;
        private Throwable error = null;

        TestObserver(List<T> items) { // GH-90000
            this.items = items;
        }

        @Override
        public void onNext(T value) { // GH-90000
            items.add(value); // GH-90000
        }

        @Override
        public void onError(Throwable t) { // GH-90000
            this.error = t;
        }

        @Override
        public void onCompleted() { // GH-90000
            this.completed = true;
        }
    }

    private static String resolveTestClasspath() { // GH-90000
        Set<String> paths = new LinkedHashSet<>(); // GH-90000
        // Add locations of classes required by the dynamically compiled test plugin
        addClassLocation(paths, com.ghatana.yappc.core.plugin.PluginManager.class); // GH-90000
        addClassLocation(paths, com.ghatana.yappc.core.plugin.PluginState.class); // GH-90000
        // Also add system classpath entries
        String sysCp = System.getProperty("java.class.path");
        if (sysCp != null) { // GH-90000
            for (String p : sysCp.split(File.pathSeparator)) { // GH-90000
                if (!p.isBlank()) paths.add(p); // GH-90000
            }
        }
        return String.join(File.pathSeparator, paths); // GH-90000
    }

    private static void addClassLocation(Set<String> paths, Class<?> clazz) { // GH-90000
        try {
            java.security.ProtectionDomain pd = clazz.getProtectionDomain(); // GH-90000
            if (pd != null && pd.getCodeSource() != null) { // GH-90000
                URL location = pd.getCodeSource().getLocation(); // GH-90000
                if (location != null) { // GH-90000
                    paths.add(new File(location.toURI()).getAbsolutePath()); // GH-90000
                }
            }
        } catch (Exception e) { // GH-90000
            // ignore
        }
    }
}
