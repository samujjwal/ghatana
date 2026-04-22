package com.ghatana.kernel.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelRegistryBoundaryClasspathTest {

    @Test
    void kernelModuleMustNotDependOnPlatformPluginRegistry() { // GH-90000
        assertThrows(ClassNotFoundException.class, // GH-90000
            () -> Class.forName("com.ghatana.platform.plugin.PluginRegistry [GH-90000]"));
    }
}
