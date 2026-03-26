/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Data-Cloud startup configuration before the server begins serving traffic.
 *
 * <p>Called once during application startup to perform fail-fast validation of all
 * required environment variables. Missing or invalid configuration is reported as a
 * single comprehensive message rather than discovered piece-by-piece during runtime.
 *
 * <h2>Validation rules</h2>
 * <ul>
 *   <li>STANDALONE mode: {@code DC_SERVER_URL} must be set and must be {@code https://}
 *       outside development</li>
 *   <li>DISTRIBUTED mode: {@code DC_CLUSTER_URLS} must be set with at least 2 hosts,
 *       and must be {@code https://} outside development</li>
 *   <li>EMBEDDED mode: no URL required</li>
 *   <li>Non-development: auth token is required for remote modes</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Fail-fast startup validation for Data-Cloud environment configuration
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class DataCloudStartupValidator {

    private static final Logger LOG = LoggerFactory.getLogger(DataCloudStartupValidator.class);

    private DataCloudStartupValidator() {}

    /**
     * Validates the configuration provided by {@link DataCloudEnvConfig}.
     *
     * <p>Logs all validation errors together, then throws {@link IllegalStateException}
     * if any are found.
     *
     * @param config environment config to validate
     * @throws IllegalStateException if one or more validation errors are found
     */
    public static void validate(DataCloudEnvConfig config) {
        List<String> errors = new ArrayList<>();

        String mode = config.deploymentMode();
        boolean isDev = config.isDevelopment();

        switch (mode) {
            case "STANDALONE" -> {
                String url = config.serverUrl();
                if (url.isBlank()) {
                    errors.add("DC_DEPLOYMENT_MODE=STANDALONE requires DC_SERVER_URL to be set.");
                } else if (!isDev && !url.startsWith("https://")) {
                    errors.add("DC_SERVER_URL must start with 'https://' in non-development environments. "
                        + "Got: " + url);
                }
                if (!isDev && config.authToken().isBlank()) {
                    errors.add("STANDALONE/DISTRIBUTED mode outside development requires "
                        + "DATACLOUD_HTTP_AUTH_TOKEN to be set.");
                }
            }
            case "DISTRIBUTED" -> {
                String clusterUrls = config.clusterUrls();
                if (clusterUrls.isBlank()) {
                    errors.add("DC_DEPLOYMENT_MODE=DISTRIBUTED requires DC_CLUSTER_URLS to be set "
                        + "(comma-separated list of at least 2 URLs).");
                } else {
                    String[] urls = clusterUrls.split(",");
                    if (urls.length < 2) {
                        errors.add("DC_CLUSTER_URLS must contain at least 2 nodes for DISTRIBUTED mode. "
                            + "Got: " + urls.length);
                    }
                    if (!isDev) {
                        for (String url : urls) {
                            if (!url.trim().startsWith("https://")) {
                                errors.add("All DC_CLUSTER_URLS must start with 'https://' in "
                                    + "non-development environments. Got: " + url.trim());
                                break; // report once for brevity
                            }
                        }
                    }
                }
                if (!isDev && config.authToken().isBlank()) {
                    errors.add("STANDALONE/DISTRIBUTED mode outside development requires "
                        + "DATACLOUD_HTTP_AUTH_TOKEN to be set.");
                }
            }
            case "EMBEDDED" -> {
                // no URL validation needed for embedded mode
                LOG.debug("DC_DEPLOYMENT_MODE=EMBEDDED — in-process, no remote URL required.");
            }
            default -> errors.add("DC_DEPLOYMENT_MODE must be EMBEDDED, STANDALONE, or DISTRIBUTED. "
                + "Got: " + mode);
        }

        if (!errors.isEmpty()) {
            String message = buildErrorMessage(mode, errors);
            LOG.error("Data-Cloud startup validation FAILED:\n{}", message);
            throw new IllegalStateException("Data-Cloud startup validation failed:\n" + message);
        }

        LOG.info("Data-Cloud startup validation passed — mode={}, env={}",
            mode, isDev ? "development" : "production");
    }

    /**
     * Convenience overload that reads from the system environment.
     *
     * @throws IllegalStateException if validation fails
     */
    public static void validateFromSystem() {
        validate(DataCloudEnvConfig.fromSystem());
    }

    private static String buildErrorMessage(String mode, List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mode: ").append(mode).append('\n');
        sb.append("Errors (").append(errors.size()).append("):\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(errors.get(i)).append('\n');
        }
        return sb.toString();
    }
}
