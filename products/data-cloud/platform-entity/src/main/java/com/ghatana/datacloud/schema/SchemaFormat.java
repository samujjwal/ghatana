/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Schema format enumeration.
 */
package com.ghatana.datacloud.schema;

/**
 * Component for SchemaFormat
 *
 * @doc.type enum
 * @doc.purpose Component for SchemaFormat
 * @doc.layer product
 * @doc.pattern Service
 */
public enum SchemaFormat {
    /** JSON Schema (Draft 2020-12). */
    JSON_SCHEMA,
    /** Apache Avro schema. */
    AVRO,
    /** Protocol Buffers schema. */
    PROTOBUF
}
