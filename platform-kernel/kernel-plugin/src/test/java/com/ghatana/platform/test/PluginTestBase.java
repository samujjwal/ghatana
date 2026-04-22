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
    public void setUp() { // GH-90000
        registry = new PluginRegistry(); // GH-90000
        context = new TestPluginContext(); // GH-90000
    }

    /**
     * Helper to register and initialize a plugin.
     * Must be called within runPromise or similar context.
     */
    protected Promise<Void> registerAndInit(Plugin plugin) { // GH-90000
        registry.register(plugin); // GH-90000
        return registry.initializeAll(context); // GH-90000
    }
}
