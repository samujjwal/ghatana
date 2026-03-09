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
    void setUp() {
        registry = new PluginRegistry();
        mockPlugin = mock(YappcPlugin.class);

        metadata = PluginMetadata.builder()
                .id("test-plugin")
                .name("Test Plugin")
                .version("1.0.0")
                .capabilities(List.of(PluginCapability.BUILD_SYSTEM))
                .supportedLanguages(List.of("java", "kotlin"))
                .supportedBuildSystems(List.of("gradle"))
                .build();

        when(mockPlugin.getMetadata()).thenReturn(metadata);
    }

    @Test
    void testRegisterPlugin() throws PluginException {
        registry.register(mockPlugin);

        assertTrue(registry.isRegistered("test-plugin"));
        assertEquals(1, registry.getPluginCount());
    }

    @Test
    void testRegisterDuplicatePluginThrows() throws PluginException {
        registry.register(mockPlugin);

        assertThrows(PluginException.class, () -> {
            registry.register(mockPlugin);
        });
    }

    @Test
    void testUnregisterPlugin() throws PluginException {
        registry.register(mockPlugin);
        assertTrue(registry.unregister("test-plugin"));
        assertFalse(registry.isRegistered("test-plugin"));
        assertEquals(0, registry.getPluginCount());
    }

    @Test
    void testUnregisterNonExistentPlugin() {
        assertFalse(registry.unregister("non-existent"));
    }

    @Test
    void testGetPlugin() throws PluginException {
        registry.register(mockPlugin);

        assertTrue(registry.getPlugin("test-plugin").isPresent());
        assertEquals(mockPlugin, registry.getPlugin("test-plugin").get());
    }

    @Test
    void testGetNonExistentPlugin() {
        assertTrue(registry.getPlugin("non-existent").isEmpty());
    }

    @Test
    void testGetAllPlugins() throws PluginException {
        registry.register(mockPlugin);

        List<YappcPlugin> plugins = registry.getAllPlugins();
        assertEquals(1, plugins.size());
        assertEquals(mockPlugin, plugins.get(0));
    }

    @Test
    void testGetPluginsByCapability() throws PluginException {
        registry.register(mockPlugin);

        List<YappcPlugin> plugins = registry.getPluginsByCapability(PluginCapability.BUILD_SYSTEM);
        assertEquals(1, plugins.size());
        assertEquals(mockPlugin, plugins.get(0));

        List<YappcPlugin> emptyList = registry.getPluginsByCapability(PluginCapability.ANALYZER);
        assertTrue(emptyList.isEmpty());
    }

    @Test
    void testGetPluginsByLanguage() throws PluginException {
        registry.register(mockPlugin);

        List<YappcPlugin> javaPlugins = registry.getPluginsByLanguage("java");
        assertEquals(1, javaPlugins.size());

        List<YappcPlugin> kotlinPlugins = registry.getPluginsByLanguage("kotlin");
        assertEquals(1, kotlinPlugins.size());

        List<YappcPlugin> pythonPlugins = registry.getPluginsByLanguage("python");
        assertTrue(pythonPlugins.isEmpty());
    }

    @Test
    void testGetPluginsByBuildSystem() throws PluginException {
        registry.register(mockPlugin);

        List<YappcPlugin> gradlePlugins = registry.getPluginsByBuildSystem("gradle");
        assertEquals(1, gradlePlugins.size());

        List<YappcPlugin> mavenPlugins = registry.getPluginsByBuildSystem("maven");
        assertTrue(mavenPlugins.isEmpty());
    }

    @Test
    void testClear() throws PluginException {
        registry.register(mockPlugin);
        assertEquals(1, registry.getPluginCount());

        registry.clear();
        assertEquals(0, registry.getPluginCount());
        assertFalse(registry.isRegistered("test-plugin"));
    }

    @Test
    void testGetAllMetadata() throws PluginException {
        registry.register(mockPlugin);

        List<PluginMetadata> metadataList = registry.getAllMetadata();
        assertEquals(1, metadataList.size());
        assertEquals(metadata, metadataList.get(0));
    }
}
