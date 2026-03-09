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

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles plugin metadata test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PluginMetadataTest {

    @Test
    void testBuilderCreatesValidMetadata() {
        PluginMetadata metadata = PluginMetadata.builder()
                .id("test-plugin")
                .name("Test Plugin")
                .version("1.0.0")
                .description("A test plugin")
                .author("Test Author")
                .capabilities(List.of(PluginCapability.BUILD_SYSTEM))
                .supportedLanguages(List.of("java"))
                .supportedBuildSystems(List.of("gradle"))
                .stability(PluginMetadata.StabilityLevel.STABLE)
                .build();

        assertEquals("test-plugin", metadata.id());
        assertEquals("Test Plugin", metadata.name());
        assertEquals("1.0.0", metadata.version());
        assertEquals("A test plugin", metadata.description());
        assertEquals("Test Author", metadata.author());
        assertEquals(1, metadata.capabilities().size());
        assertEquals(PluginCapability.BUILD_SYSTEM, metadata.capabilities().get(0));
        assertEquals(PluginMetadata.StabilityLevel.STABLE, metadata.stability());
    }

    @Test
    void testBuilderRequiresId() {
        assertThrows(IllegalStateException.class, () -> {
            PluginMetadata.builder()
                    .name("Test Plugin")
                    .version("1.0.0")
                    .build();
        });
    }

    @Test
    void testBuilderRequiresName() {
        assertThrows(IllegalStateException.class, () -> {
            PluginMetadata.builder()
                    .id("test-plugin")
                    .version("1.0.0")
                    .build();
        });
    }

    @Test
    void testBuilderRequiresVersion() {
        assertThrows(IllegalStateException.class, () -> {
            PluginMetadata.builder()
                    .id("test-plugin")
                    .name("Test Plugin")
                    .build();
        });
    }

    @Test
    void testBuilderWithDefaults() {
        PluginMetadata metadata = PluginMetadata.builder()
                .id("test-plugin")
                .name("Test Plugin")
                .version("1.0.0")
                .build();

        assertTrue(metadata.capabilities().isEmpty());
        assertTrue(metadata.supportedLanguages().isEmpty());
        assertTrue(metadata.supportedBuildSystems().isEmpty());
        assertTrue(metadata.requiredConfig().isEmpty());
        assertTrue(metadata.optionalConfig().isEmpty());
        assertTrue(metadata.dependencies().isEmpty());
        assertEquals(PluginMetadata.StabilityLevel.STABLE, metadata.stability());
    }

    @Test
    void testMetadataWithConfig() {
        Map<String, String> requiredConfig = Map.of("apiKey", "API key for service");
        Map<String, String> optionalConfig = Map.of("timeout", "Request timeout in seconds");

        PluginMetadata metadata = PluginMetadata.builder()
                .id("test-plugin")
                .name("Test Plugin")
                .version("1.0.0")
                .requiredConfig(requiredConfig)
                .optionalConfig(optionalConfig)
                .build();

        assertEquals(1, metadata.requiredConfig().size());
        assertEquals("API key for service", metadata.requiredConfig().get("apiKey"));
        assertEquals(1, metadata.optionalConfig().size());
        assertEquals("Request timeout in seconds", metadata.optionalConfig().get("timeout"));
    }

    @Test
    void testMetadataWithDependencies() {
        List<String> dependencies = List.of("plugin-a", "plugin-b");

        PluginMetadata metadata = PluginMetadata.builder()
                .id("test-plugin")
                .name("Test Plugin")
                .version("1.0.0")
                .dependencies(dependencies)
                .build();

        assertEquals(2, metadata.dependencies().size());
        assertTrue(metadata.dependencies().contains("plugin-a"));
        assertTrue(metadata.dependencies().contains("plugin-b"));
    }
}
