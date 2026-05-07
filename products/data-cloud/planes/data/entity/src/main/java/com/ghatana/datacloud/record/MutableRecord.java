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
 * Marker interface for mutable records.
 *
 * <p>Records implementing this interface can be updated after creation.
 * This includes entity records, configuration records, and other
 * modifiable data.
 *
 * <h2>Characteristics</h2>
 * <ul>
 *   <li>Can be updated via PUT/PATCH operations</li>
 *   <li>Supports versioning for optimistic concurrency</li>
 *   <li>Tracks modification timestamps</li>
 *   <li>May have audit trail for changes</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * if (record instanceof MutableRecord mutable) {
 *     // Allow update operations
 *     storage.update(mutable.id(), patch);
 * } else {
 *     throw new UnsupportedOperationException("Record is immutable");
 * }
 * }</pre>
 *
 * @see ImmutableRecord
 * @see Record
 * @doc.type interface
 * @doc.purpose Marker for mutable records
 * @doc.layer core
 * @doc.pattern Marker Interface
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface MutableRecord extends Record {
    // Marker interface - allows mutation operations
}
