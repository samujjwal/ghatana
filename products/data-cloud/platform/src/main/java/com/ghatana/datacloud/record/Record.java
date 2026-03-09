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

package com.ghatana.datacloud.record;

import java.util.Map;
import java.util.UUID;

/**
 * Minimal core interface for all Data-Cloud records.
 *
 * <p>This interface defines the absolute minimum contract for a record
 * in Data-Cloud. It is intentionally lightweight (~15 lines) to allow
 * for maximum flexibility and composition.
 *
 * <h2>Design Philosophy</h2>
 * <ul>
 *   <li><b>Minimal Core</b>: Only essential identity attributes</li>
 *   <li><b>Composable</b>: Add behavior via traits (Versioned, Auditable, etc.)</li>
 *   <li><b>Immutable</b>: Records are immutable by default</li>
 *   <li><b>Type-Safe</b>: No dynamic typing in core interface</li>
 * </ul>
 *
 * <h2>Trait Composition</h2>
 * <pre>{@code
 * // Simple record
 * interface MyRecord extends Record {}
 *
 * // Record with versioning
 * interface VersionedRecord extends Record, Versioned {}
 *
 * // Record with full audit trail
 * interface AuditedRecord extends Record, Versioned, Auditable, Timestamped {}
 * }</pre>
 *
 * @see Versioned
 * @see Auditable
 * @see Timestamped
 * @see Identifiable
 * @doc.type interface
 * @doc.purpose Minimal core record interface
 * @doc.layer core
 * @doc.pattern Value Object, Trait Composition
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface Record {

    /**
     * Returns the unique identifier for this record.
     *
     * @return record ID (never null)
     */
    UUID id();

    /**
     * Returns the tenant identifier for multi-tenancy.
     *
     * @return tenant ID (never null)
     */
    String tenantId();

    /**
     * Returns the collection this record belongs to.
     *
     * @return collection name (never null)
     */
    String collectionName();

    /**
     * Returns the record's data payload.
     *
     * @return data as key-value map (never null, may be empty)
     */
    Map<String, Object> data();

    /**
     * Returns the record type.
     *
     * @return record type
     */
    RecordType recordType();

    /**
     * Record type enumeration.
     */
    enum RecordType {
        /** Mutable entity with versioning */
        ENTITY,
        /** Immutable event (append-only) */
        EVENT,
        /** Time-series data point */
        TIMESERIES,
        /** Document (schema-free) */
        DOCUMENT,
        /** Graph node or edge */
        GRAPH
    }
}
