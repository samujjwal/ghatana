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

package com.ghatana.yappc.core.featurepack;

/**
 * Types of feature packs that can be applied to projects.
 *
 * <p>Week 7 Day 34: Database and API feature pack types.
 *
 * @doc.type enum
 * @doc.purpose Types of feature packs that can be applied to projects.
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum FeaturePackType {

    /**
 * Database integration feature packs (PostgreSQL, MySQL, MongoDB, etc.) */
    DATABASE("database", "Database integration and access layer"),

    /**
 * API framework feature packs (REST, GraphQL, gRPC, etc.) */
    API("api", "API framework and service layer"),

    /**
 * Authentication and authorization feature packs */
    AUTH("auth", "Authentication and authorization"),

    /**
 * Observability feature packs (metrics, tracing, logging) */
    OBSERVABILITY("observability", "Observability and monitoring"),

    /**
 * Message queue and event streaming feature packs */
    MESSAGING("messaging", "Message queues and event streaming"),

    /**
 * Cache integration feature packs */
    CACHE("cache", "Caching layer integration"),

    /**
 * Security feature packs (encryption, secrets, etc.) */
    SECURITY("security", "Security and cryptography"),

    /**
 * Testing utilities and frameworks */
    TESTING("testing", "Testing frameworks and utilities"),

    /**
 * Development tooling and utilities */
    DEVTOOLS("devtools", "Development tools and utilities"),

    /**
 * Container and deployment feature packs */
    DEPLOYMENT("deployment", "Container and deployment configurations");

    private final String key;
    private final String description;

    FeaturePackType(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public static FeaturePackType fromKey(String key) {
        for (FeaturePackType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown feature pack type: " + key);
    }
}
