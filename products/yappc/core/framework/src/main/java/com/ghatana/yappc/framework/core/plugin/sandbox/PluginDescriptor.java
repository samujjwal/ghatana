/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

import java.net.URL;
import java.util.List;

/**
 * Stable, versioned blueprint describing a plugin's identity, classpath, and
 * permission requirements.
 *
 * <p>A {@code PluginDescriptor} is resolved once at registration time and is
 * treated as immutable for the lifetime of the loaded plugin instance.
 *
 * @doc.type record
 * @doc.purpose Plugin identity and permission specification
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginDescriptor(
        /** Stable, unique plugin identifier. E.g. {@code "com.acme.my-plugin"}. */
        String id,

        /** SemVer version string. E.g. {@code "1.2.3"}. */
        String version,

        /**
         * Minimum platform version this plugin requires (inclusive).
         * Compared by {@link IsolatingPluginSandbox} before loading.
         */
        String minPlatformVersion,

        /**
         * Maximum platform version this plugin supports (inclusive).
         * {@code null} or {@code "*"} means no upper bound.
         */
        String maxPlatformVersion,

        /** Fully-qualified class name of the plugin entry point. */
        String mainClass,

        /** JAR / class-file URLs making up the plugin's private classpath. */
        List<URL> classpath,

        /** Security permissions granted to this plugin at runtime. */
        PermissionSet permissions) {

    /**
     * Creates a descriptor with an empty permission set and no upper version bound.
     *
     * @param id                  plugin id
     * @param version             SemVer version
     * @param minPlatformVersion  minimum platform version required
     * @param mainClass           entry-point class name
     * @param classpath           plugin JARs / URLs
     * @return descriptor with {@link PermissionSet#empty()} and no upper version bound
     */
    public static PluginDescriptor restrictedOf(
            String id,
            String version,
            String minPlatformVersion,
            String mainClass,
            List<URL> classpath) {
        return new PluginDescriptor(id, version, minPlatformVersion, null, mainClass, classpath, PermissionSet.empty());
    }

    /** Returns a compact, human-readable identifier for log messages. */
    public String logId() {
        return id + "@" + version;
    }
}
