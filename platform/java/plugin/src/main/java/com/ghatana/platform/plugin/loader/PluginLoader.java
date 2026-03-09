package com.ghatana.platform.plugin.loader;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for loading plugins from a source.
 *
 * @doc.type interface
 * @doc.purpose Plugin loading abstraction
 * @doc.layer core
 */
public interface PluginLoader {

    /**
     * Loads plugins from the specified directory.
     *
     * @param directory The directory to scan for plugins
     * @return A Promise resolving to a list of loaded plugins
     */
    @NotNull
    Promise<List<Plugin>> loadPlugins(@NotNull Path directory);
}
