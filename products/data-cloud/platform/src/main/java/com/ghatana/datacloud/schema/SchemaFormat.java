/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Schema format enumeration.
 */
package com.ghatana.datacloud.schema;

/**
 * Supported schema formats for event and entity schemas.
 */
public enum SchemaFormat {
    /** JSON Schema (Draft 2020-12). */
    JSON_SCHEMA,
    /** Apache Avro schema. */
    AVRO,
    /** Protocol Buffers schema. */
    PROTOBUF
}
