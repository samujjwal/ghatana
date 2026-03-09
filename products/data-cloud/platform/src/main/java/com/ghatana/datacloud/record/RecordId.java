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

import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe record identifier value object.
 *
 * <p>Wraps a UUID to provide type safety and prevent mixing
 * record IDs with other UUID-based identifiers.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generate new ID
 * RecordId id = RecordId.generate();
 *
 * // From string
 * RecordId id = RecordId.of("550e8400-e29b-41d4-a716-446655440000");
 *
 * // From UUID
 * RecordId id = RecordId.of(uuid);
 *
 * // Get underlying value
 * UUID uuid = id.value();
 * }</pre>
 *
 * @see Record
 * @see TenantId
 * @doc.type record
 * @doc.purpose Type-safe record identifier
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record RecordId(UUID value) {

    /**
     * Compact constructor with validation.
     */
    public RecordId {
        Objects.requireNonNull(value, "id cannot be null");
    }

    /**
     * Generates a new random record ID.
     *
     * @return new random RecordId
     */
    public static RecordId generate() {
        return new RecordId(UUID.randomUUID());
    }

    /**
     * Creates a RecordId from a string representation.
     *
     * @param value UUID string (36 characters)
     * @return RecordId wrapping the parsed UUID
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static RecordId of(String value) {
        Objects.requireNonNull(value, "id string cannot be null");
        return new RecordId(UUID.fromString(value));
    }

    /**
     * Creates a RecordId from a UUID.
     *
     * @param value the UUID
     * @return RecordId wrapping the UUID
     */
    public static RecordId of(UUID value) {
        return new RecordId(value);
    }

    /**
     * Returns the string representation of this record ID.
     *
     * @return UUID string representation
     */
    @Override
    public String toString() {
        return value.toString();
    }

    /**
     * Returns the most significant bits of the UUID.
     *
     * @return most significant bits
     */
    public long getMostSignificantBits() {
        return value.getMostSignificantBits();
    }

    /**
     * Returns the least significant bits of the UUID.
     *
     * @return least significant bits
     */
    public long getLeastSignificantBits() {
        return value.getLeastSignificantBits();
    }
}
