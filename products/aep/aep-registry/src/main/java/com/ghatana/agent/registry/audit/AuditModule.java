package com.ghatana.agent.registry.audit;

import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;

public class AuditModule extends AbstractModule {
    public static Module create() {
        return new AuditModule();
    }

    @Override
    protected void configure() {
        // Bind audit-related dependencies here
    }
}
