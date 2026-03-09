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
 * Concrete record implementations for the Data-Cloud platform.
 *
 * <p>This package contains concrete implementations of the record
 * interfaces defined in the parent package. These implementations
 * provide ready-to-use record types for different use cases.
 *
 * <h2>Available Implementations</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.record.impl.SimpleRecord} -
 *       Minimal record with just identity (id, tenantId, type)</li>
 *   <li>{@link com.ghatana.datacloud.record.impl.FullEntityRecord} -
 *       Full-featured entity with all traits (versioning, auditing, AI)</li>
 *   <li>{@link com.ghatana.datacloud.record.impl.ImmutableEventRecord} -
 *       Immutable event for event sourcing</li>
 * </ul>
 *
 * <h2>Choosing an Implementation</h2>
 * <pre>
 * ┌────────────────────────┬────────────────────────────────────────────┐
 * │  Use Case              │  Implementation                            │
 * ├────────────────────────┼────────────────────────────────────────────┤
 * │  Identity reference    │  SimpleRecord                              │
 * │  Full entity CRUD      │  FullEntityRecord                          │
 * │  Event log entry       │  ImmutableEventRecord                      │
 * │  Wire-format transfer  │  SimpleRecord                              │
 * │  AI-enhanced entity    │  FullEntityRecord (with aiMetadata)        │
 * └────────────────────────┴────────────────────────────────────────────┘
 * </pre>
 *
 * @see com.ghatana.datacloud.record.Record
 * @see com.ghatana.datacloud.record.DataRecord
 */
package com.ghatana.datacloud.record.impl;
