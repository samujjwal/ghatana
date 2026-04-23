/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 */

package com.ghatana.yappc.core.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles plugin manager test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PluginManagerTest {

    private PluginManager manager;
    private PluginContext context;

    @BeforeEach
    void setUp() { // GH-90000
        manager = new PluginManager(); // GH-90000

        Path workspace = Paths.get("/tmp/test-workspace");
        Path packs = workspace.resolve("packs");

        context = new PluginContext( // GH-90000
                workspace,
                packs,
                Map.of(), // GH-90000
                new PluginEventBus(), // GH-90000
                PluginSandbox.permissive(workspace)); // GH-90000
    }

    @Test
    void testGetPluginState() { // GH-90000
        String pluginId = "test-plugin";
        PluginState state = manager.getPluginState(pluginId); // GH-90000

        assertEquals(PluginState.UNLOADED, state); // GH-90000
    }

    @Test
    void testHealthCheckAll() { // GH-90000
        Map<String, PluginHealthResult> results = manager.healthCheckAll(); // GH-90000

        assertNotNull(results); // GH-90000
        assertTrue(results.isEmpty()); // GH-90000
    }

    @Test
    void testGetEventBus() { // GH-90000
        PluginEventBus eventBus = manager.getEventBus(); // GH-90000

        assertNotNull(eventBus); // GH-90000
    }

    @Test
    void testGetRegistry() { // GH-90000
        PluginRegistry registry = manager.getRegistry(); // GH-90000

        assertNotNull(registry); // GH-90000
        assertEquals(0, registry.getPluginCount()); // GH-90000
    }
}
