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

/**
 * Record with additional metadata beyond the core data.
 *
 * <p>Metadata contains secondary information about the record that
 * is not part of the main data payload. Examples include:
 * <ul>
 *   <li>Source system identifiers</li>
 *   <li>Processing timestamps</li>
 *   <li>Quality scores</li>
 *   <li>Lineage information</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MetadataRecord record = ...;
 *
 * // Get metadata
 * Map<String, Object> meta = record.metadata();
 * String source = (String) meta.get("source");
 *
 * // Update metadata (immutable pattern)
 * MetadataRecord updated = record.withMetadata(Map.of("processed", true));
 * }</pre>
 *
 * @see Record
 * @see DataRecord
 * @see HasMetadata
 * @doc.type interface
 * @doc.purpose Record with metadata payload
 * @doc.layer core
 * @doc.pattern Value Object, Immutable Update
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface MetadataRecord extends Record {

    /**
     * Returns the metadata as a key-value map.
     *
     * @return immutable map of metadata keys to values (never null)
     */
    Map<String, Object> metadata();

    /**
     * Returns a copy of this record with updated metadata.
     *
     * @param metadata the new metadata map
     * @return new record with updated metadata
     */
    MetadataRecord withMetadata(Map<String, Object> metadata);

    /**
     * Returns a specific metadata value.
     *
     * @param key the metadata key
     * @return the value, or null if not present
     */
    default Object getMeta(String key) {
        return metadata().get(key);
    }

    /**
     * Returns a specific metadata value cast to the expected type.
     *
     * @param <T> the expected type
     * @param key the metadata key
     * @param type the expected class
     * @return the value cast to type, or null if not present
     */
    @SuppressWarnings("unchecked")
    default <T> T getMeta(String key, Class<T> type) {
        Object value = metadata().get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Returns true if metadata contains the given key.
     *
     * @param key the metadata key
     * @return true if key exists
     */
    default boolean hasMeta(String key) {
        return metadata().containsKey(key);
    }
}
