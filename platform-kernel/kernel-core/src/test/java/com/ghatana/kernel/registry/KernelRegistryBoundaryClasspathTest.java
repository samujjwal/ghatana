package com.ghatana.kernel.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelRegistryBoundaryClasspathTest {

    @Test
    void kernelModuleMustNotDependOnPlatformPluginRegistry() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("com.ghatana.platform.plugin.PluginRegistry"));
    }
}