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

package com.ghatana.yappc.core.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Loads plugins from JAR files with classloader isolation.
 *
 * @doc.type class
 * @doc.purpose Plugin loading with isolation
 * @doc.layer platform
 * @doc.pattern Factory/Loader
 */
public class PluginLoader {

    private final List<PluginClassLoader> classLoaders = new ArrayList<>();

    /**
     * Loads a plugin from a JAR file.
     *
     * @param jarPath path to plugin JAR
     * @return loaded plugin
     * @throws PluginException if loading fails
     */
    public YappcPlugin loadPlugin(Path jarPath) throws PluginException {
        validateJarFile(jarPath);

        try {
            URL jarUrl = jarPath.toUri().toURL();
            PluginClassLoader classLoader = new PluginClassLoader(
                    new URL[] { jarUrl },
                    getClass().getClassLoader());

            classLoaders.add(classLoader);

            ServiceLoader<YappcPlugin> serviceLoader = ServiceLoader.load(
                    YappcPlugin.class,
                    classLoader);

            YappcPlugin plugin = serviceLoader.findFirst()
                    .orElseThrow(() -> new PluginException(
                            "No plugin implementation found in " + jarPath));

            return plugin;

        } catch (IOException e) {
            throw new PluginException("Failed to load plugin from " + jarPath, e);
        }
    }

    /**
     * Loads all plugins from a directory.
     *
     * @param pluginsDir directory containing plugin JARs
     * @return list of loaded plugins
     * @throws PluginException if loading fails
     */
    public List<YappcPlugin> loadPluginsFromDirectory(Path pluginsDir) throws PluginException {
        if (!Files.isDirectory(pluginsDir)) {
            throw new PluginException("Not a directory: " + pluginsDir);
        }

        try {
            List<Path> jarFiles = Files.list(pluginsDir)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .collect(Collectors.toList());

            List<YappcPlugin> plugins = new ArrayList<>();
            List<PluginException> errors = new ArrayList<>();

            for (Path jarFile : jarFiles) {
                try {
                    plugins.add(loadPlugin(jarFile));
                } catch (PluginException e) {
                    errors.add(e);
                }
            }

            if (!errors.isEmpty() && plugins.isEmpty()) {
                throw new PluginException(
                        "Failed to load any plugins. Errors: " +
                                errors.stream()
                                        .map(Throwable::getMessage)
                                        .collect(Collectors.joining("; ")));
            }

            return plugins;

        } catch (IOException e) {
            throw new PluginException("Failed to list plugins directory", e);
        }
    }

    /**
     * Unloads all plugins and releases classloaders.
     */
    public void unloadAll() {
        for (PluginClassLoader classLoader : classLoaders) {
            try {
                classLoader.close();
            } catch (IOException e) {
                // Log but continue
            }
        }
        classLoaders.clear();
    }

    /**
     * Validates that a JAR file is valid.
     *
     * @param jarPath path to JAR
     * @throws PluginException if validation fails
     */
    private void validateJarFile(Path jarPath) throws PluginException {
        if (!Files.exists(jarPath)) {
            throw new PluginException("Plugin JAR not found: " + jarPath);
        }

        if (!Files.isRegularFile(jarPath)) {
            throw new PluginException("Not a file: " + jarPath);
        }

        if (!jarPath.toString().endsWith(".jar")) {
            throw new PluginException("Not a JAR file: " + jarPath);
        }

        // Validate JAR structure
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            if (jarFile.getManifest() == null) {
                throw new PluginException("JAR has no manifest: " + jarPath);
            }
        } catch (IOException e) {
            throw new PluginException("Invalid JAR file: " + jarPath, e);
        }
    }

    /**
     * Custom classloader for plugin isolation.
     */
    private static class PluginClassLoader extends URLClassLoader {

        public PluginClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Load plugin classes in isolation, but delegate YAPPC core classes to parent
            if (name.startsWith("com.ghatana.yappc.core.plugin.")) {
                return super.loadClass(name, resolve);
            }

            // Try to load from this classloader first
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        // Delegate to parent
                        c = super.loadClass(name, resolve);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }
}
