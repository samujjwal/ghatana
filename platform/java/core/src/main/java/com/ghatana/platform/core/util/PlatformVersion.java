/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes platform version metadata generated at build time.
 *
 * <p>Values are read from {@code META-INF/platform.properties} which is
 * populated by Gradle's {@code processResources} task.
 *
 * @doc.type class
 * @doc.purpose Build-time platform/SDK version metadata
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class PlatformVersion {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformVersion.class);
    private static final String PROPERTIES_PATH = "META-INF/platform.properties";
    private static final String FALLBACK = "unknown";

    private static final PlatformVersion INSTANCE = load();

    private final String platformVersion;
    private final String sdkVersion;
    private final String instrumentationVersion;

    private PlatformVersion(String platformVersion, String sdkVersion, String instrumentationVersion) {
        this.platformVersion = platformVersion;
        this.sdkVersion = sdkVersion;
        this.instrumentationVersion = instrumentationVersion;
    }

    private static PlatformVersion load() {
        Properties props = new Properties();
        try (InputStream is = PlatformVersion.class.getClassLoader().getResourceAsStream(PROPERTIES_PATH)) {
            if (is != null) {
                props.load(is);
            } else {
                LOG.warn("Platform properties not found on classpath: {}", PROPERTIES_PATH);
            }
        } catch (IOException e) {
            LOG.warn("Failed to load platform properties: {}", e.getMessage());
        }
        return new PlatformVersion(
                props.getProperty("platform.version", FALLBACK),
                props.getProperty("sdk.version", FALLBACK),
                props.getProperty("instrumentation.version", FALLBACK)
        );
    }

    /** Returns the singleton instance. */
    public static PlatformVersion get() {
        return INSTANCE;
    }

    /** Platform version (e.g. {@code "1.0.0-SNAPSHOT"}). */
    public String platformVersion() {
        return platformVersion;
    }

    /** SDK version aligned with the platform release. */
    public String sdkVersion() {
        return sdkVersion;
    }

    /** Instrumentation / tracer version. */
    public String instrumentationVersion() {
        return instrumentationVersion;
    }
}
