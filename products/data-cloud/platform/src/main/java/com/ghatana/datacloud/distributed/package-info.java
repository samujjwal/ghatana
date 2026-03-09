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
 * Distributed computing support for Data-Cloud.
 *
 * <p>This package provides primitives for distributed Data-Cloud deployments:
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.distributed.ConsistencyLevel} -
 *       Tunable consistency levels (STRONG, QUORUM, EVENTUAL)</li>
 *   <li>{@link com.ghatana.datacloud.distributed.ClusterCoordinator} -
 *       SPI for cluster coordination (etcd, Consul, ZooKeeper)</li>
 *   <li>{@link com.ghatana.datacloud.distributed.NodeInfo} -
 *       Cluster node metadata</li>
 *   <li>{@link com.ghatana.datacloud.distributed.LeaderLease} -
 *       Leader election lease handle</li>
 *   <li>{@link com.ghatana.datacloud.distributed.DistributedLock} -
 *       Cross-node synchronization lock</li>
 * </ul>
 *
 * <h2>Consistency Model</h2>
 * <pre>
 * ┌─────────────────┬─────────────────────┬─────────────────────┐
 * │  Level          │  Description        │  Use Case           │
 * ├─────────────────┼─────────────────────┼─────────────────────┤
 * │  STRONG         │  All replicas ACK   │  Financial txns     │
 * │  QUORUM         │  Majority ACK       │  General workloads  │
 * │  ONE            │  Single replica     │  Read-heavy         │
 * │  EVENTUAL       │  Async replication  │  Analytics, logs    │
 * │  LOCAL          │  No replication     │  Caching            │
 * └─────────────────┴─────────────────────┴─────────────────────┘
 * </pre>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create coordinator
 * ClusterCoordinator coordinator = EtcdCoordinator.create(
 *     ClusterConfig.etcd("etcd:2379"));
 *
 * // Join cluster
 * coordinator.join(NodeInfo.builder()
 *     .nodeId("node-1")
 *     .host("10.0.0.1")
 *     .port(8080)
 *     .build());
 *
 * // Use tunable consistency
 * storagePlugin.put(key, value, ConsistencyLevel.QUORUM);
 * }</pre>
 *
 * @see com.ghatana.datacloud.deployment.DeploymentMode#DISTRIBUTED
 * @see com.ghatana.datacloud.deployment.ClusterConfig
 */
package com.ghatana.datacloud.distributed;
