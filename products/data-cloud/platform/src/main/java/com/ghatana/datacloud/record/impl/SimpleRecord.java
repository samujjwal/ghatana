/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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

package com.ghatana.datacloud.record.impl;

import com.ghatana.datacloud.record.Record;
import com.ghatana.datacloud.record.Record.RecordType;
import com.ghatana.datacloud.record.RecordId;
import com.ghatana.platform.domain.auth.TenantId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal record implementation with just identity.
 *
 * <p>This is the lightest possible concrete record implementation.
 * It contains only the essential identity fields and no additional
 * features like versioning, auditing, or metadata.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Lightweight references</li>
 *   <li>Identity-only lookups</li>
 *   <li>Minimal memory footprint</li>
 *   <li>Wire format efficiency</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create new entity record
 * SimpleRecord record = SimpleRecord.entity(TenantId.of("acme"));
 *
 * // Create new event record
 * SimpleRecord event = SimpleRecord.event(TenantId.of("acme"));
 *
 * // From existing ID
 * SimpleRecord ref = SimpleRecord.of(RecordId.of(uuid), tenantId, RecordType.ENTITY);
 * }</pre>
 *
 * @see Record
 * @see FullEntityRecord
 * @doc.type record
 * @doc.purpose Minimal record implementation
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record SimpleRecord(
        RecordId recordId,
        TenantId tenantIdValue,
        String collectionName,
        RecordType recordType
) implements Record {

    /**
     * Compact constructor with validation.
     */
    public SimpleRecord {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(tenantIdValue, "tenantId cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");
        Objects.requireNonNull(recordType, "recordType cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════
    // Record interface implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public UUID id() {
        return recordId.value();
    }

    @Override
    public String tenantId() {
        return tenantIdValue.value();
    }

    @Override
    public Map<String, Object> data() {
        return Map.of(); // No data payload
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a new entity record with generated ID.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return new entity record
     */
    public static SimpleRecord entity(TenantId tenantId, String collectionName) {
        return new SimpleRecord(
                RecordId.generate(),
                tenantId,
                collectionName,
                RecordType.ENTITY
        );
    }

    /**
     * Creates a new event record with generated ID.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return new event record
     */
    public static SimpleRecord event(TenantId tenantId, String collectionName) {
        return new SimpleRecord(
                RecordId.generate(),
                tenantId,
                collectionName,
                RecordType.EVENT
        );
    }

    /**
     * Creates a new document record with generated ID.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return new document record
     */
    public static SimpleRecord document(TenantId tenantId, String collectionName) {
        return new SimpleRecord(
                RecordId.generate(),
                tenantId,
                collectionName,
                RecordType.DOCUMENT
        );
    }

    /**
     * Creates a new graph record with generated ID.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return new graph record
     */
    public static SimpleRecord graph(TenantId tenantId, String collectionName) {
        return new SimpleRecord(
                RecordId.generate(),
                tenantId,
                collectionName,
                RecordType.GRAPH
        );
    }

    /**
     * Creates a record from explicit values.
     *
     * @param id the record ID
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param type the record type
     * @return new record
     */
    public static SimpleRecord of(
            RecordId id,
            TenantId tenantId,
            String collectionName,
            RecordType type
    ) {
        return new SimpleRecord(id, tenantId, collectionName, type);
    }

    /**
     * Creates a record from string values (convenience).
     *
     * @param id the record ID as string
     * @param tenantId the tenant ID as string
     * @param collectionName the collection name
     * @param type the record type
     * @return new record
     */
    public static SimpleRecord of(
            String id,
            String tenantId,
            String collectionName,
            RecordType type
    ) {
        return new SimpleRecord(
                RecordId.of(id),
                TenantId.of(tenantId),
                collectionName,
                type
        );
    }
}
