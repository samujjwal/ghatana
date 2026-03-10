/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

import java.util.List;

/**
 * Describes the security permissions granted to a plugin at runtime.
 *
 * <p>All fields use allow-lists: anything not explicitly listed is denied.
 *
 * @doc.type record
 * @doc.purpose Plugin permission specification (network, filesystem, Java packages)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PermissionSet(
        /**
         * Hostnames or IP addresses the plugin may connect to.
         * Empty list means no network access is permitted.
         */
        List<String> allowedNetworkHosts,

        /**
         * Absolute filesystem paths (or prefixes) the plugin may read from or write to.
         * Empty list means no filesystem access outside the plugin's own JAR.
         */
        List<String> allowedFilePaths,

        /**
         * Java package prefixes the plugin's code may reference directly.
         * The platform API packages are always implicitly allowed.
         * Empty list means no additional package access beyond the platform API.
         */
        List<String> allowedJavaPackages) {

    /**
     * Returns an empty permission set (no network, no filesystem, no packages).
     * This is the most restrictive possible configuration.
     *
     * @return empty {@code PermissionSet}
     */
    public static PermissionSet empty() {
        return new PermissionSet(List.of(), List.of(), List.of());
    }

    /**
     * Returns a permission set that grants unrestricted access to all resources.
     * Should only be used in trusted, controlled environments (e.g. dev mode).
     *
     * @return unrestricted {@code PermissionSet}
     */
    public static PermissionSet unrestricted() {
        return new PermissionSet(List.of("*"), List.of("*"), List.of("*"));
    }

    /**
     * Returns {@code true} if the given hostname is allowed by this permission set.
     *
     * @param host hostname or IP address to check
     * @return {@code true} if network access to {@code host} is permitted
     */
    public boolean isNetworkHostAllowed(String host) {
        if (host == null) {
            return false;
        }
        for (String allowed : allowedNetworkHosts) {
            if ("*".equals(allowed) || allowed.equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if access to the given filesystem path is permitted.
     *
     * @param path absolute path to check
     * @return {@code true} if filesystem access to {@code path} is permitted
     */
    public boolean isFilePathAllowed(String path) {
        if (path == null) {
            return false;
        }
        for (String allowed : allowedFilePaths) {
            if ("*".equals(allowed) || path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given Java package prefix is permitted.
     *
     * @param packageName fully-qualified package or class name to check
     * @return {@code true} if access to {@code packageName} is permitted
     */
    public boolean isJavaPackageAllowed(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String allowed : allowedJavaPackages) {
            if ("*".equals(allowed) || packageName.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }
}
