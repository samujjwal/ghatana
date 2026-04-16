/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.identity.IdentityResolutionService;
import com.ghatana.aep.identity.JdbcAgentIdentityResolver;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.IdentityResolver;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production-only AEP DI module.
 *
 * <p>Extends {@link AepCoreModule} but validates mandatory production
 * configuration eagerly so the service fails fast before binding unsafe
 * in-memory fallbacks.
 *
 * @doc.type class
 * @doc.purpose Production AEP DI profile with mandatory DB and JWT configuration
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepProductionModule extends AepCoreModule {

    private final Map<String, String> environment;
    private DataSource cachedDataSource;

    public AepProductionModule() {
        this(System.getenv());
    }

    public AepProductionModule(Map<String, String> environment) {
        validateRequiredConfiguration(environment);
        this.environment = Map.copyOf(environment);
    }

    @Override
    DataSource dataSource() {
        if (cachedDataSource != null) {
            return cachedDataSource;
        }
        DataSource dataSource = super.dataSource(environment);
        if (dataSource == null) {
            throw new IllegalStateException(
                "AEP_DB_URL must be configured when AEP_PROFILE=production");
        }
        cachedDataSource = dataSource;
        return cachedDataSource;
    }

    @Override
    IdentityService identityService() {
        String jwtSecret = environment.get("AEP_JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "AEP_JWT_SECRET must be configured when AEP_PROFILE=production");
        }

        return IdentityResolutionService.identityServiceWithResolvers(identityResolvers(dataSource()));
    }

    List<IdentityResolver> identityResolvers(DataSource dataSource) {
        return identityResolvers(environment, dataSource);
    }

    @Override
    protected IdentityResolver baseIdentityResolver(DataSource dataSource) {
        return new JdbcAgentIdentityResolver(dataSource);
    }

    static void validateRequiredConfiguration(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        if (!AepRuntimeProfile.isProduction(environment)) {
            return;
        }

        requireValue(environment, "AEP_DB_URL");
        requireValue(environment, "AEP_JWT_SECRET");
    }

    private static void requireValue(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                key + " must be configured when AEP_PROFILE=production");
        }
    }

}
