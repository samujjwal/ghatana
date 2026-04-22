package com.ghatana.platform.plugin.test;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.PluginState;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the InMemoryStoragePlugin reference implementation.
 *
 * @doc.type class
 * @doc.purpose Verify reference implementation behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
public class InMemoryStoragePluginTest extends PluginTestBase {

    @Test
    public void shouldInitializeAndStart() { // GH-90000
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin(); // GH-90000

        runPromise(() -> registerAndInit(plugin) // GH-90000
                .then(() -> registry.startAll()) // GH-90000
                .whenResult(() -> { // GH-90000
                    assertEquals(PluginState.RUNNING, plugin.getState()); // GH-90000
                    // Health check not implemented in base Plugin interface default, but good to have
                    // assertTrue(plugin.healthCheck().getResult().isHealthy()); // GH-90000
                }));
    }

    @Test
    public void shouldWriteAndRead() { // GH-90000
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin(); // GH-90000
        TenantId tenantId = TenantId.random(); // GH-90000
        String record = "test-record";

        runPromise(() -> registerAndInit(plugin) // GH-90000
                .then(() -> registry.startAll()) // GH-90000
                .then(() -> plugin.write(record, tenantId)) // GH-90000
                .then(offset -> plugin.read("default", tenantId, offset, 10)) // GH-90000
                .whenResult(records -> { // GH-90000
                    assertTrue(records.contains(record)); // GH-90000
                }));
    }

    @Test
    public void shouldHandleMissingData() { // GH-90000
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin(); // GH-90000
        TenantId tenantId = TenantId.random(); // GH-90000

        runPromise(() -> registerAndInit(plugin) // GH-90000
                .then(() -> registry.startAll()) // GH-90000
                .then(() -> plugin.read("missing", tenantId, Offset.zero(), 10)) // GH-90000
                .whenResult(records -> { // GH-90000
                    assertTrue(records.isEmpty()); // GH-90000
                }));
    }

    @Test
    public void shouldDelete() { // GH-90000
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin(); // GH-90000
        TenantId tenantId = TenantId.random(); // GH-90000
        String record = "del-record";

        runPromise(() -> registerAndInit(plugin) // GH-90000
                .then(() -> registry.startAll()) // GH-90000
                .then(() -> plugin.write(record, tenantId)) // GH-90000
                .then(offset -> plugin.delete("default", tenantId, offset)) // GH-90000
                .then(() -> plugin.read("default", tenantId, Offset.zero(), 10)) // GH-90000
                .whenResult(records -> { // GH-90000
                    assertTrue(records.isEmpty()); // GH-90000
                }));
    }
}
