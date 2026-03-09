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

import java.time.Instant;

/**
 * Trait for records with timestamp tracking.
 *
 * <p>Records implementing this trait have:
 * <ul>
 *   <li>Creation timestamp (immutable)</li>
 *   <li>Last update timestamp</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * interface TimestampedEntity extends Record, Timestamped {}
 *
 * // Check if recently modified
 * if (record.updatedAt().isAfter(threshold)) {
 *     // Process recent changes
 * }
 * }</pre>
 *
 * @see Record
 * @doc.type interface
 * @doc.purpose Timestamp tracking trait
 * @doc.layer core
 * @doc.pattern Trait, Mixin
 *
 * @param <T> self-type for fluent API
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface Timestamped<T extends Timestamped<T>> {

    /**
     * Returns when this record was created.
     *
     * @return creation timestamp (never null)
     */
    Instant createdAt();

    /**
     * Returns when this record was last updated.
     *
     * @return update timestamp (never null, equals createdAt for new records)
     */
    Instant updatedAt();

    /**
     * Returns a copy with updated timestamp set to now.
     *
     * @return new instance with updated timestamp
     */
    T touch();

    /**
     * Returns true if this record has been updated since creation.
     *
     * @return true if updatedAt > createdAt
     */
    default boolean hasBeenUpdated() {
        return updatedAt().isAfter(createdAt());
    }

    /**
     * Returns the age of this record (time since creation).
     *
     * @return age in milliseconds
     */
    default long ageMillis() {
        return Instant.now().toEpochMilli() - createdAt().toEpochMilli();
    }

    /**
     * Returns true if this record was created after the given time.
     *
     * @param threshold the time threshold
     * @return true if created after threshold
     */
    default boolean createdAfter(Instant threshold) {
        return createdAt().isAfter(threshold);
    }

    /**
     * Returns true if this record was updated after the given time.
     *
     * @param threshold the time threshold
     * @return true if updated after threshold
     */
    default boolean updatedAfter(Instant threshold) {
        return updatedAt().isAfter(threshold);
    }
}
