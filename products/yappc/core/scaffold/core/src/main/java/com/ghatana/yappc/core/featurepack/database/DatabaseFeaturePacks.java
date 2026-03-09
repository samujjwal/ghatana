/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.featurepack.database;

import com.ghatana.yappc.core.featurepack.FeaturePackSpec;
import com.ghatana.yappc.core.featurepack.FeaturePackType;
import java.util.List;
import java.util.Map;

/**
 * Database feature pack specifications for cross-language database integration. Provides
 * comprehensive database access patterns across Gradle, Maven, Cargo, and Make.
 *
 * <p>Week 7 Day 34: Database feature packs with cross-build-system support.
 *
 * @doc.type class
 * @doc.purpose Database feature pack specifications for cross-language database integration. Provides
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class DatabaseFeaturePacks {

    private DatabaseFeaturePacks() {
        // Utility class
    }

    /**
 * PostgreSQL feature pack with cross-language support. */
    public static FeaturePackSpec postgresql() {
        return FeaturePackSpec.builder()
                .name("postgresql")
                .version("1.0.0")
                .description(
                        "PostgreSQL database integration with connection pooling and migrations")
                .type(FeaturePackType.DATABASE)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("org.postgresql:postgresql", "42.7.1"),
                                Map.entry("com.zaxxer:HikariCP", "5.1.0"),
                                Map.entry("org.flywaydb:flyway-core", "10.4.1"),
                                // Rust dependencies
                                Map.entry("tokio-postgres", "0.7.10"),
                                Map.entry("deadpool-postgres", "0.12.1"),
                                Map.entry("refinery", "0.8.11"),
                                // C++ dependencies
                                Map.entry("libpq-dev", "latest"),
                                Map.entry("libpqxx-dev", "7.7.4")))
                .devDependencies(
                        Map.of(
                                "org.testcontainers:postgresql", "1.19.3",
                                "testcontainers", "0.15.0"))
                .requiredFeatures(List.of("connection-pooling", "migrations"))
                .optionalFeatures(List.of("async-support", "ssl-config", "monitoring"))
                .configuration(
                        Map.of(
                                "database.host",
                                "localhost",
                                "database.port",
                                5432,
                                "database.name",
                                "{{project.name}}",
                                "database.schema",
                                "public",
                                "pool.maxSize",
                                20,
                                "pool.minIdle",
                                5,
                                "migrations.enabled",
                                true))
                .templateFiles(
                        List.of(
                                "database/postgresql/java/DatabaseConfig.java.hbs",
                                "database/postgresql/java/ConnectionPool.java.hbs",
                                "database/postgresql/rust/database.rs.hbs",
                                "database/postgresql/cpp/database.hpp.hbs",
                                "database/postgresql/migrations/V001__Initial_schema.sql.hbs"))
                .configFiles(List.of("application.properties", "database.toml", "database.conf"))
                .environment(
                        Map.of(
                                "DATABASE_URL",
                                        "postgresql://{{database.host}}:{{database.port}}/{{database.name}}",
                                "DATABASE_POOL_SIZE", "{{pool.maxSize}}"))
                .build();
    }

    /**
 * MySQL feature pack with cross-language support. */
    public static FeaturePackSpec mysql() {
        return FeaturePackSpec.builder()
                .name("mysql")
                .version("1.0.0")
                .description("MySQL database integration with connection pooling and migrations")
                .type(FeaturePackType.DATABASE)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("com.mysql:mysql-connector-j", "8.2.0"),
                                Map.entry("com.zaxxer:HikariCP", "5.1.0"),
                                Map.entry("org.flywaydb:flyway-mysql", "10.4.1"),
                                // Rust dependencies
                                Map.entry("mysql", "24.0.0"),
                                Map.entry("sqlx", "0.7.3"),
                                // C++ dependencies
                                Map.entry("libmysqlclient-dev", "latest"),
                                Map.entry("mysql-connector-cpp-dev", "8.2.0")))
                .devDependencies(
                        Map.of(
                                "org.testcontainers:mysql", "1.19.3",
                                "testcontainers", "0.15.0"))
                .requiredFeatures(List.of("connection-pooling", "migrations"))
                .optionalFeatures(List.of("async-support", "ssl-config", "replication"))
                .configuration(
                        Map.of(
                                "database.host",
                                "localhost",
                                "database.port",
                                3306,
                                "database.name",
                                "{{project.name}}",
                                "pool.maxSize",
                                20,
                                "pool.minIdle",
                                5,
                                "migrations.enabled",
                                true))
                .build();
    }

    /**
 * MongoDB feature pack with cross-language support. */
    public static FeaturePackSpec mongodb() {
        return FeaturePackSpec.builder()
                .name("mongodb")
                .version("1.0.0")
                .description("MongoDB document database integration with ODM/ORM support")
                .type(FeaturePackType.DATABASE)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("org.mongodb:mongodb-driver-sync", "4.11.1"),
                                Map.entry("org.springframework.data:spring-data-mongodb", "4.2.1"),
                                // Rust dependencies
                                Map.entry("mongodb", "2.8.0"),
                                Map.entry("tokio", "1.35.1"),
                                // C++ dependencies
                                Map.entry("libmongocxx-dev", "3.8.1"),
                                Map.entry("libbsoncxx-dev", "3.8.1")))
                .devDependencies(
                        Map.of(
                                "org.testcontainers:mongodb", "1.19.3",
                                "testcontainers", "0.15.0"))
                .requiredFeatures(List.of("connection-pooling", "indexing"))
                .optionalFeatures(List.of("async-support", "aggregation-pipeline", "transactions"))
                .configuration(
                        Map.of(
                                "database.host", "localhost",
                                "database.port", 27017,
                                "database.name", "{{project.name}}",
                                "connection.maxPoolSize", 20,
                                "connection.minPoolSize", 5))
                .build();
    }

    /**
 * Redis feature pack for caching and session storage. */
    public static FeaturePackSpec redis() {
        return FeaturePackSpec.builder()
                .name("redis")
                .version("1.0.0")
                .description("Redis in-memory data structure store for caching and sessions")
                .type(FeaturePackType.CACHE)
                .supportedBuildSystems(List.of("gradle", "maven", "cargo", "make"))
                .supportedLanguages(List.of("java", "rust", "cpp", "typescript"))
                .dependencies(
                        Map.ofEntries(
                                // Java dependencies
                                Map.entry("redis.clients:jedis", "5.1.0"),
                                Map.entry("org.springframework.data:spring-data-redis", "3.2.1"),
                                // Rust dependencies
                                Map.entry("redis", "0.24.0"),
                                Map.entry("tokio", "1.35.1"),
                                // C++ dependencies
                                Map.entry("hiredis-dev", "1.2.0"),
                                Map.entry("redis-plus-plus-dev", "1.3.10")))
                .devDependencies(
                        Map.of(
                                "org.testcontainers:junit-jupiter", "1.19.3",
                                "testcontainers", "0.15.0"))
                .requiredFeatures(List.of("connection-pooling", "serialization"))
                .optionalFeatures(List.of("cluster-support", "pub-sub", "lua-scripts"))
                .configuration(
                        Map.of(
                                "redis.host", "localhost",
                                "redis.port", 6379,
                                "redis.database", 0,
                                "pool.maxActive", 20,
                                "pool.maxIdle", 5,
                                "pool.timeout", 5000))
                .build();
    }

    /**
 * Returns all available database feature packs. */
    public static List<FeaturePackSpec> allDatabasePacks() {
        return List.of(postgresql(), mysql(), mongodb(), redis());
    }
}
