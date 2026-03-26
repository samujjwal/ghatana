package com.ghatana.datacloud.plugins.iceberg;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the Apache Iceberg L2 (COOL tier) storage plugin.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates all configuration settings for connecting to Iceberg catalogs,
 * managing tables, and controlling file format and compression settings.
 *
 * <p><b>Catalog Types</b><br>
 * <ul>
 *   <li><b>HADOOP</b>: Uses filesystem (local/HDFS/S3) for metadata. Good for dev/test.</li>
 *   <li><b>HIVE</b>: Uses Hive Metastore. Good for on-premises deployments.</li>
 *   <li><b>GLUE</b>: Uses AWS Glue Data Catalog. Recommended for AWS production.</li>
 *   <li><b>NESSIE</b>: Uses Nessie for Git-like version control. Advanced use cases.</li>
 * </ul>
 *
 * <p><b>File Formats</b><br>
 * <ul>
 *   <li><b>PARQUET</b>: Columnar, best for analytics. Default and recommended.</li>
 *   <li><b>ORC</b>: Alternative columnar format with built-in indexes.</li>
 *   <li><b>AVRO</b>: Row-based, better for streaming but less efficient for analytics.</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // S3 + Hadoop catalog (development)
 * IcebergStorageConfig devConfig = IcebergStorageConfig.builder()
 *     .catalogType(CatalogType.HADOOP)
 *     .warehousePath("s3://eventcloud-dev/warehouse")
 *     .fileFormat(FileFormat.PARQUET)
 *     .compressionCodec("snappy")
 *     .build();
 * 
 * // S3 + AWS Glue (production)
 * IcebergStorageConfig prodConfig = IcebergStorageConfig.builder()
 *     .catalogType(CatalogType.GLUE)
 *     .warehousePath("s3://eventcloud-prod/warehouse")
 *     .awsRegion("us-east-1")
 *     .fileFormat(FileFormat.PARQUET)
 *     .compressionCodec("zstd")
 *     .targetFileSizeBytes(128 * 1024 * 1024) // 128MB
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration for Iceberg L2 storage plugin
 * @doc.layer plugin
 * @doc.pattern ValueObject, Builder
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class IcebergStorageConfig {

    // ==================== Catalog Configuration ====================

    /**
     * Type of Iceberg catalog to use.
     */
    @Builder.Default
    private final CatalogType catalogType = CatalogType.HADOOP;

    /**
     * Name of the catalog (for multi-catalog setups).
     */
    @Builder.Default
    private final String catalogName = "eventcloud";

    /**
     * Path to the data warehouse (S3/HDFS/local).
     * Examples:
     * - s3://bucket/warehouse
     * - hdfs://namenode:8020/warehouse
     * - /tmp/iceberg-warehouse (local)
     */
    @Builder.Default
    private final String warehousePath = "/tmp/eventcloud-iceberg-warehouse";

    /**
     * Hive Metastore URI (for HIVE catalog type).
     */
    private final String hiveMetastoreUri;

    /**
     * REST catalog base URI (for REST catalog type).
     * Example: {@code http://catalog-service:8181}
     */
    private final String restCatalogUri;

    /**
     * Nessie server URI (for NESSIE catalog type).
     * Nessie exposes an Iceberg REST catalog endpoint at {@code {uri}/iceberg}.
     * Example: {@code http://nessie:19120}
     */
    private final String nessieCatalogUri;

    /**
     * Nessie branch to use for all table operations (for NESSIE catalog type).
     * Defaults to {@code main}.
     */
    @Builder.Default
    private final String nessieBranch = "main";

    /**
     * AWS Region (for GLUE catalog and S3).
     */
    @Builder.Default
    private final String awsRegion = "us-east-1";

    /**
     * AWS S3 endpoint override (for MinIO, LocalStack).
     */
    private final String s3Endpoint;

    /**
     * Enable S3 path-style access (required for MinIO).
     */
    @Builder.Default
    private final boolean s3PathStyleAccess = false;

    // ==================== Table Configuration ====================

    /**
     * Database/namespace within the catalog.
     */
    @Builder.Default
    private final String databaseName = "eventcloud_events";

    /**
     * Prefix for event tables (per tenant/stream).
     */
    @Builder.Default
    private final String tablePrefix = "events_";

    // ==================== File Format Configuration ====================

    /**
     * File format for data files.
     */
    @Builder.Default
    private final FileFormat fileFormat = FileFormat.PARQUET;

    /**
     * Compression codec for data files.
     * For Parquet: snappy, gzip, zstd, lz4, uncompressed
     * For ORC: zlib, snappy, lzo, zstd
     */
    @Builder.Default
    private final String compressionCodec = "zstd";

    /**
     * Target size for data files in bytes (default 128MB).
     * Larger files = fewer files but longer write times.
     */
    @Builder.Default
    private final long targetFileSizeBytes = 128 * 1024 * 1024L; // 128MB

    /**
     * Split size for reading files (default 128MB).
     */
    @Builder.Default
    private final long splitSizeBytes = 128 * 1024 * 1024L; // 128MB

    // ==================== Write Configuration ====================

    /**
     * Maximum records per batch write.
     */
    @Builder.Default
    private final int maxBatchSize = 50_000;

    /**
     * Recommended batch size for optimal throughput.
     */
    @Builder.Default
    private final int recommendedBatchSize = 10_000;

    /**
     * Enable write commit retry.
     */
    @Builder.Default
    private final boolean commitRetryEnabled = true;

    /**
     * Number of commit retries before failing.
     */
    @Builder.Default
    private final int commitRetryAttempts = 3;

    // ==================== Partitioning Configuration ====================

    /**
     * Partition granularity for detection_date.
     */
    @Builder.Default
    private final PartitionGranularity partitionGranularity = PartitionGranularity.DAY;

    // ==================== Compaction Configuration ====================

    /**
     * Enable automatic small file compaction.
     */
    @Builder.Default
    private final boolean compactionEnabled = true;

    /**
     * Minimum number of files before triggering compaction.
     */
    @Builder.Default
    private final int compactionMinFiles = 5;

    /**
     * Target number of files after compaction.
     */
    @Builder.Default
    private final int compactionTargetFiles = 1;

    // ==================== Snapshot Configuration ====================

    /**
     * Enable snapshot expiration cleanup.
     */
    @Builder.Default
    private final boolean snapshotExpirationEnabled = true;

    /**
     * Retention period for snapshots (for time-travel).
     */
    @Builder.Default
    private final Duration snapshotRetention = Duration.ofDays(7);

    /**
     * Maximum number of snapshots to retain.
     */
    @Builder.Default
    private final int maxSnapshotsToKeep = 100;

    // ==================== Metrics Configuration ====================

    /**
     * Enable metrics collection.
     */
    @Builder.Default
    private final boolean metricsEnabled = true;

    /**
     * Metrics prefix for all Iceberg metrics.
     */
    @Builder.Default
    private final String metricsPrefix = "eventcloud.iceberg";

    // ==================== Enums ====================

    /**
     * Supported Iceberg catalog types.
     */
    public enum CatalogType {
        /**
         * Hadoop catalog - uses filesystem for metadata.
         * Best for development and simple deployments.
         */
        HADOOP,

        /**
         * Hive Metastore catalog.
         * Best for on-premises with existing Hive infrastructure.
         */
        HIVE,

        /**
         * AWS Glue Data Catalog.
         * Best for AWS production deployments.
         */
        GLUE,

        /**
         * Nessie catalog - Git-like versioned catalog.
         * Best for advanced multi-branch analytics.
         */
        NESSIE,

        /**
         * REST catalog - generic HTTP-based catalog.
         * Best for cloud-agnostic deployments.
         */
        REST
    }

    /**
     * Supported data file formats.
     */
    public enum FileFormat {
        /**
         * Apache Parquet - columnar, best for analytics.
         */
        PARQUET,

        /**
         * Apache ORC - columnar with built-in indexes.
         */
        ORC,

        /**
         * Apache Avro - row-based, good for streaming.
         */
        AVRO
    }

    /**
     * Partition granularity for time-based partitioning.
     */
    public enum PartitionGranularity {
        /**
         * Partition by hour - fine granularity, many partitions.
         */
        HOUR,

        /**
         * Partition by day - balanced, recommended.
         */
        DAY,

        /**
         * Partition by month - coarse granularity, fewer partitions.
         */
        MONTH,

        /**
         * Partition by year - very coarse, for multi-year archives.
         */
        YEAR
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a default configuration for development.
     * Uses Hadoop catalog with local filesystem.
     *
     * @return default configuration
     */
    public static IcebergStorageConfig defaults() {
        return IcebergStorageConfig.builder().build();
    }

    /**
     * Creates a configuration for S3 with Hadoop catalog.
     * Good for development with S3-compatible storage (MinIO).
     *
     * @param warehousePath S3 path (s3://bucket/warehouse)
     * @param s3Endpoint    S3 endpoint (for MinIO)
     * @return S3 configuration
     */
    public static IcebergStorageConfig forS3(String warehousePath, String s3Endpoint) {
        return IcebergStorageConfig.builder()
                .catalogType(CatalogType.HADOOP)
                .warehousePath(Objects.requireNonNull(warehousePath))
                .s3Endpoint(s3Endpoint)
                .s3PathStyleAccess(true)
                .build();
    }

    /**
     * Creates a configuration for AWS Glue catalog.
     * Recommended for AWS production deployments.
     *
     * @param warehousePath S3 path (s3://bucket/warehouse)
     * @param awsRegion     AWS region
     * @return Glue configuration
     */
    public static IcebergStorageConfig forGlue(String warehousePath, String awsRegion) {
        return IcebergStorageConfig.builder()
                .catalogType(CatalogType.GLUE)
                .warehousePath(Objects.requireNonNull(warehousePath))
                .awsRegion(Objects.requireNonNull(awsRegion))
                .compressionCodec("zstd")
                .build();
    }

    /**
     * Creates a configuration for Hive Metastore.
     * Good for on-premises with existing Hive infrastructure.
     *
     * @param warehousePath    HDFS/S3 path
     * @param hiveMetastoreUri Hive Metastore thrift URI
     * @return Hive configuration
     */
    public static IcebergStorageConfig forHive(String warehousePath, String hiveMetastoreUri) {
        return IcebergStorageConfig.builder()
                .catalogType(CatalogType.HIVE)
                .warehousePath(Objects.requireNonNull(warehousePath))
                .hiveMetastoreUri(Objects.requireNonNull(hiveMetastoreUri))
                .build();
    }

    /**
     * Creates a configuration for the Iceberg REST Catalog specification.
     * Cloud-agnostic; works with any server implementing the Iceberg REST API.
     *
     * @param restCatalogUri REST catalog base URI (e.g. {@code http://catalog:8181})
     * @param warehousePath  Warehouse path understood by the REST catalog server
     * @return REST catalog configuration
     */
    public static IcebergStorageConfig forRest(String restCatalogUri, String warehousePath) {
        return IcebergStorageConfig.builder()
                .catalogType(CatalogType.REST)
                .restCatalogUri(Objects.requireNonNull(restCatalogUri))
                .warehousePath(Objects.requireNonNull(warehousePath))
                .build();
    }

    /**
     * Creates a configuration for Project Nessie.
     * Nessie v0.50+ exposes a standard Iceberg REST catalog endpoint at
     * {@code {nessieCatalogUri}/iceberg}.
     *
     * @param nessieCatalogUri Nessie server URI (e.g. {@code http://nessie:19120})
     * @param warehousePath    S3/HDFS warehouse path registered in Nessie
     * @param branch           Branch to target (e.g. {@code main})
     * @return Nessie catalog configuration
     */
    public static IcebergStorageConfig forNessie(
            String nessieCatalogUri, String warehousePath, String branch) {
        return IcebergStorageConfig.builder()
                .catalogType(CatalogType.NESSIE)
                .nessieCatalogUri(Objects.requireNonNull(nessieCatalogUri))
                .nessieBranch(branch != null ? branch : "main")
                .warehousePath(Objects.requireNonNull(warehousePath))
                .build();
    }

    // ==================== Validation ====================

    /**
     * Validates the configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (warehousePath == null || warehousePath.isBlank()) {
            throw new IllegalStateException("warehousePath is required");
        }

        if (catalogType == CatalogType.HIVE && (hiveMetastoreUri == null || hiveMetastoreUri.isBlank())) {
            throw new IllegalStateException("hiveMetastoreUri is required for HIVE catalog");
        }

        if (catalogType == CatalogType.GLUE && (awsRegion == null || awsRegion.isBlank())) {
            throw new IllegalStateException("awsRegion is required for GLUE catalog");
        }

        if (catalogType == CatalogType.REST && (restCatalogUri == null || restCatalogUri.isBlank())) {
            throw new IllegalStateException("restCatalogUri is required for REST catalog");
        }

        if (catalogType == CatalogType.NESSIE && (nessieCatalogUri == null || nessieCatalogUri.isBlank())) {
            throw new IllegalStateException("nessieCatalogUri is required for NESSIE catalog");
        }

        if (targetFileSizeBytes <= 0) {
            throw new IllegalStateException("targetFileSizeBytes must be positive");
        }

        if (maxBatchSize <= 0) {
            throw new IllegalStateException("maxBatchSize must be positive");
        }

        if (snapshotRetention.isNegative() || snapshotRetention.isZero()) {
            throw new IllegalStateException("snapshotRetention must be positive");
        }
    }
}
