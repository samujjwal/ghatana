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
 * Embedded Data Cloud Package.
 *
 * <p>Provides embeddable data cloud functionality for in-process
 * integration scenarios where a separate server is not needed.
 *
 * <h2>Primary Use Cases</h2>
 * <ul>
 *   <li><b>AEP Integration</b> - Embedded storage for event processing</li>
 *   <li><b>Unit Testing</b> - Fast in-memory storage for tests</li>
 *   <li><b>Edge Deployment</b> - Lightweight local storage</li>
 *   <li><b>Development</b> - Quick local development without servers</li>
 * </ul>
 *
 * <h2>Core Components</h2>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │                    Embedded Package                            │
 * ├────────────────────────────────────────────────────────────────┤
 * │                                                                │
 * │  {@link com.ghatana.datacloud.embedded.EmbeddableDataCloud}    │
 * │  └── Main interface for embedded data cloud                    │
 * │                                                                │
 * │  {@link com.ghatana.datacloud.embedded.DefaultEmbeddableDataCloud}│
 * │  └── Default in-memory implementation                          │
 * │                                                                │
 * │  EmbeddedStore                                                 │
 * │  └── Key-value storage operations                              │
 * │                                                                │
 * │  EmbeddedQuery                                                 │
 * │  └── Query/filter operations                                   │
 * │                                                                │
 * │  EmbeddedEventStream                                           │
 * │  └── Change notification stream                                │
 * │                                                                │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create in-memory instance
 * EmbeddableDataCloud dc = EmbeddableDataCloud.inMemory();
 *
 * // Start
 * dc.start().whenComplete((v, e) -> {
 *     // Store data
 *     dc.store().put("key", record);
 *
 *     // Query data
 *     dc.query().find(r -> r.tenantId().equals("tenant-1"));
 *
 *     // Subscribe to changes
 *     dc.events().subscribe(event -> {
 *         System.out.println("Change: " + event.type() + " " + event.key());
 *     });
 * });
 *
 * // Shutdown
 * dc.stop();
 * }</pre>
 *
 * <h2>AEP Integration Example</h2>
 * <pre>{@code
 * // In AEP operator
/**
 * Stateful operator.
 *
 * @doc.type class
 * Example usage:
 * <pre>
 * public class StatefulOperator extends UnifiedOperator {
 *     private final EmbeddableDataCloud stateStore;
 *
 *     public StatefulOperator() {
 *         this.stateStore = EmbeddableDataCloud.create()
 *             .withStorage(StorageType.ROCKS_DB)
 *             .withPersistence(true)
 *             .build();
 *     }
 *
 *     public void start() {
 *         stateStore.start();
 *     }
 *
 *     public Promise process(Event event) {
 *         return stateStore.store()
 *             .put(event.id(), toRecord(event))
 *             .map(v -&gt; event);
 *     }
 * }
 * </pre>
 *
 * @see com.ghatana.datacloud.embedded.EmbeddableDataCloud
 * @see com.ghatana.datacloud.deployment.DeploymentMode#EMBEDDED
 *
 * @since 1.0.0
 */
package com.ghatana.datacloud.embedded;
