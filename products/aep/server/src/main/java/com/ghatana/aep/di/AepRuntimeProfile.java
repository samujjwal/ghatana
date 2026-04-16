/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves the effective AEP runtime profile from environment variables.
 *
 * <p>{@code AEP_PROFILE} takes precedence over {@code AEP_ENV}. The default is
 * {@code production} so unset environments fail safe.
 *
 * @doc.type class
 * @doc.purpose Resolve AEP runtime profile for production fail-fast checks
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class AepRuntimeProfile {

    private static final String PRODUCTION = "production";

    private AepRuntimeProfile() {}

    public static String resolve() {
        return resolve(System.getenv());
    }

    public static String resolve(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");

        String explicitProfile = normalize(environment.get("AEP_PROFILE"));
        if (explicitProfile != null) {
            return explicitProfile;
        }

        String environmentName = normalize(environment.get("AEP_ENV"));
        return environmentName != null ? environmentName : PRODUCTION;
    }

    public static boolean isProduction() {
        return isProduction(System.getenv());
    }

    public static boolean isProduction(Map<String, String> environment) {
        return PRODUCTION.equals(resolve(environment));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
