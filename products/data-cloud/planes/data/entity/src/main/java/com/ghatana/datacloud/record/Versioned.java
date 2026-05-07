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

/**
 * Trait for versioned records supporting optimistic concurrency.
 *
 * <p>Records implementing this trait track version numbers for:
 * <ul>
 *   <li>Optimistic locking (conflict detection)</li>
 *   <li>Change history tracking</li>
 *   <li>Cache invalidation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * interface VersionedEntity extends Record, Versioned {}
 *
 * // Update with version check
 * if (record.version() == expectedVersion) {
 *     VersionedEntity updated = record.incrementVersion();
 *     storage.put(updated);
 * } else {
 *     throw new ConcurrentModificationException();
 * }
 * }</pre>
 *
 * @param <T> self-type for fluent API
 * @see Record
 * @doc.type interface
 * @doc.purpose Versioning trait for optimistic concurrency
 * @doc.layer core
 * @doc.pattern Trait, Mixin
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface Versioned<T extends Versioned<T>> {

    /**
     * Returns the version number.
     *
     * <p>Version starts at 0 and increments on each update.
     *
     * @return current version (>= 0)
     */
    long version();

    /**
     * Returns a copy with incremented version.
     *
     * @return new instance with version + 1
     */
    T incrementVersion();

    /**
     * Returns true if this is the first version.
     *
     * @return true if version == 0
     */
    default boolean isFirstVersion() {
        return version() == 0L;
    }

    /**
     * Checks if this version matches the expected version.
     *
     * @param expected the expected version
     * @return true if versions match
     */
    default boolean matchesVersion(long expected) {
        return version() == expected;
    }
}
