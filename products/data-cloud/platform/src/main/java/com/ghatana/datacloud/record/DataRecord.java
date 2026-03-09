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
 * Record with a mutable data payload.
 *
 * <p>Extends the base {@link Record} interface to add a data payload
 * that can be updated. This is the foundation for entity records that
 * store field-value pairs.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DataRecord record = ...;
 *
 * // Get data
 * Map<String, Object> data = record.data();
 * String name = (String) data.get("name");
 *
 * // Update data (immutable pattern)
 * DataRecord updated = record.withData(Map.of("name", "New Name"));
 * }</pre>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>Data map is immutable (updates return new instances)</li>
 *   <li>Values can be any serializable type</li>
 *   <li>Empty map is valid (no data)</li>
 * </ul>
 *
 * @see Record
 * @see MetadataRecord
 * @doc.type interface
 * @doc.purpose Record with data payload
 * @doc.layer core
 * @doc.pattern Value Object, Immutable Update
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface DataRecord extends Record {

    /**
     * Returns the data payload as a key-value map.
     *
     * @return immutable map of field names to values (never null)
     */
    Map<String, Object> data();

    /**
     * Returns a copy of this record with updated data.
     *
     * @param data the new data map
     * @return new record with updated data
     */
    DataRecord withData(Map<String, Object> data);

    /**
     * Returns a specific field value from the data.
     *
     * @param field the field name
     * @return the value, or null if not present
     */
    default Object get(String field) {
        return data().get(field);
    }

    /**
     * Returns a specific field value cast to the expected type.
     *
     * @param <T> the expected type
     * @param field the field name
     * @param type the expected class
     * @return the value cast to type, or null if not present
     * @throws ClassCastException if value is not of expected type
     */
    @SuppressWarnings("unchecked")
    default <T> T get(String field, Class<T> type) {
        Object value = data().get(field);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Returns true if the data contains the given field.
     *
     * @param field the field name
     * @return true if field exists
     */
    default boolean has(String field) {
        return data().containsKey(field);
    }

    /**
     * Returns the number of fields in the data.
     *
     * @return field count
     */
    default int fieldCount() {
        return data().size();
    }
}
