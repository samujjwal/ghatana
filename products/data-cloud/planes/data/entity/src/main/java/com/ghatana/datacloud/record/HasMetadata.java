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
 * Trait for records with metadata (secondary attributes).
 *
 * <p>Records implementing this trait have:
 * <ul>
 *   <li>Type-specific metadata separate from data payload</li>
 *   <li>Extensible attributes without schema changes</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * interface MetadataRecord extends Record, HasMetadata {}
 *
 * // Access metadata
 * String source = record.getMetadata("source", String.class);
 * Integer priority = record.getMetadata("priority", Integer.class);
 * }</pre>
 *
 * @see Record
 * @doc.type interface
 * @doc.purpose Metadata trait for extensible attributes
 * @doc.layer core
 * @doc.pattern Trait, Mixin
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface HasMetadata {

    /**
     * Returns the record's metadata.
     *
     * @return metadata as key-value map (never null, may be empty)
     */
    Map<String, Object> metadata();

    /**
     * Gets a metadata value by key.
     *
     * @param key the metadata key
     * @return the value, or null if not present
     */
    default Object getMetadata(String key) {
        return metadata().get(key);
    }

    /**
     * Gets a typed metadata value by key.
     *
     * @param key the metadata key
     * @param type the expected type
     * @param <T> the value type
     * @return the typed value, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    default <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata().get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Gets a metadata value with default.
     *
     * @param key the metadata key
     * @param defaultValue the default if not present
     * @param <T> the value type
     * @return the value, or default if not present
     */
    @SuppressWarnings("unchecked")
    default <T> T getMetadataOrDefault(String key, T defaultValue) {
        Object value = metadata().get(key);
        if (value != null && defaultValue.getClass().isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }

    /**
     * Returns true if this record has metadata.
     *
     * @return true if metadata is not empty
     */
    default boolean hasMetadata() {
        return !metadata().isEmpty();
    }

    /**
     * Returns true if this record has a specific metadata key.
     *
     * @param key the metadata key
     * @return true if key is present
     */
    default boolean hasMetadataKey(String key) {
        return metadata().containsKey(key);
    }
}
