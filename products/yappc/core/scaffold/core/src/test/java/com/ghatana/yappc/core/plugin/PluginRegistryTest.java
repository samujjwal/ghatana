/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**

 * @doc.type class

 * @doc.purpose Handles plugin registry test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PluginRegistryTest {

    private PluginRegistry registry;
    private YappcPlugin mockPlugin;
    private PluginMetadata metadata;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new PluginRegistry(); // GH-90000
        mockPlugin = mock(YappcPlugin.class); // GH-90000

        metadata = PluginMetadata.builder() // GH-90000
                .id("test-plugin [GH-90000]")
                .name("Test Plugin [GH-90000]")
                .version("1.0.0 [GH-90000]")
                .capabilities(List.of(PluginCapability.BUILD_SYSTEM)) // GH-90000
                .supportedLanguages(List.of("java", "kotlin")) // GH-90000
                .supportedBuildSystems(List.of("gradle [GH-90000]"))
                .build(); // GH-90000

        when(mockPlugin.getMetadata()).thenReturn(metadata); // GH-90000
    }

    @Test
    void testRegisterPlugin() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        assertTrue(registry.isRegistered("test-plugin [GH-90000]"));
        assertEquals(1, registry.getPluginCount()); // GH-90000
    }

    @Test
    void testRegisterDuplicatePluginThrows() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        assertThrows(PluginException.class, () -> { // GH-90000
            registry.register(mockPlugin); // GH-90000
        });
    }

    @Test
    void testUnregisterPlugin() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000
        assertTrue(registry.unregister("test-plugin [GH-90000]"));
        assertFalse(registry.isRegistered("test-plugin [GH-90000]"));
        assertEquals(0, registry.getPluginCount()); // GH-90000
    }

    @Test
    void testUnregisterNonExistentPlugin() { // GH-90000
        assertFalse(registry.unregister("non-existent [GH-90000]"));
    }

    @Test
    void testGetPlugin() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        assertTrue(registry.getPlugin("test-plugin [GH-90000]").isPresent());
        assertEquals(mockPlugin, registry.getPlugin("test-plugin [GH-90000]").get());
    }

    @Test
    void testGetNonExistentPlugin() { // GH-90000
        assertTrue(registry.getPlugin("non-existent [GH-90000]").isEmpty());
    }

    @Test
    void testGetAllPlugins() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        List<YappcPlugin> plugins = registry.getAllPlugins(); // GH-90000
        assertEquals(1, plugins.size()); // GH-90000
        assertEquals(mockPlugin, plugins.get(0)); // GH-90000
    }

    @Test
    void testGetPluginsByCapability() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        List<YappcPlugin> plugins = registry.getPluginsByCapability(PluginCapability.BUILD_SYSTEM); // GH-90000
        assertEquals(1, plugins.size()); // GH-90000
        assertEquals(mockPlugin, plugins.get(0)); // GH-90000

        List<YappcPlugin> emptyList = registry.getPluginsByCapability(PluginCapability.ANALYZER); // GH-90000
        assertTrue(emptyList.isEmpty()); // GH-90000
    }

    @Test
    void testGetPluginsByLanguage() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        List<YappcPlugin> javaPlugins = registry.getPluginsByLanguage("java [GH-90000]");
        assertEquals(1, javaPlugins.size()); // GH-90000

        List<YappcPlugin> kotlinPlugins = registry.getPluginsByLanguage("kotlin [GH-90000]");
        assertEquals(1, kotlinPlugins.size()); // GH-90000

        List<YappcPlugin> pythonPlugins = registry.getPluginsByLanguage("python [GH-90000]");
        assertTrue(pythonPlugins.isEmpty()); // GH-90000
    }

    @Test
    void testGetPluginsByBuildSystem() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        List<YappcPlugin> gradlePlugins = registry.getPluginsByBuildSystem("gradle [GH-90000]");
        assertEquals(1, gradlePlugins.size()); // GH-90000

        List<YappcPlugin> mavenPlugins = registry.getPluginsByBuildSystem("maven [GH-90000]");
        assertTrue(mavenPlugins.isEmpty()); // GH-90000
    }

    @Test
    void testClear() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000
        assertEquals(1, registry.getPluginCount()); // GH-90000

        registry.clear(); // GH-90000
        assertEquals(0, registry.getPluginCount()); // GH-90000
        assertFalse(registry.isRegistered("test-plugin [GH-90000]"));
    }

    @Test
    void testGetAllMetadata() throws PluginException { // GH-90000
        registry.register(mockPlugin); // GH-90000

        List<PluginMetadata> metadataList = registry.getAllMetadata(); // GH-90000
        assertEquals(1, metadataList.size()); // GH-90000
        assertEquals(metadata, metadataList.get(0)); // GH-90000
    }
}
