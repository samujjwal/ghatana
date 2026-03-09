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

/**
 * Lightweight Record Package.
 *
 * <p>This package provides minimal, composable record interfaces
 * following the lightweight core design principle. Records are
 * designed to be as small as possible with capabilities added
 * through trait interfaces.
 *
 * <h2>Core Interface</h2>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │                     Record Interface                           │
 * ├────────────────────────────────────────────────────────────────┤
 * │  id()       → Unique identifier                                │
 * │  tenantId() → Multi-tenancy support                            │
 * │  data()     → Flexible payload                                 │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Composable Traits</h2>
 * <table border="1">
 *   <tr><th>Trait</th><th>Purpose</th><th>Methods</th></tr>
 *   <tr>
 *     <td>{@link com.ghatana.datacloud.record.Versioned}</td>
 *     <td>Optimistic concurrency</td>
 *     <td>version(), incrementVersion()</td>
 *   </tr>
 *   <tr>
 *     <td>{@link com.ghatana.datacloud.record.Timestamped}</td>
 *     <td>Temporal tracking</td>
 *     <td>createdAt(), updatedAt(), touch()</td>
 *   </tr>
 *   <tr>
 *     <td>{@link com.ghatana.datacloud.record.Auditable}</td>
 *     <td>Audit trail</td>
 *     <td>createdBy(), modifiedBy(), recordModification()</td>
 *   </tr>
 *   <tr>
 *     <td>{@link com.ghatana.datacloud.record.HasMetadata}</td>
 *     <td>Extensible metadata</td>
 *     <td>metadata(), getMetadata(), withMetadata()</td>
 *   </tr>
 * </table>
 *
 * <h2>Composition Pattern</h2>
 * <pre>{@code
 * // Minimal record - just the core
/**
 * Simple record.
 *
 * @doc.type interface
 * @doc.purpose Simple record
 * @doc.layer platform
 * Example interfaces:
 * <pre>
 * public interface SimpleRecord extends Record {}
 *
 * // Full-featured record - compose traits
 * public interface RichRecord extends Record, Versioned, Timestamped, Auditable, HasMetadata {}
 *
 * // Domain-specific record
 * public interface EventRecord extends Record, Timestamped {
 *     String eventType();
 *     String source();
 * }
 * </pre>
 *
 * <h2>Design Rationale</h2>
 * <ul>
 *   <li><b>Minimal Core</b> - Base interface is ~15 lines</li>
 *   <li><b>Pay for What You Use</b> - Only mix in needed traits</li>
 *   <li><b>Type Safety</b> - Self-types ensure fluent API works</li>
 *   <li><b>Immutability Friendly</b> - Methods return new instances</li>
 * </ul>
 *
 * <h2>Value Objects</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.record.RecordId} - Type-safe record identifier</li>
 *   <li>{@link com.ghatana.datacloud.record.TenantId} - Type-safe tenant identifier</li>
 * </ul>
 *
 * <h2>Extended Interfaces</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.record.DataRecord} - Record with data payload</li>
 *   <li>{@link com.ghatana.datacloud.record.MetadataRecord} - Record with metadata</li>
 *   <li>{@link com.ghatana.datacloud.record.MutableRecord} - Marker for mutable records</li>
 *   <li>{@link com.ghatana.datacloud.record.ImmutableRecord} - Marker for immutable records</li>
 *   <li>{@link com.ghatana.datacloud.record.Schematized} - Collection/schema binding</li>
 *   <li>{@link com.ghatana.datacloud.record.AIEnhanced} - AI-generated metadata</li>
 * </ul>
 *
 * <h2>Concrete Implementations</h2>
 * <p>See {@link com.ghatana.datacloud.record.impl} package for ready-to-use implementations:
 * <ul>
 *   <li>{@link com.ghatana.datacloud.record.impl.SimpleRecord} - Minimal identity-only record</li>
 *   <li>{@link com.ghatana.datacloud.record.impl.FullEntityRecord} - Full-featured entity</li>
 *   <li>{@link com.ghatana.datacloud.record.impl.ImmutableEventRecord} - Event sourcing record</li>
 * </ul>
 *
 * <h2>Relationship to DataRecord</h2>
 * <p>The existing {@code DataRecord} JPA entity can implement these
 * traits for gradual migration. New code should prefer these
 * lightweight interfaces.
 *
 * @see com.ghatana.datacloud.record.Record
 * @see com.ghatana.datacloud.record.RecordId
 * @see com.ghatana.datacloud.record.TenantId
 * @see com.ghatana.datacloud.record.Versioned
 * @see com.ghatana.datacloud.record.Timestamped
 * @see com.ghatana.datacloud.record.Auditable
 * @see com.ghatana.datacloud.record.HasMetadata
 * @see com.ghatana.datacloud.record.Schematized
 * @see com.ghatana.datacloud.record.AIEnhanced
 *
 * @since 1.0.0
 */
package com.ghatana.datacloud.record;
