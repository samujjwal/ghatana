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

import java.util.Optional;

/**
 * Trait for records with schema/collection binding.
 *
 * <p>Records implementing this trait are associated with a named
 * collection and optionally a schema version. This enables:
 * <ul>
 *   <li>Schema validation on write</li>
 *   <li>Schema evolution tracking</li>
 *   <li>Collection-based access control</li>
 *   <li>Type-safe deserialization</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * interface OrderRecord extends Record, Schematized {}
 *
 * // Check schema compatibility
 * if (record.schemaVersion().isPresent()) {
 *     String version = record.schemaVersion().get();
 *     if (!schema.isCompatible(version)) {
 *         throw new SchemaIncompatibleException(version);
 *     }
 * }
 *
 * // Route to collection
 * Collection collection = registry.get(record.collectionName());
 * }</pre>
 *
 * @see Record
 * @see Versioned
 * @doc.type interface
 * @doc.purpose Schema/collection binding trait
 * @doc.layer core
 * @doc.pattern Trait, Mixin
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface Schematized {

    /**
     * Returns the name of the collection this record belongs to.
     *
     * @return collection name (never null)
     */
    String collectionName();

    /**
     * Returns the schema version this record conforms to.
     *
     * <p>Schema versions follow semantic versioning (e.g., "1.0.0", "2.1.0").
     * Returns empty if the record predates schema versioning.
     *
     * @return schema version, or empty if unversioned
     */
    Optional<String> schemaVersion();

    /**
     * Returns true if this record has an explicit schema version.
     *
     * @return true if schema version is present
     */
    default boolean hasSchemaVersion() {
        return schemaVersion().isPresent();
    }

    /**
     * Returns the schema version as a string, or null if not present.
     *
     * @return schema version string or null
     */
    default String schemaVersionOrNull() {
        return schemaVersion().orElse(null);
    }
}
