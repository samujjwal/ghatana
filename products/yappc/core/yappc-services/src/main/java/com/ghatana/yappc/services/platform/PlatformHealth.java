/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import java.util.Map;

/**
 * Platform health status.
 *
 * @doc.type record
 * @doc.purpose Represents the health status of platform services
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record PlatformHealth(
    boolean isHealthy,
    String status,
    Map<String, String> components,
    String version
) {}
