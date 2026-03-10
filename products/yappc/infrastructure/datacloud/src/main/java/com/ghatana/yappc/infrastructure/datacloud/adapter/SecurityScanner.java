/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure Module
 */
package com.ghatana.yappc.infrastructure.datacloud.adapter;

import io.activej.promise.Promise;

import java.nio.file.Path;

/**
 * Contract for static/dynamic security scanning of YAPPC-generated and user project code.
 *
 * <p>Implementations must be stateless and safe to call concurrently.
 * Each scan returns a {@link SecurityReport} that is never {@code null}.
 *
 * @doc.type interface
 * @doc.purpose Contract for security scanning of project source code
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public interface SecurityScanner {

    /**
     * Scans a project directory for security vulnerabilities, insecure patterns, and
     * known CVEs in dependency manifests.
     *
     * @param projectPath root directory of the project to scan
     * @return {@link SecurityReport} containing findings; never {@code null}
     */
    Promise<SecurityReport> scan(Path projectPath);
}
