package com.ghatana.platform.plugin.loader;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Loads plugins from JAR files using ServiceLoader.
 *
 * @doc.type class
 * @doc.purpose JAR-based plugin loader
 * @doc.layer core
 */
public class JarPluginLoader implements PluginLoader {

    private final Executor executor;

    public JarPluginLoader(Executor executor) {
        this.executor = executor;
    }

    @Override
    public @NotNull Promise<List<Plugin>> loadPlugins(@NotNull Path directory) {
        return Promise.ofBlocking(executor, () -> {
            List<Plugin> plugins = new ArrayList<>();
            if (!Files.exists(directory)) {
                return plugins;
            }

            try (Stream<Path> stream = Files.walk(directory)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .forEach(jarPath -> plugins.addAll(loadFromJar(jarPath)));
            } catch (Exception e) {
                // Propagate or log? For now propagate to fail the promise
                throw new RuntimeException("Failed to scan plugins directory", e);
            }
            return plugins;
        });
    }

    private List<Plugin> loadFromJar(Path jarPath) {
        List<Plugin> loaded = new ArrayList<>();
        try {
            URL[] urls = {jarPath.toUri().toURL()};
            // Use current thread's context classloader as parent to ensure visibility of core classes
            URLClassLoader classLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
            
            ServiceLoader<Plugin> serviceLoader = ServiceLoader.load(Plugin.class, classLoader);
            for (Plugin plugin : serviceLoader) {
                loaded.add(plugin);
            }
        } catch (Exception e) {
            // Log error but continue loading other jars
            // In a real system, use a proper logger
            System.err.println("Failed to load plugin from " + jarPath + ": " + e.getMessage());
        }
        return loaded;
    }
}
