/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

/**
 * Plugin capability enumeration (P8).
 *
 * <p>Defines the capabilities that a plugin can provide.
 *
 * @doc.type enum
 * @doc.purpose Plugin capability enumeration
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum PluginCapability {
    STORAGE,
    QUERY,
    STREAMING,
    TRANSFORMATION,
    VALIDATION,
    ENCRYPTION,
    COMPRESSION,
    INDEXING,
    SEARCH,
    AGGREGATION,
    NOTIFICATION,
    AUDIT,
    METRICS,
    TRACING,
    AUTHENTICATION,
    AUTHORIZATION,
    RATE_LIMITING,
    CACHING,
    PROXY,
    ROUTING,
    SERIALIZATION,
    DESERIALIZATION,
    SCHEMA_VALIDATION,
    DATA_MIGRATION,
    BACKUP,
    RESTORE,
    REPLICATION,
    SHARDING,
    PARTITIONING,
    TRANSACTION_MANAGEMENT,
    CONCURRENCY_CONTROL,
    DEADLOCK_DETECTION,
    QUERY_OPTIMIZATION,
    EXECUTION_PLANNING,
    RESOURCE_MANAGEMENT,
    SCHEDULING,
    WORKFLOW_EXECUTION,
    EVENT_PROCESSING,
    MESSAGE_QUEUING,
    PUB_SUB,
    FILE_SYSTEM,
    OBJECT_STORAGE,
    DATABASE_CONNECTOR,
    API_CLIENT,
    WEBHOOK,
    CUSTOM
}
