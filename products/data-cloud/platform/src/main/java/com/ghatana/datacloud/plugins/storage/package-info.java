/**
 * Apache Iceberg L2 (COOL tier) Storage Plugin for EventCloud.
 *
 * <p><b>Purpose</b><br>
 * Provides columnar analytics storage for events using Apache Iceberg table format.
 * Designed for long-term retention (30+ days), time-travel queries, and efficient
 * analytics workloads.
 *
 * <p><b>Storage Tier</b><br>
 * L2 (COOL) tier - Analytics storage for historical events:
 * <pre>
 * L0 (Hot)   → Redis/Memory      → Real-time reads (&lt;10ms)
 * L1 (Warm)  → PostgreSQL        → Recent history (days)
 * L2 (Cool)  → Iceberg/Parquet   → Analytics (months)  ← THIS PLUGIN
 * L4 (Cold)  → S3/Glacier        → Archive (years)
 * </pre>
 *
 * <p><b>Features</b><br>
 * <ul>
 *   <li><b>Columnar Storage</b>: Parquet files with predicate pushdown</li>
 *   <li><b>Time Travel</b>: Query historical snapshots by timestamp</li>
 *   <li><b>Schema Evolution</b>: Add/rename columns without rewriting</li>
 *   <li><b>Partitioning</b>: By tenant, stream, detection_date for pruning</li>
 *   <li><b>Compaction</b>: Automatic small file consolidation</li>
 *   <li><b>ACID Transactions</b>: Atomic commits, concurrent readers</li>
 * </ul>
 *
 * <p><b>Supported Catalogs</b><br>
 * <ul>
 *   <li><b>Hadoop</b>: Local filesystem or HDFS (development)</li>
 *   <li><b>AWS Glue</b>: Managed catalog for AWS (production)</li>
 *   <li><b>Hive Metastore</b>: On-premises Hive (self-hosted)</li>
 *   <li><b>Nessie</b>: Git-like versioned catalog (advanced)</li>
 * </ul>
 *
 * <p><b>File Formats</b><br>
 * <ul>
 *   <li><b>Parquet</b>: Default, best for analytics (recommended)</li>
 *   <li><b>ORC</b>: Alternative columnar format</li>
 *   <li><b>Avro</b>: Row-based, for streaming ingestion</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Configuration for S3-based Iceberg
 * IcebergStorageConfig config = IcebergStorageConfig.builder()
 *     .catalogType(CatalogType.HADOOP)
 *     .warehousePath("s3://eventcloud-lake/warehouse")
 *     .fileFormat(FileFormat.PARQUET)
 *     .compressionCodec("zstd")
 *     .build();
 * 
 * // Create and initialize plugin
 * CoolTierStoragePlugin plugin = new CoolTierStoragePlugin(config);
 * plugin.initialize(context);
 * plugin.start();
 * 
 * // Migrate events from L1 (Postgres) to L2 (Iceberg)
 * List<Event> eventsToMigrate = postgresPlugin.readByTimeRange(...);
 * plugin.appendBatch(eventsToMigrate);
 * 
 * // Time-travel query - read events as of yesterday
 * Instant snapshotTime = Instant.now().minus(Duration.ofDays(1));
 * List<Event> historicalEvents = plugin.readAtSnapshot(tenantId, streamName, snapshotTime);
 * }</pre>
 *
 * <p><b>Performance</b><br>
 * <table border="1">
 *   <tr><th>Operation</th><th>Latency</th><th>Throughput</th></tr>
 *   <tr><td>Batch Append</td><td>100-500ms</td><td>50k events/batch</td></tr>
 *   <tr><td>Time Range Query</td><td>500ms-5s</td><td>1M+ events</td></tr>
 *   <tr><td>Point Query (by ID)</td><td>100-500ms</td><td>N/A (use L1)</td></tr>
 * </table>
 *
 * <p><b>Thread Safety</b><br>
 * All methods are async (Promise-based) and safe for concurrent calls.
 * Iceberg provides MVCC for concurrent reads during writes.
 *
 * @see com.ghatana.datacloud.event.plugins.iceberg.CoolTierStoragePlugin
 * @see com.ghatana.datacloud.event.plugins.iceberg.IcebergTableManager
 * @see com.ghatana.datacloud.event.plugins.iceberg.IcebergStorageConfig
 * @see com.ghatana.datacloud.event.spi.StoragePlugin
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.ghatana.datacloud.plugins.iceberg;
