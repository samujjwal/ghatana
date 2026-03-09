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
 * Hybrid deployment mode configuration for Data-Cloud.
 *
 * <p>This package provides configuration and abstractions for deploying
 * Data-Cloud in three different modes:
 *
 * <ul>
 *   <li><b>{@link com.ghatana.datacloud.deployment.DeploymentMode#EMBEDDED EMBEDDED}</b> -
 *       In-process library mode with zero network overhead. Ideal for
 *       AEP integration, edge deployments, and testing.</li>
 *
 *   <li><b>{@link com.ghatana.datacloud.deployment.DeploymentMode#STANDALONE STANDALONE}</b> -
 *       Single-node deployment with full HTTP/gRPC API. Ideal for
 *       development and small production deployments.</li>
 *
 *   <li><b>{@link com.ghatana.datacloud.deployment.DeploymentMode#DISTRIBUTED DISTRIBUTED}</b> -
 *       Multi-node clustered deployment with coordination, partitioning,
 *       and replication. Ideal for enterprise production.</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Embedded for AEP integration
 * DeploymentConfig config = DeploymentConfig.embeddedForProduction("/data/events");
 *
 * // Standalone for development
 * DeploymentConfig config = DeploymentConfig.standalone();
 *
 * // Distributed for production
 * DeploymentConfig config = DeploymentConfig.distributedWithEtcd(
 *     8080, "etcd-1:2379", "etcd-2:2379", "etcd-3:2379");
 * }</pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.deployment.DeploymentMode} - Deployment mode enum</li>
 *   <li>{@link com.ghatana.datacloud.deployment.DeploymentConfig} - Unified deployment configuration</li>
 *   <li>{@link com.ghatana.datacloud.deployment.EmbeddedConfig} - Embedded mode configuration</li>
 *   <li>{@link com.ghatana.datacloud.deployment.ServerConfig} - HTTP/gRPC server configuration</li>
 *   <li>{@link com.ghatana.datacloud.deployment.ClusterConfig} - Cluster coordination configuration</li>
 * </ul>
 *
 * @see com.ghatana.datacloud.deployment.DeploymentMode
 * @see com.ghatana.datacloud.deployment.DeploymentConfig
 */
package com.ghatana.datacloud.deployment;
