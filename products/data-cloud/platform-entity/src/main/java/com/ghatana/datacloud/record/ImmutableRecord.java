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
 * Marker interface for immutable records.
 *
 * <p>Records implementing this interface cannot be modified after creation.
 * This is the foundation for event sourcing and append-only data structures.
 *
 * <h2>Characteristics</h2>
 * <ul>
 *   <li>Cannot be updated or deleted</li>
 *   <li>Append-only storage model</li>
 *   <li>Perfect for event logs and audit trails</li>
 *   <li>Enables time-travel queries</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * if (record instanceof ImmutableRecord) {
 *     // Reject update operations
 *     throw new UnsupportedOperationException("Cannot modify immutable record");
 * }
 *
 * // Type-safe operations
 * void appendEvent(ImmutableRecord event) {
 *     // Guaranteed to be immutable
 *     eventLog.append(event);
 * }
 * }</pre>
 *
 * @see MutableRecord
 * @see Record
 * @doc.type interface
 * @doc.purpose Marker for immutable records
 * @doc.layer core
 * @doc.pattern Marker Interface
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface ImmutableRecord extends Record {
    // Marker interface - no mutation operations allowed
}
