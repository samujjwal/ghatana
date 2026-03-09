/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
    void setUp() {
        manager = new PluginManager();

        Path workspace = Paths.get("/tmp/test-workspace");
        Path packs = workspace.resolve("packs");

        context = new PluginContext(
                workspace,
                packs,
                Map.of(),
                new PluginEventBus(),
                PluginSandbox.permissive(workspace));
    }

    @Test
    void testGetPluginState() {
        String pluginId = "test-plugin";
        PluginState state = manager.getPluginState(pluginId);

        assertEquals(PluginState.UNLOADED, state);
    }

    @Test
    void testHealthCheckAll() {
        Map<String, PluginHealthResult> results = manager.healthCheckAll();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetEventBus() {
        PluginEventBus eventBus = manager.getEventBus();

        assertNotNull(eventBus);
    }

    @Test
    void testGetRegistry() {
        PluginRegistry registry = manager.getRegistry();

        assertNotNull(registry);
        assertEquals(0, registry.getPluginCount());
    }
}
