package com.ghatana.platform.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HotReloadPluginManager Classloader Release [GH-90000]")
class HotReloadPluginManagerClassLoaderReleaseTest {

    @Test
    @DisplayName("KP-4: stop() closes plugin classloaders and clears loaded plugin map")
    @SuppressWarnings("unchecked")
    void shouldReleaseClassLoadersOnStop() throws Exception {
        HotReloadPluginManager manager = new HotReloadPluginManager(Path.of("build/tmp/kp4-plugins"));

        TrackableClassLoader classLoader = new TrackableClassLoader();
        HotReloadPluginManager.LoadedPlugin loadedPlugin = new HotReloadPluginManager.LoadedPlugin(
            "kp4-plugin",
            Path.of("kp4-plugin.jar"),
            classLoader,
            null
        );

        Field pluginsField = HotReloadPluginManager.class.getDeclaredField("plugins");
        pluginsField.setAccessible(true);
        Map<String, HotReloadPluginManager.LoadedPlugin> plugins =
            (ConcurrentHashMap<String, HotReloadPluginManager.LoadedPlugin>) pluginsField.get(manager);
        plugins.put("kp4-plugin", loadedPlugin);

        manager.stop();

        assertThat(classLoader.closed).isTrue();
        assertThat(plugins).isEmpty();
    }

    private static final class TrackableClassLoader extends URLClassLoader {
        private boolean closed;

        private TrackableClassLoader() {
            super(new URL[0], HotReloadPluginManagerClassLoaderReleaseTest.class.getClassLoader());
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}

