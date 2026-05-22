package com.ghatana.platform.plugin.test;

import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for testing plugins using ActiveJ Eventloop.
 * Ensures all tests run within the event loop context.
 *
 * @doc.type class
 * @doc.purpose Base test class for async plugin testing
 * @doc.layer platform
 * @doc.pattern TestBase
 */
public abstract class PluginTestBase extends EventloopTestBase {

    protected PluginRegistry registry;
    protected PluginContext context;

    @BeforeEach
    void setUpPluginTestBase() {
        registry = new PluginRegistry();
        context = new TestPluginContext();
    }

    protected Promise<Void> registerAndInit(Plugin plugin) {
        registry.register(plugin);
        return registry.initializeAll(context);
    }
}
