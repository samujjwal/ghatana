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
    public void shouldInitializeAndStart() {
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();

        runPromise(() -> registerAndInit(plugin)
                .then(() -> registry.startAll())
                .whenResult(() -> {
                    assertEquals(PluginState.RUNNING, plugin.getState());
                    // Health check not implemented in base Plugin interface default, but good to have
                    // assertTrue(plugin.healthCheck().getResult().isHealthy());
                }));
    }

    @Test
    public void shouldWriteAndRead() {
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
        TenantId tenantId = TenantId.random();
        String record = "test-record";

        runPromise(() -> registerAndInit(plugin)
                .then(() -> registry.startAll())
                .then(() -> plugin.write(record, tenantId))
                .then(offset -> plugin.read("default", tenantId, offset, 10))
                .whenResult(records -> {
                    assertTrue(records.contains(record));
                }));
    }

    @Test
    public void shouldHandleMissingData() {
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
        TenantId tenantId = TenantId.random();

        runPromise(() -> registerAndInit(plugin)
                .then(() -> registry.startAll())
                .then(() -> plugin.read("missing", tenantId, Offset.zero(), 10))
                .whenResult(records -> {
                    assertTrue(records.isEmpty());
                }));
    }

    @Test
    public void shouldDelete() {
        InMemoryStoragePlugin plugin = new InMemoryStoragePlugin();
        TenantId tenantId = TenantId.random();
        String record = "del-record";

        runPromise(() -> registerAndInit(plugin)
                .then(() -> registry.startAll())
                .then(() -> plugin.write(record, tenantId))
                .then(offset -> plugin.delete("default", tenantId, offset))
                .then(() -> plugin.read("default", tenantId, Offset.zero(), 10))
                .whenResult(records -> {
                    assertTrue(records.isEmpty());
                }));
    }
}
