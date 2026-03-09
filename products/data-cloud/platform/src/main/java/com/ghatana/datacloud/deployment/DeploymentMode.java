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

package com.ghatana.datacloud.deployment;

/**
 * Deployment mode enumeration for hybrid architecture.
 *
 * <p>Data-Cloud supports three deployment modes to accommodate different
 * operational requirements:
 *
 * <ul>
 *   <li><b>EMBEDDED</b> - In-process library mode with direct method invocation.
 *       No HTTP server, zero network latency. Ideal for AEP integration,
 *       edge/IoT deployments, and testing.</li>
 *   <li><b>STANDALONE</b> - Single-node deployment with full HTTP/gRPC API.
 *       No clustering, simple operations. Ideal for development and
 *       small production deployments.</li>
 *   <li><b>DISTRIBUTED</b> - Multi-node clustered deployment with coordination,
 *       partitioning, and replication. Ideal for enterprise production
 *       requiring high availability and horizontal scaling.</li>
 * </ul>
 *
 * <h2>Mode Selection Guide</h2>
 * <pre>
 * ┌─────────────────┬─────────────────┬─────────────────┐
 * │    EMBEDDED     │   STANDALONE    │   DISTRIBUTED   │
 * ├─────────────────┼─────────────────┼─────────────────┤
 * │ • Same JVM      │ • Single node   │ • Multi-node    │
 * │ • No HTTP API   │ • HTTP API      │ • HTTP API      │
 * │ • Zero latency  │ • Full features │ • HA + Scale    │
 * │ • AEP, edge     │ • Dev, small    │ • Production    │
 * └─────────────────┴─────────────────┴─────────────────┘
 * </pre>
 *
 * @see DeploymentConfig
 * @see EmbeddedConfig
 * @see ClusterConfig
 * @doc.type enum
 * @doc.purpose Defines the three deployment modes
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public enum DeploymentMode {

    /**
     * In-process library mode.
     *
     * <p>No HTTP server, direct method invocation.
     * Data-Cloud runs embedded within host application (e.g., AEP).
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>AEP with embedded EventCloud (same JVM)</li>
     *   <li>Edge/IoT deployments with local storage</li>
     *   <li>Unit and integration testing</li>
     *   <li>Single-node processing applications</li>
     * </ul>
     *
     * <p><b>Storage Options:</b> IN_MEMORY, ROCKS_DB, SQLITE, H2
     */
    EMBEDDED,

    /**
     * Single-node deployment with full HTTP/gRPC API.
     *
     * <p>Suitable for development, testing, and small deployments.
     * No clustering, simple operations.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Local development</li>
     *   <li>Small team deployments</li>
     *   <li>Simple microservice architecture</li>
     * </ul>
     *
     * <p><b>Storage Options:</b> Any StoragePlugin (PostgreSQL, Redis, etc.)
     */
    STANDALONE,

    /**
     * Multi-node clustered deployment.
     *
     * <p>Coordination, partitioning, and replication enabled.
     * Provides horizontal scaling, fault tolerance, and high availability.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Enterprise production</li>
     *   <li>High-availability requirements</li>
     *   <li>Multi-tenant platforms</li>
     *   <li>Geo-distributed deployments</li>
     * </ul>
     *
     * <p><b>Requires:</b> ClusterCoordinator (etcd, Consul, or ZooKeeper)
     */
    DISTRIBUTED;

    /**
     * Returns true if this mode requires an HTTP server.
     *
     * @return true for STANDALONE and DISTRIBUTED modes
     */
    public boolean requiresServer() {
        return this != EMBEDDED;
    }

    /**
     * Returns true if this mode requires cluster coordination.
     *
     * @return true only for DISTRIBUTED mode
     */
    public boolean requiresCluster() {
        return this == DISTRIBUTED;
    }

    /**
     * Returns true if this mode supports horizontal scaling.
     *
     * @return true only for DISTRIBUTED mode
     */
    public boolean supportsHorizontalScale() {
        return this == DISTRIBUTED;
    }

    /**
     * Returns true if this mode runs in-process without network.
     *
     * @return true only for EMBEDDED mode
     */
    public boolean isInProcess() {
        return this == EMBEDDED;
    }

    /**
     * Returns true if this mode supports fault tolerance through replication.
     *
     * @return true only for DISTRIBUTED mode
     */
    public boolean supportsFaultTolerance() {
        return this == DISTRIBUTED;
    }
}
