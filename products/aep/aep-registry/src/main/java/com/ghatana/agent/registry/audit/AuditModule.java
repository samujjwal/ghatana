package com.ghatana.agent.registry.audit;

import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;

/**
 * @doc.type class
 * @doc.purpose Provides audit module functionality.
 * @doc.layer product
 * @doc.pattern Component
 */
public class AuditModule extends AbstractModule {
    public static Module create() {
        return new AuditModule();
    }

    @Override
    protected void configure() {
        // Bind audit-related dependencies here
    }
}
