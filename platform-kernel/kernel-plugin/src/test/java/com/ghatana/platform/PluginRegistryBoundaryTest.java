package com.ghatana.platform.plugin;

import com.ghatana.platform.plugin.impl.DefaultPluginContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginRegistryBoundaryTest {

    @Test
    void pluginModuleMustNotDependOnKernelRegistryApis() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("com.ghatana.kernel.registry.KernelRegistry"));
    }

    @Test
    void defaultPluginContextMustBeBackedByPlatformPluginRegistry() throws Exception {
        DefaultPluginContext context = new DefaultPluginContext(new PluginRegistry(), Map.of());
        Field registryField = DefaultPluginContext.class.getDeclaredField("registry");
        registryField.setAccessible(true);

        assertThat(registryField.getType()).isEqualTo(PluginRegistry.class);
        assertThat(registryField.get(context)).isInstanceOf(PluginRegistry.class);
    }
}
