/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.testing.testcontainers;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable PostgreSQL Testcontainers configuration.
 *
 * <p>Provides a pre-configured PostgreSQL container for integration tests.
 * Uses the official PostgreSQL image with sensible defaults for testing.</p>
 *
 * @doc.type class
 * @doc.purpose Reusable PostgreSQL Testcontainers configuration
 * @doc.layer platform
 * @doc.pattern Test Utility
 */
public final class PostgresTestContainer {

    private static final String IMAGE_NAME = "postgres:16-alpine";
    private static final String DATABASE_NAME = "testdb";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testpass";

    /**
     * Creates a new PostgreSQL container with default configuration.
     *
     * @return configured PostgreSQL container
     */
    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withDatabaseName(DATABASE_NAME)
                .withUsername(USERNAME)
                .withPassword(PASSWORD);
    }

    /**
     * Creates a new PostgreSQL container with custom database name.
     *
     * @param databaseName the database name
     * @return configured PostgreSQL container
     */
    public static PostgreSQLContainer<?> create(String databaseName) {
        return new PostgreSQLContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withDatabaseName(databaseName)
                .withUsername(USERNAME)
                .withPassword(PASSWORD);
    }

    /**
     * Gets the JDBC URL for a running PostgreSQL container.
     *
     * @param container the running PostgreSQL container
     * @return JDBC URL
     */
    public static String getJdbcUrl(PostgreSQLContainer<?> container) {
        return container.getJdbcUrl();
    }

    /**
     * Gets the JDBC URL for a running PostgreSQL container with specific database.
     *
     * @param container the running PostgreSQL container
     * @param databaseName the database name
     * @return JDBC URL
     */
    public static String getJdbcUrl(PostgreSQLContainer<?> container, String databaseName) {
        return container.getJdbcUrl().replace(DATABASE_NAME, databaseName);
    }

    private PostgresTestContainer() {}
}
