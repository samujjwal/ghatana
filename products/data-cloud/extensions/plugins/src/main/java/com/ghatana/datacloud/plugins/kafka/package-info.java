/**
 * Apache Kafka Streaming Plugin for EventLog.
 *
 * <p>
 * This package provides high-throughput distributed streaming capabilities for
 * EventLog using Apache Kafka. Key features include:
 * <ul>
 * <li><b>Consumer Groups</b>: Coordinated consumption with automatic partition
 * rebalancing</li>
 * <li><b>Exactly-Once Semantics</b>: Transactional delivery guarantees</li>
 * <li><b>High Throughput</b>: Target 500k+ events/sec sustained</li>
 * <li><b>Multi-Tenancy</b>: Topic-based tenant isolation</li>
 * </ul>
 *
 * <p>
 * <b>Architecture</b>:
 * <pre>
 * EventLog → KafkaStreamingPlugin → Kafka Cluster
 *                    ↓
 *              ConsumerGroupManager
 *                    ↓
 *              PartitionAssigner
 * </pre>
 *
 * <p>
 * <b>Topic Naming Convention</b>:
 * <pre>
 * eventlog.{tenant_id}.{stream_name}
 * eventlog.{tenant_id}.{stream_name}.{partition_id}
 * </pre>
 *
 * <p>
 * <b>Security</b>:
 * <ul>
 * <li>SASL/SCRAM authentication</li>
 * <li>SSL/TLS encryption</li>
 * <li>ACL-based authorization</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose Apache Kafka streaming plugin for EventLog
 * @doc.layer plugin
 * @see com.ghatana.datacloud.event.plugins.kafka.KafkaStreamingPlugin
 * @see com.ghatana.datacloud.event.plugins.kafka.KafkaStreamingConfig
 *
 * Canonical home for Data Cloud Kafka streaming/runtime integration types.
 */
package com.ghatana.datacloud.plugins.kafka;
